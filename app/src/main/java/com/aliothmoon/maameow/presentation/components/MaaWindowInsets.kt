package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable

/** 顶栏 / 底栏避让 inset：systemBars 含 captionBar，自由窗口（HyperOS 小窗）的手柄区靠它避让 */
object MaaWindowInsets {
    val bottomBar: WindowInsets
        @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Bottom)

    val topBar: WindowInsets
        @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Top)
}
