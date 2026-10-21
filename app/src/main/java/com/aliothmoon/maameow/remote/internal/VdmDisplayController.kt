package com.aliothmoon.maameow.remote.internal

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.hardware.display.VirtualDisplay
import android.hardware.display.VirtualDisplayConfig
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.view.Display
import android.view.Surface
import androidx.annotation.RequiresApi
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.remote.internal.VirtualDisplayManager.DisplayConfig
import com.aliothmoon.maameow.third.FakeContext
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.aliothmoon.maameow.third.wrappers.VirtualDeviceManager.VirtualDevice
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Android 14+ 借 VirtualDevice 建后台虚拟屏，拿独立显示组；进程须一直是 shell */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object VdmDisplayController {
    private const val TAG = "VdmDisplay"
    // uid 已校验为 2000，只会在 user 0
    private const val USER_ID = 0
    private const val STREAMING_PROFILE = "android.app.role.COMPANION_DEVICE_APP_STREAMING"
    private const val ASSOCIATION_TIMEOUT_MS = 17_000L
    private const val DISPLAY_TIMEOUT_MS = 1_000L
    private const val POLL_MS = 100L

    private const val FLAG_SUPPORTS_TOUCH = 1 shl 6
    private const val FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
    private const val FLAG_TRUSTED = 1 shl 10
    private const val FLAG_TOUCH_FEEDBACK_DISABLED = 1 shl 13
    private const val FLAG_OWN_FOCUS = 1 shl 14
    private const val FLAG_STEAL_TOP_FOCUS_DISABLED = 1 shl 16

    // 14 的 VDM 不自带 TOUCH_FEEDBACK_DISABLED
    private const val BASE_FLAGS =
        FLAG_SUPPORTS_TOUCH or FLAG_DESTROY_CONTENT_ON_REMOVAL or FLAG_TOUCH_FEEDBACK_DISABLED
    private const val TRUSTED_FLAGS = FLAG_TRUSTED or FLAG_OWN_FOCUS or FLAG_STEAL_TOP_FOCUS_DISABLED

    internal data class Tier(val alwaysUnlocked: Boolean, val trusted: Boolean) {
        val flags: Int get() = if (trusted) BASE_FLAGS or TRUSTED_FLAGS else BASE_FLAGS
    }

    /** 缺 ADD_ALWAYS_UNLOCKED_DISPLAY 或 ADD_TRUSTED_DISPLAY 时逐档降，锁屏照跑优先于独立焦点 */
    internal val tiers = listOf(
        Tier(alwaysUnlocked = true, trusted = true),
        Tier(alwaysUnlocked = true, trusted = false),
        Tier(alwaysUnlocked = false, trusted = true),
        Tier(alwaysUnlocked = false, trusted = false),
    )

    private class Session(val device: VirtualDevice, val display: VirtualDisplay)

    private val config = AtomicReference(DisplayConfig())
    // 读路径不进锁：start 可能卡在等关联上
    private val displayId = AtomicInteger(DefaultDisplayConfig.DISPLAY_NONE)
    private val flagTier = AtomicInteger(VirtualDisplayManager.FLAG_TIER_NONE)
    private var session: Session? = null
    // 本进程建过或复用过关联，stop 时拆掉
    private var associated = false

    private val address: String by lazy {
        val hash = 31 * BuildConfig.APPLICATION_ID.hashCode() + USER_ID
        String.format(
            Locale.ROOT,
            "02:4D:41:%02X:%02X:%02X",
            hash ushr 16 and 0xff,
            hash ushr 8 and 0xff,
            hash and 0xff,
        )
    }

    fun getDisplayId(): Int = displayId.get()

    fun getFlagTier(): Int = flagTier.get()

    /** 分辨率变了只拆显示、留关联，紧接着的 [start] 按新配置重建 */
    @Synchronized
    fun setResolution(width: Int, height: Int, dpi: Int) {
        val newConfig = DisplayConfig(width, height, dpi)
        if (config.getAndSet(newConfig) == newConfig) return
        val active = session ?: return
        Ln.i("$TAG: resolution changed to ${width}x${height}@$dpi, release displayId=${displayId.get()}")
        close(active)
    }

    @Synchronized
    fun start(): Int {
        session?.let { active ->
            if (active.display.display.isValid) return displayId.get()
            // 关联被移除或设备被系统关掉，显示已经没了
            Ln.w("$TAG: displayId=${displayId.get()} removed by the system, recreate")
            close(active)
        }
        check(Process.myUid() == Process.SHELL_UID && Os.geteuid() == Process.SHELL_UID) {
            "VDM owner must be permanently shell (uid=${Process.myUid()}, euid=${Os.geteuid()})"
        }
        try {
            val association = ensureAssociation()
            associated = true
            open(association.id)
            return displayId.get()
        } catch (failure: Throwable) {
            if (associated) {
                associated = false
                runCatching { removeAssociation() }.onFailure(failure::addSuppressed)
            }
            Ln.e("$TAG start failed", failure)
            throw failure
        }
    }

    /** 按 display → device → association 逆序释放 */
    @Synchronized
    fun stop() {
        session?.let(::close)
        if (!associated) return
        associated = false
        runCatching { removeAssociation() }
            .onFailure { Ln.w("$TAG: remove association failed", it) }
        Ln.i("$TAG stopped")
    }

    private fun open(associationId: Int) {
        val cfg = config.get()
        val physicalRotation = VirtualDisplayManager.physicalRotation()
        val surface = NativeBridgeLib.setupNativeCapturer(cfg.width, cfg.height)
        val (tier, opened) = try {
            VirtualDisplayManager.createWithFallback(tiers) { openTier(associationId, surface, cfg, it) }
        } catch (failure: Throwable) {
            NativeBridgeLib.releaseNativeCapturer()
            throw failure
        }
        val display = opened.display.display
        session = opened
        displayId.set(display.displayId)
        flagTier.set(tier)
        Ln.i(
            "$TAG started: displayId=${display.displayId}, group=${displayGroupId(display)}, " +
                "${cfg.width}x${cfg.height}@${cfg.dpi}, tier=$tier ${tiers[tier]}"
        )
        VirtualDisplayManager.fixRotation(display, cfg, physicalRotation)
    }

    @SuppressLint("WrongConstant")
    private fun openTier(associationId: Int, surface: Surface, cfg: DisplayConfig, tier: Tier): Session {
        val device = ServiceManager.getVirtualDeviceManager().createVirtualDevice(associationId, tier.alwaysUnlocked)
        try {
            val display = device.createVirtualDisplay(
                VirtualDisplayConfig.Builder(DefaultDisplayConfig.VD_NAME, cfg.width, cfg.height, cfg.dpi)
                    .setSurface(surface)
                    .setFlags(tier.flags)
                    .build()
            )
            try {
                awaitDisplayReady(display.display)
            } catch (failure: Throwable) {
                runCatching { releaseDisplay(display) }.onFailure(failure::addSuppressed)
                throw failure
            }
            return Session(device, display)
        } catch (failure: Throwable) {
            runCatching { device.close() }.onFailure(failure::addSuppressed)
            throw failure
        }
    }

    private fun close(active: Session) {
        session = null
        val id = displayId.getAndSet(DefaultDisplayConfig.DISPLAY_NONE)
        flagTier.set(VirtualDisplayManager.FLAG_TIER_NONE)
        runCatching { releaseDisplay(active.display) }
            .onFailure { Ln.w("$TAG: release displayId=$id failed: ${it.message}") }
        runCatching { active.device.close() }
            .onFailure { Ln.w("$TAG: close virtual device failed: ${it.message}") }
        NativeBridgeLib.releaseNativeCapturer()
    }

    private fun releaseDisplay(display: VirtualDisplay) {
        try {
            display.setSurface(null)
        } finally {
            display.release()
        }
    }

    private fun ensureAssociation(): AssociationInfo {
        findAssociation()?.let { existing ->
            if (!requiresProfileMigration(existing.deviceProfile, Build.VERSION.SDK_INT)) {
                Ln.i("$TAG: association reused id=${existing.id} addr=$address")
                return existing
            }
            Ln.i(
                "$TAG: association profile migration: ${existing.deviceProfile} -> " +
                    "$STREAMING_PROFILE, disassociate id=${existing.id}"
            )
            removeAssociation()
        }

        val exitCode = RemoteUtils.shellExec(buildAssociateCommand(address, Build.VERSION.SDK_INT))
        check(exitCode == 0) { "companiondevice associate failed ($exitCode)" }
        try {
            val created = awaitAssociation() ?: error("Companion association was not created")
            Ln.i("$TAG: association created id=${created.id} addr=$address")
            return created
        } catch (failure: Throwable) {
            // 命令已受理，关联可能晚到
            runCatching { removeLateAssociation() }.onFailure(failure::addSuppressed)
            throw failure
        }
    }

    internal fun buildAssociateCommand(address: String, sdkInt: Int): String = buildString {
        append("cmd companiondevice associate $USER_ID ${FakeContext.PACKAGE_NAME} $address")
        if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) append(" $STREAMING_PROFILE false")
    }

    internal fun requiresProfileMigration(profile: String?, sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM && profile != STREAMING_PROFILE

    private fun findAssociation(): AssociationInfo? =
        ServiceManager.getCompanionDeviceManager().getAssociations(FakeContext.PACKAGE_NAME, USER_ID)
            .firstOrNull { address.equals(it.deviceMacAddress?.toString(), ignoreCase = true) }

    private fun awaitAssociation(): AssociationInfo? {
        val deadline = SystemClock.elapsedRealtime() + ASSOCIATION_TIMEOUT_MS
        do {
            findAssociation()?.let { return it }
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return findAssociation()
    }

    private fun awaitAssociationRemoval(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + ASSOCIATION_TIMEOUT_MS
        do {
            if (findAssociation() == null) return true
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return findAssociation() == null
    }

    private fun removeAssociation() {
        if (findAssociation() == null) return
        val exitCode = RemoteUtils.shellExec(
            "cmd companiondevice disassociate $USER_ID ${FakeContext.PACKAGE_NAME} $address"
        )
        check(exitCode == 0) { "companiondevice disassociate failed ($exitCode)" }
        check(awaitAssociationRemoval()) { "Companion association was not removed" }
        Ln.i("$TAG: association removed addr=$address")
    }

    private fun removeLateAssociation() {
        if (awaitAssociation() != null) removeAssociation()
    }

    private fun awaitDisplayReady(display: Display) {
        val deadline = SystemClock.elapsedRealtime() + DISPLAY_TIMEOUT_MS
        do {
            if (displayGroupId(display) > 0 && display.state == Display.STATE_ON) return
            SystemClock.sleep(20L)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("VDM display not ready: id=${display.displayId}, group=${displayGroupId(display)}, state=${display.state}")
    }

    private fun displayGroupId(display: Display): Int =
        ServiceManager.getDisplayManager().getDisplayGroupId(display.displayId)
}
