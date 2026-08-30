package com.aliothmoon.maameow.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

object LogTypography {
    val Timestamp = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 12.sp
    )
    val LevelLabel = TextStyle(
        fontSize = 10.sp,
        lineHeight = 12.sp
    )
    val BodyMonospace = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    val BodyMonospaceSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
}

object ScreenSaverDimens {
    val ClockFontSize = 80.sp
}
