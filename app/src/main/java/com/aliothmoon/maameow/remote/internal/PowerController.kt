package com.aliothmoon.maameow.remote.internal

import android.os.Build
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import com.aliothmoon.maameow.constant.AndroidVersions
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.DisplayControl
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.aliothmoon.maameow.third.wrappers.SurfaceControl
import com.aliothmoon.maameow.utils.device.Manufacturer
import java.util.concurrent.atomic.AtomicBoolean

object PowerController {
    private const val TAG = "PowerController"
    private const val USER_ACTIVITY_INTERVAL_MS = 4_000L
    private const val AWAKE_CHECK_INTERVAL_TICKS = 3

    // 不落盘：进程被杀后由用户唤醒时系统恢复面板
    private val poweredOff = AtomicBoolean(false)

    private val keepAliveLock = Any()
    private var keepAliveDisplayId = DefaultDisplayConfig.DISPLAY_NONE
    private var keepAliveThread: Thread? = null

    internal interface DisplayPowerOps {
        fun displayGroupId(displayId: Int): Int
        fun isDisplayInteractiveOrNull(displayId: Int): Boolean?
        fun wakeDisplay(displayId: Int): Boolean
        fun userActivity(displayId: Int)
    }

    private object SystemDisplayPowerOps : DisplayPowerOps {
        override fun displayGroupId(displayId: Int): Int =
            ServiceManager.getDisplayManager().getDisplayGroupId(displayId)

        override fun isDisplayInteractiveOrNull(displayId: Int): Boolean? =
            ServiceManager.getPowerManager().isScreenOnOrNull(displayId)

        override fun wakeDisplay(displayId: Int): Boolean =
            ServiceManager.getPowerManager().wakeUp(displayId)

        override fun userActivity(displayId: Int) {
            ServiceManager.getPowerManager().userActivity(displayId)
        }
    }

    @VisibleForTesting
    internal var ops: DisplayPowerOps = SystemDisplayPowerOps

    @VisibleForTesting
    internal var sleepMs: (Long) -> Unit = SystemClock::sleep

    @VisibleForTesting
    internal var sdkInt: () -> Int = { Build.VERSION.SDK_INT }

    // 判不出按醒着处理，宁可点亮
    @VisibleForTesting
    internal var systemAwake: () -> Boolean = {
        runCatching { ServiceManager.getPowerManager().isScreenOnOrNull(0) }.getOrNull() ?: true
    }

    fun hasOwnPowerGroup(displayId: Int): Boolean = ops.displayGroupId(displayId) > 0

    // 同主屏电源组时预览不续命，避免主屏无法自然息屏
    internal fun targetKeepAliveDisplay(
        displayId: Int?,
        taskKeepAlive: Boolean,
        previewKeepAlive: Boolean,
    ): Int? = displayId?.takeIf { taskKeepAlive || (previewKeepAlive && hasOwnPowerGroup(it)) }

    fun setDisplayPower(on: Boolean): Boolean {
        poweredOff.set(!on)
        return setDisplayPowerInternal(on)
    }

    private fun setDisplayPowerInternal(on: Boolean): Boolean {
        var applyToMultiPhysicalDisplays =
            Build.VERSION.SDK_INT >= AndroidVersions.API_29_ANDROID_10

        if (applyToMultiPhysicalDisplays
            && Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14 && Manufacturer.isHonorBrand()
            && SurfaceControl.hasGetBuildInDisplayMethod()
        ) {
            applyToMultiPhysicalDisplays = false
        }

        val mode: Int =
            if (on) SurfaceControl.POWER_MODE_NORMAL else SurfaceControl.POWER_MODE_OFF
        if (applyToMultiPhysicalDisplays) {
            val useDisplayControl =
                Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14 && !SurfaceControl.hasGetPhysicalDisplayIdsMethod()

            val physicalDisplayIds =
                if (useDisplayControl) DisplayControl.getPhysicalDisplayIds() else SurfaceControl.getPhysicalDisplayIds()
            if (physicalDisplayIds == null) {
                Ln.e("Could not get physical display ids")
                return false
            }

            var allOk = true
            for (physicalDisplayId in physicalDisplayIds) {
                val binder = if (useDisplayControl) DisplayControl.getPhysicalDisplayToken(
                    physicalDisplayId
                ) else SurfaceControl.getPhysicalDisplayToken(physicalDisplayId)
                allOk = allOk and SurfaceControl.setDisplayPowerMode(binder, mode)
            }
            return allOk
        }

        val d = SurfaceControl.getBuiltInDisplay()
        if (d == null) {
            Ln.e("Could not get built-in display")
            return false
        }
        return SurfaceControl.setDisplayPowerMode(d, mode)
    }

