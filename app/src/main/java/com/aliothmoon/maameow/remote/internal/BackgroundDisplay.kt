package com.aliothmoon.maameow.remote.internal

import android.os.Build
import androidx.annotation.VisibleForTesting
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.VirtualDisplayBackendMode
import com.aliothmoon.maameow.constant.VirtualDisplayUpgradeOutcome
import com.aliothmoon.maameow.remote.VirtualDisplayStatus
import com.aliothmoon.maameow.third.Ln

/** 后台虚拟屏在 Legacy 与 VDM（14+）间分流，同一时刻最多一个在跑 */
object BackgroundDisplay {
    private const val TAG = "BackgroundDisplay"

    private enum class ActiveBackend { NONE, LEGACY, VDM }

    internal interface BackendOps {
        fun startLegacy(): Int
        fun stopLegacy()
        fun setLegacyResolution(width: Int, height: Int, dpi: Int)
        fun legacyDisplayId(): Int
        fun legacyFlagTier(): Int
        fun legacyDisplayGroupId(): Int
        fun legacyHasOwnFocus(): Boolean

        fun isVdmSupported(): Boolean
        fun startVdm(): Int
        fun stopVdm()
        fun setVdmResolution(width: Int, height: Int, dpi: Int)
        fun vdmDisplayId(): Int
        fun vdmFlagTier(): Int
        fun vdmDisplayGroupId(): Int
        fun vdmHasOwnFocus(): Boolean
    }

    private object SystemBackendOps : BackendOps {
        override fun startLegacy(): Int = VirtualDisplayManager.start()
        override fun stopLegacy() = VirtualDisplayManager.stop()
        override fun setLegacyResolution(width: Int, height: Int, dpi: Int) =
            VirtualDisplayManager.setResolution(width, height, dpi)
        override fun legacyDisplayId(): Int = VirtualDisplayManager.getDisplayId()
        override fun legacyFlagTier(): Int = VirtualDisplayManager.getFlagTier()
        override fun legacyDisplayGroupId(): Int = VirtualDisplayManager.getDisplayGroupId()
        override fun legacyHasOwnFocus(): Boolean = VirtualDisplayManager.hasOwnFocus()

        override fun isVdmSupported(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && VdmDisplayController.isSupported()

        override fun startVdm(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) VdmDisplayController.start()
            else DefaultDisplayConfig.DISPLAY_NONE

        override fun stopVdm() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                VdmDisplayController.stop()
            }
        }

