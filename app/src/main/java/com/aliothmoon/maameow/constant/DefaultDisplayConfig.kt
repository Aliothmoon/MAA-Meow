package com.aliothmoon.maameow.constant

/**
 * 默认允许的一些宽高配置
 */
object DefaultDisplayConfig {
    const val VD_NAME = "MAA_VD"
    const val DISPLAY_NONE = -1


    const val WIDTH = 1920
    const val HEIGHT = 1080
    const val DPI = 240
    const val ASPECT_RATIO_WIDTH = 16
    const val ASPECT_RATIO_HEIGHT = 9

    /** 16:9 宽高比 */
    val ASPECT_RATIO: Float get() = WIDTH.toFloat() / HEIGHT

    const val FRAME_INTERVAL_MS = 16L

}
