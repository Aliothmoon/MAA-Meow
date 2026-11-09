package com.aliothmoon.maameow.utils.device

import android.os.Build

/**
 * Windows Subsystem for Android 识别。
 *
 * WSA 把每个应用当作独立 Win32 窗口托管，首建时就把 task 的 maxBounds 钉死成当时的
 * 窗口内容区，之后不再重算，导致画面只占屏的一角。只有这种宿主才需要「铺满虚拟屏」，
 * 普通手机开了只是徒增一次 resize。
 */
object WsaDetector {

    /**
     * WSA 的 ro.product.model 是 "Subsystem for Android(TM)"，
     * ro.boot.hardware / ro.hardware 是 windows_x86_64，ro.product.board 是 windows。
     * 三项任一命中即认定，避免个别 nightly 构建改名导致漏判。
     */
    private val cached: Boolean by lazy { detect() }

    fun isWsa(): Boolean = cached

    private fun detect(): Boolean {
        val model = Build.MODEL.orEmpty()
        if (model.contains("Subsystem for Android", ignoreCase = true)) return true

        val hardware = SystemProps.get("ro.boot.hardware")
            .ifEmpty { Build.HARDWARE.orEmpty() }
        if (hardware.startsWith("windows", ignoreCase = true)) return true

        val board = SystemProps.get("ro.product.board")
            .ifEmpty { Build.BOARD.orEmpty() }
        return board.equals("windows", ignoreCase = true)
    }
}
