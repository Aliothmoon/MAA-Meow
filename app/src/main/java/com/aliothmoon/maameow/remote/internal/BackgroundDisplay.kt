package com.aliothmoon.maameow.remote.internal

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.third.Ln

/** 后台虚拟屏在 Legacy 与 VDM（14+）间分流，同一时刻最多一个在跑 */
object BackgroundDisplay {
    private const val TAG = "BackgroundDisplay"

    /** 换后端前须先 [stop] */
    @Volatile
    var useVdm = false

    // 选了 VDM 就要独立显示组，失败不回退 Legacy
    fun start(): Int = if (vdm()) {
        runCatching { VdmDisplayController.start() }
            .onFailure { Ln.e("$TAG: VDM start failed: ${it.message}") }
            .getOrDefault(DefaultDisplayConfig.DISPLAY_NONE)
    } else {
        VirtualDisplayManager.start()
    }

    fun stop() {
        VirtualDisplayManager.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            VdmDisplayController.stop()
        }
    }

    fun setResolution(width: Int, height: Int, dpi: Int) {
        VirtualDisplayManager.setResolution(width, height, dpi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            VdmDisplayController.setResolution(width, height, dpi)
        }
    }

    fun displayId(): Int =
        if (vdm()) VdmDisplayController.getDisplayId() else VirtualDisplayManager.getDisplayId()

    fun flagTier(): Int =
        if (vdm()) VdmDisplayController.getFlagTier() else VirtualDisplayManager.getFlagTier()

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun vdm(): Boolean =
        useVdm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}
