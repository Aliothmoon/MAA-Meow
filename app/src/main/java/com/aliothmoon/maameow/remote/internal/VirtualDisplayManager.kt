package com.aliothmoon.maameow.remote.internal

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.view.Surface
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.constant.AndroidVersions
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.DefaultDisplayConfig.VD_NAME
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


object VirtualDisplayManager {

    private const val STATE_IDLE = 0
    private const val STATE_CAPTURING = 1

    private const val VIRTUAL_DISPLAY_FLAG_PUBLIC: Int = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
    private const val VIRTUAL_DISPLAY_FLAG_PRESENTATION: Int =
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
    private const val VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY: Int =
        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
    private const val VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH: Int = 1 shl 6
    private const val VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT: Int = 1 shl 7
    private const val VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL: Int = 1 shl 8
    private const val VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS: Int = 1 shl 9
    private const val VIRTUAL_DISPLAY_FLAG_TRUSTED: Int = 1 shl 10
    private const val VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP: Int = 1 shl 11
    private const val VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED: Int = 1 shl 12
    private const val VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED: Int = 1 shl 13
    private const val VIRTUAL_DISPLAY_FLAG_OWN_FOCUS: Int = 1 shl 14
    private const val VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP: Int = 1 shl 15
    private const val VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED: Int = 1 shl 16

    private const val VD_SYSTEM_DECORATIONS = false
    private const val VD_DESTROY_CONTENT = true

    const val DISPLAY_NONE = -1
    const val FLAG_TIER_NONE = -1

    data class DisplayConfig(
        val width: Int = DefaultDisplayConfig.WIDTH,
        val height: Int = DefaultDisplayConfig.HEIGHT,
        val dpi: Int = DefaultDisplayConfig.DPI
    )

    private val state = AtomicInteger(STATE_IDLE)
    private val config = AtomicReference(DisplayConfig())
    private val displayId = AtomicInteger(DISPLAY_NONE)
    private val flagTier = AtomicInteger(FLAG_TIER_NONE)
    private val virtualDisplay = AtomicReference<VirtualDisplay?>()

    fun start(): Int {
        if (!state.compareAndSet(STATE_IDLE, STATE_CAPTURING)) {
            Ln.w("start: already capturing")
            return displayId.get()
        }
        return startInternal()
    }

    fun stop() {
        if (!state.compareAndSet(STATE_CAPTURING, STATE_IDLE)) {
            return
        }
        releaseResources()
        Ln.i("VirtualDisplayManager stopped")
    }

    fun restart() {
        if (state.get() != STATE_CAPTURING) {
            return
        }
        releaseResources()
        startInternal()
    }

    fun setResolution(width: Int, height: Int, dpi: Int = config.get().dpi) {
        val newConfig = DisplayConfig(width, height, dpi)
        val oldConfig = config.getAndSet(newConfig)
        if (state.get() == STATE_CAPTURING && oldConfig != newConfig) {
            Ln.i("Resolution changed: ${oldConfig.width}x${oldConfig.height} -> ${width}x${height}, restart")
            restart()
        }
    }

    fun getDisplayId(): Int = displayId.get()

    fun getFlagTier(): Int = flagTier.get()

    private fun startInternal(): Int {
        try {
            val cfg = config.get()
            val surface = NativeBridgeLib.setupNativeCapturer(cfg.width, cfg.height)
            createVirtualDisplay(surface, cfg)

            Ln.i("VirtualDisplayManager started, displayId=${displayId.get()}")
            return displayId.get()
        } catch (e: Exception) {
            Ln.e("VirtualDisplayManager start failed", e)
            state.set(STATE_IDLE)
            return DISPLAY_NONE
        }
    }

    private fun releaseResources() {
        virtualDisplay.getAndSet(null)?.release()
        NativeBridgeLib.releaseNativeCapturer()
        displayId.set(DISPLAY_NONE)
        flagTier.set(FLAG_TIER_NONE)
    }