        override fun setVdmResolution(width: Int, height: Int, dpi: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                VdmDisplayController.setResolution(width, height, dpi)
            }
        }

        override fun vdmDisplayId(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) VdmDisplayController.getDisplayId()
            else DefaultDisplayConfig.DISPLAY_NONE

        override fun vdmFlagTier(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) VdmDisplayController.getFlagTier()
            else VirtualDisplayManager.FLAG_TIER_NONE

        override fun vdmDisplayGroupId(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) VdmDisplayController.getDisplayGroupId()
            else VirtualDisplayManager.DISPLAY_GROUP_UNKNOWN

        override fun vdmHasOwnFocus(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && VdmDisplayController.hasOwnFocus()
    }

    @VisibleForTesting
    internal var ops: BackendOps = SystemBackendOps

    @VisibleForTesting
    internal var sdkInt: () -> Int = { Build.VERSION.SDK_INT }

    /** 换后端前须先 [stop] */
    @Volatile
    var backendMode = VirtualDisplayBackendMode.AUTO

    @Volatile
    private var activeBackend = ActiveBackend.NONE

    @Volatile
    private var upgradeOutcome = VirtualDisplayUpgradeOutcome.NONE

    // 本进程内 AUTO 升 VDM 失败过即熔断
    @Volatile
    private var autoVdmFailed = false

    fun start(): Int = when (effectiveMode()) {
        VirtualDisplayBackendMode.VDM -> startVdmStrict()
        VirtualDisplayBackendMode.LEGACY -> startLegacy()
        else -> startAuto()
    }

    fun stop() {
        ops.stopLegacy()
        if (canUseVdmApi()) {
            ops.stopVdm()
        }
        activeBackend = ActiveBackend.NONE
        upgradeOutcome = VirtualDisplayUpgradeOutcome.NONE
    }

    fun setResolution(width: Int, height: Int, dpi: Int) {
        ops.setLegacyResolution(width, height, dpi)
        if (canUseVdmApi()) {
            ops.setVdmResolution(width, height, dpi)
        }
    }

    fun displayId(): Int = when (activeBackend) {
        ActiveBackend.VDM -> ops.vdmDisplayId()
        ActiveBackend.LEGACY -> ops.legacyDisplayId()
        ActiveBackend.NONE -> DefaultDisplayConfig.DISPLAY_NONE
    }

    fun status(): VirtualDisplayStatus = when (activeBackend) {
        ActiveBackend.VDM -> VirtualDisplayStatus(
            displayId = ops.vdmDisplayId(),
            usingVdm = true,
            flagTier = ops.vdmFlagTier(),
            displayGroupId = ops.vdmDisplayGroupId(),
            upgradeOutcome = upgradeOutcome,
            hasOwnFocus = ops.vdmHasOwnFocus(),
        )

        ActiveBackend.LEGACY -> VirtualDisplayStatus(
            displayId = ops.legacyDisplayId(),
            usingVdm = false,
            flagTier = ops.legacyFlagTier(),
            displayGroupId = ops.legacyDisplayGroupId(),
            upgradeOutcome = upgradeOutcome,
            hasOwnFocus = ops.legacyHasOwnFocus(),
        )

        ActiveBackend.NONE -> VirtualDisplayStatus(
            displayId = DefaultDisplayConfig.DISPLAY_NONE,
            usingVdm = backendMode == VirtualDisplayBackendMode.VDM && canUseVdmApi(),
            flagTier = VirtualDisplayManager.FLAG_TIER_NONE,
            displayGroupId = VirtualDisplayManager.DISPLAY_GROUP_UNKNOWN,
            upgradeOutcome = upgradeOutcome,
            hasOwnFocus = false,
        )
    }

    /** group=-1 未知时不误触发升级 */
    internal fun shouldUpgradeToVdm(legacyId: Int, legacyTier: Int, legacyGroupId: Int): Boolean =
        legacyId == DefaultDisplayConfig.DISPLAY_NONE || legacyTier > 0 || legacyGroupId == 0

    @VisibleForTesting
    internal fun resetForTest() {
        backendMode = VirtualDisplayBackendMode.AUTO
        activeBackend = ActiveBackend.NONE
        upgradeOutcome = VirtualDisplayUpgradeOutcome.NONE
        autoVdmFailed = false
        ops = SystemBackendOps
        sdkInt = { Build.VERSION.SDK_INT }
    }

    private fun startLegacy(): Int {
        val id = ops.startLegacy()
        activeBackend = if (id != DefaultDisplayConfig.DISPLAY_NONE) ActiveBackend.LEGACY else ActiveBackend.NONE
        upgradeOutcome = VirtualDisplayUpgradeOutcome.NONE
        return id
    }

    // 显式选 VDM 就要独立显示组，失败不回退 Legacy
    private fun startVdmStrict(): Int {
        val id = tryStartVdm { Ln.e("$TAG: VDM start failed: ${it.message}") }
        activeBackend = if (id != DefaultDisplayConfig.DISPLAY_NONE) ActiveBackend.VDM else ActiveBackend.NONE
        upgradeOutcome = VirtualDisplayUpgradeOutcome.NONE
        if (id != DefaultDisplayConfig.DISPLAY_NONE) autoVdmFailed = false
        return id
    }

    private fun startAuto(): Int {
        if (activeBackend == ActiveBackend.VDM) return reuseAutoVdm()
        if (activeBackend == ActiveBackend.LEGACY) {
            // 复用活跃 Legacy 屏，不强拆运行中的游戏
            val existingId = ops.legacyDisplayId()
            if (existingId != DefaultDisplayConfig.DISPLAY_NONE) return existingId
        }
        val legacyId = startLegacy()
        val snapshot = status()
        val tier = snapshot.flagTier
        val group = snapshot.displayGroupId
        if (!shouldUpgradeToVdm(legacyId, tier, group)) return legacyId
        if (!ops.isVdmSupported()) {
            Ln.i("$TAG: AUTO keep Legacy (id=$legacyId, tier=$tier, group=$group), VDM unavailable")
            upgradeOutcome = VirtualDisplayUpgradeOutcome.UNSUPPORTED
            return legacyId
        }
        if (autoVdmFailed) {
            Ln.i("$TAG: AUTO keep Legacy (id=$legacyId, tier=$tier, group=$group), VDM previously failed")
            upgradeOutcome = VirtualDisplayUpgradeOutcome.FALLBACK
            return legacyId
        }
        return upgradeAutoToVdm(legacyId, tier, group)
    }

    private fun reuseAutoVdm(): Int {
        val vdmId = tryStartVdm { Ln.w("$TAG: AUTO VDM reuse failed, falling back to Legacy: ${it.message}") }
        if (vdmId != DefaultDisplayConfig.DISPLAY_NONE) {
            upgradeOutcome = VirtualDisplayUpgradeOutcome.UPGRADED
            return vdmId
        }
        ops.stopVdm()
        autoVdmFailed = true
        return fallbackToLegacy()
    }

    private fun upgradeAutoToVdm(legacyId: Int, tier: Int, group: Int): Int {
        Ln.i("$TAG: AUTO upgrading to VDM (legacy id=$legacyId, tier=$tier, group=$group)")
        if (legacyId != DefaultDisplayConfig.DISPLAY_NONE) {
            ops.stopLegacy()
            activeBackend = ActiveBackend.NONE
        }
        val vdmId = tryStartVdm { Ln.w("$TAG: AUTO VDM upgrade failed, falling back to Legacy: ${it.message}") }
        if (vdmId != DefaultDisplayConfig.DISPLAY_NONE) {
            activeBackend = ActiveBackend.VDM
            upgradeOutcome = VirtualDisplayUpgradeOutcome.UPGRADED
            return vdmId
        }
        ops.stopVdm()
        autoVdmFailed = true
        if (legacyId == DefaultDisplayConfig.DISPLAY_NONE) {
            activeBackend = ActiveBackend.NONE
            upgradeOutcome = VirtualDisplayUpgradeOutcome.FALLBACK
            return DefaultDisplayConfig.DISPLAY_NONE
        }
        return fallbackToLegacy()
    }

    private inline fun tryStartVdm(onFailure: (Throwable) -> Unit): Int =
        runCatching { ops.startVdm() }
            .onFailure(onFailure)
            .getOrDefault(DefaultDisplayConfig.DISPLAY_NONE)

    private fun fallbackToLegacy(): Int =
        startLegacy().also { upgradeOutcome = VirtualDisplayUpgradeOutcome.FALLBACK }

    private fun effectiveMode(): Int =
        if (canUseVdmApi()) backendMode else VirtualDisplayBackendMode.LEGACY

    private fun canUseVdmApi(): Boolean = sdkInt() >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}