    fun startUserActivityKeepAlive(displayId: Int) {
        synchronized(keepAliveLock) {
            val prev = keepAliveDisplayId
            keepAliveDisplayId = displayId
            if (keepAliveThread != null) {
                if (prev != displayId && prev != DefaultDisplayConfig.DISPLAY_NONE) {
                    Ln.i("$TAG: userActivity keep-alive switched displayId=$prev -> $displayId")
                }
                return
            }
            Ln.i("$TAG: userActivity keep-alive started, displayId=$displayId")
            keepAliveThread = Thread(::runKeepAlive).apply {
                name = "power-user-activity-keepalive"
                isDaemon = true
                start()
            }
        }
    }

    fun stopUserActivityKeepAlive() {
        synchronized(keepAliveLock) { keepAliveDisplayId = DefaultDisplayConfig.DISPLAY_NONE }
    }

    // 退出与清零 keepAliveThread 同锁，防停后立刻重启撞上线程收尾
    private fun nextKeepAliveDisplay(): Int = synchronized(keepAliveLock) {
        keepAliveDisplayId.also { if (it == DefaultDisplayConfig.DISPLAY_NONE) keepAliveThread = null }
    }

    private fun runKeepAlive() {
        var lastDisplayId = DefaultDisplayConfig.DISPLAY_NONE
        var tick = 0
        var asleepReported = false
        while (true) {
            val id = nextKeepAliveDisplay()
            if (id == DefaultDisplayConfig.DISPLAY_NONE) break
            if (id != lastDisplayId) {
                lastDisplayId = id
                tick = 0
                asleepReported = false
            }
            runCatching { ops.userActivity(id) }
                .onFailure { Ln.e("$TAG: userActivity failed", it) }
            if (tick > 0 && tick % AWAKE_CHECK_INTERVAL_TICKS == 0) {
                asleepReported = !ensureKeepAliveDisplayAwake(id, asleepReported)
            }
            tick++
            sleepMs(USER_ACTIVITY_INTERVAL_MS)
        }
        Ln.i("$TAG: userActivity keep-alive stopped")
    }

    /** 仅巡检独立电源组；单次熄屏周期内失败后不重复唤醒 */
    private fun ensureKeepAliveDisplayAwake(displayId: Int, alreadyReportedAsleep: Boolean): Boolean {
        val sdk = sdkInt()
        if (sdk < AndroidVersions.API_34_ANDROID_14) return true
        val ownGroup = runCatching { hasOwnPowerGroup(displayId) }.getOrDefault(false)
        if (!ownGroup) return true
        val interactive = runCatching { ops.isDisplayInteractiveOrNull(displayId) }.getOrNull()
        if (interactive != false) return true
        if (alreadyReportedAsleep) return false
        if (sdk >= AndroidVersions.API_36_ANDROID_16) {
            val woke = runCatching { tryWakeDisplay(displayId) }.getOrDefault(false)
            if (woke) {
                Ln.w("$TAG: display $displayId fell asleep during keep-alive, woke ok")
                return true
            }
            Ln.w("$TAG: display $displayId fell asleep during keep-alive, wake failed")
            return false
        }
        Ln.w("$TAG: display $displayId asleep during keep-alive (sdk=$sdk cannot per-display wake)")
        return false
    }

    private fun tryWakeDisplay(displayId: Int): Boolean =
        ops.wakeDisplay(displayId) && ops.isDisplayInteractiveOrNull(displayId) == true

    // 仅管独立电源组（同主屏组唤醒会点亮主屏）；14+ 可按屏查交互态，16+ 可按屏唤醒
    fun ensureDisplayAwake(displayId: Int): Boolean =
        ensureDisplayAwake(displayId, sdkInt())

    @VisibleForTesting
    internal fun ensureDisplayAwake(displayId: Int, sdkInt: Int): Boolean {
        if (sdkInt < AndroidVersions.API_34_ANDROID_14 || !hasOwnPowerGroup(displayId)) return true
        if (ops.isDisplayInteractiveOrNull(displayId) != false) return true
        val woke = sdkInt >= AndroidVersions.API_36_ANDROID_16 && tryWakeDisplay(displayId)
        Ln.i("$TAG: display $displayId asleep, wake ${if (woke) "ok" else "failed"}")
        return woke
    }

    fun destroy() {
        stopUserActivityKeepAlive()
        if (poweredOff.get()) {
            // 系统已休眠就别点亮，下次唤醒由系统恢复面板
            if (!systemAwake()) {
                Ln.i("$TAG: System asleep, leave display power to the system")
                poweredOff.set(false)
                return
            }
            Ln.i("$TAG: Emergency recovering screen power...")
            runCatching {
                setDisplayPower(true)
            }.onFailure {
                Ln.e("$TAG: Failed to recover screen power: ${it.message}")
            }
        }
    }
}