    private fun createVirtualDisplay(surface: Surface, cfg: DisplayConfig) {
        val wm = ServiceManager.getWindowManager()
        val physicalRotation = runCatching { wm.rotation }.getOrDefault(-1)
        Ln.i("Physical display rotation: $physicalRotation")

        val base = baseFlags()
        val tiers = flagTiers(Build.VERSION.SDK_INT).map { tier -> tier.fold(base, Int::or) }
        val (tier, vd) = createWithFallback(tiers) { flags ->
            ServiceManager.getDisplayManager()
                .createNewVirtualDisplay(VD_NAME, cfg.width, cfg.height, cfg.dpi, surface, flags)
        }
        val flags = tiers[tier]
        flagTier.set(tier)
        virtualDisplay.set(vd)
        val vdId = vd.display.displayId
        displayId.set(vdId)

        val d = vd.display
        Ln.i(
            "VD created: id=$vdId" +
                    ", configured=${cfg.width}x${cfg.height}" +
                    ", actual=${d.mode.physicalWidth}x${d.mode.physicalHeight}" +
                    ", rotation=${d.rotation}" +
                    ", flags=0x${flags.toString(16)}, tier=$tier"
        )
        logEffective(vdId, flags, d.flags)

        if (d.rotation != Surface.ROTATION_0) {
            // 所有旋转非零的情况都先尝试 freezeRotation
            runCatching {
                wm.freezeRotation(vdId, Surface.ROTATION_0)
                Ln.i("freezeRotation done, post-freeze rotation=${vd.display.rotation}")
            }.onFailure { e -> Ln.w("freezeRotation failed: ${e.message}") }

            if (physicalRotation == Surface.ROTATION_0) {
                // 物理屏处于自然方向（rotation=0）而 VD 却有旋转角，
                // 这是横屏原生设备如AYN Odin2的典型特征：
                // 此类设备的定制 ROM 对二级显示调 freezeRotation 无效，
                // 额外调 setForcedDisplaySize 强制 VD 向内部 app 上报横屏尺寸。
                Ln.w(
                    "Landscape-native device detected (physRot=0, vdRot=${d.rotation}), " +
                            "applying setForcedDisplaySize"
                )
                runCatching {
                    wm.setForcedDisplaySize(vdId, cfg.width, cfg.height)
                    Ln.i("setForcedDisplaySize(${cfg.width}x${cfg.height}) applied")
                }.onFailure { e -> Ln.w("setForcedDisplaySize failed: ${e.message}") }
            }
        }
    }

    /** 部分 ROM 会悄悄不给申请的 flag */
    private fun logEffective(vdId: Int, requested: Int, displayFlags: Int) {
        val groupId = runCatching { ServiceManager.getDisplayManager().getDisplayGroupId(vdId) }.getOrDefault(-1)
        Ln.i("VD effective: group=$groupId, flags=${describeDisplayFlags(displayFlags)}")
        if (requested and VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP != 0 && groupId == 0) {
            Ln.w("VD requested OWN_DISPLAY_GROUP but landed in group 0, it will follow the main screen power state")
        }
    }

    /** Display 的隐藏位，13～17 未变 */
    internal fun describeDisplayFlags(flags: Int): String = listOf(
        1 shl 7 to "TRUSTED",
        1 shl 8 to "OWN_DISPLAY_GROUP",
        1 shl 9 to "ALWAYS_UNLOCKED",
        1 shl 10 to "TOUCH_FEEDBACK_DISABLED",
        1 shl 11 to "OWN_FOCUS",
        1 shl 12 to "STEAL_TOP_FOCUS_DISABLED",
    ).filter { (bit, _) -> flags and bit != 0 }
        .joinToString(prefix = "[", postfix = "]") { it.second }

    /** 不加 PRESENTATION：会被抖音等应用当成投屏目标 */
    private fun baseFlags(): Int {
        var flags = (VIRTUAL_DISPLAY_FLAG_PUBLIC
                or VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                or VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH)

        if (VD_DESTROY_CONTENT) {
            flags = flags or VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL
        }
        if (VD_SYSTEM_DECORATIONS) {
            flags = flags or VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS
        }
        return flags
    }

    /** 末档给 Shell 缺 ADD_TRUSTED_DISPLAY 的 15 QPR2：留在 group 0，熄屏锁屏随主屏停，注入会抢焦点 */
    internal fun flagTiers(sdkInt: Int): Array<IntArray> = when {
        sdkInt >= AndroidVersions.API_34_ANDROID_14 -> arrayOf(
            intArrayOf(
                VIRTUAL_DISPLAY_FLAG_TRUSTED,
                VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP,
                VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED,
                VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED,
                VIRTUAL_DISPLAY_FLAG_OWN_FOCUS,
                VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP,
                VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED,
            ),
            intArrayOf(VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED),
        )

        sdkInt >= AndroidVersions.API_33_ANDROID_13 -> arrayOf(
            intArrayOf(
                VIRTUAL_DISPLAY_FLAG_TRUSTED,
                VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP,
                VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED,
                VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED,
            ),
            intArrayOf(VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED),
        )

        else -> arrayOf(intArrayOf())
    }

    internal fun <T> createWithFallback(tiers: List<Int>, create: (flags: Int) -> T): Pair<Int, T> {
        for ((index, flags) in tiers.withIndex()) {
            try {
                return index to create(flags)
            } catch (e: SecurityException) {
                if (index == tiers.lastIndex) throw e
                Ln.w("createVirtualDisplay rejected at tier $index (flags=0x${flags.toString(16)}): ${e.message}")
            }
        }
        throw IllegalArgumentException("no flag tier")
    }
}
