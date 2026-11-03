package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/** 页签级面板左右留白，叠在外层边距上：主界面 18dp 与预览、页签头、开始栏对齐，悬浮窗 12dp */
val TabPanelHorizontalInset = 0.dp

val TabPanelPadding = PaddingValues(start = TabPanelHorizontalInset, end = TabPanelHorizontalInset, top = 2.dp, bottom = 4.dp)
