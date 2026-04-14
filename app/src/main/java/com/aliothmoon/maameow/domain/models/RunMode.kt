package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.constant.DisplayMode

enum class RunMode(
    val displayMode: Int
) {
    FOREGROUND(DisplayMode.PRIMARY),

    BACKGROUND(DisplayMode.BACKGROUND)
}
