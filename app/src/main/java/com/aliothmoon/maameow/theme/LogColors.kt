package com.aliothmoon.maameow.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.aliothmoon.maameow.data.model.LogColorRole
import com.aliothmoon.maameow.data.model.LogLevel


// 蓝色统一取主题主色（浅 / 深），同屏不出现两种蓝
private val InfoBlue = Color(0xFF2B6BCA) to Color(0xFF6D9DE3)

private fun colorsFor(role: LogColorRole): Pair<Color, Color> = when (role) {
    LogColorRole.DEFAULT -> Color.Unspecified to Color.Unspecified
    LogColorRole.INFO -> InfoBlue
    LogColorRole.SUCCESS -> Color(0xFF67C23A) to Color(0xFF7ED957)
    LogColorRole.WARNING -> Color(0xFFE6A23C) to Color(0xFFF0B050)
    LogColorRole.ERROR -> Color(0xFFF56C6C) to Color(0xFFFF8585)
    LogColorRole.TRACE -> Color(0xFF909399) to Color(0xFFB0B4BC)
    LogColorRole.RARE -> Color(0xFFFFAA00) to Color(0xFFFFC042)
    LogColorRole.STAR_1 -> Color(0xFF333333) to Color(0xFFE5E5E5)
    LogColorRole.STAR_2 -> Color(0xFF99CC33) to Color(0xFFB5E853)
    LogColorRole.STAR_3 -> InfoBlue
    LogColorRole.STAR_4 -> Color(0xFF9966FF) to Color(0xFFB58CFF)
    LogColorRole.STAR_5 -> Color(0xFFFFAA33) to Color(0xFFFFC56B)
    LogColorRole.STAR_6 -> Color(0xFFFF8C00) to Color(0xFFFFA94D)
    LogColorRole.ROBOT -> Color(0xFF666666) to Color(0xFFB0B0B0)
    LogColorRole.ROGUELIKE_SUCCESS -> Color(0xFF52C41A) to Color(0xFF73D13D)
    LogColorRole.ROGUELIKE_COMBAT -> InfoBlue
    LogColorRole.ROGUELIKE_EMERGENCY -> Color(0xFFFA8C16) to Color(0xFFFFB070)
    LogColorRole.ROGUELIKE_BOSS -> Color(0xFFEB2F96) to Color(0xFFFF6FBA)
    LogColorRole.ROGUELIKE_ABANDON -> Color(0xFF8C8C8C) to Color(0xFFBFBFBF)
}

@Immutable
class LogPalette internal constructor(private val colors: Array<Color>) {
    operator fun get(role: LogColorRole): Color = colors[role.ordinal]
}

private val LightLogPalette = LogPalette(
    Array(LogColorRole.entries.size) { i -> colorsFor(LogColorRole.entries[i]).first })
private val DarkLogPalette = LogPalette(
    Array(LogColorRole.entries.size) { i -> colorsFor(LogColorRole.entries[i]).second })

/** static 是因为色板只在主题切换时变更——远低于状态读取频率。 */
val LocalLogPalette = staticCompositionLocalOf { LightLogPalette }

/** 由 [com.aliothmoon.maameow.theme.MaaMeowTheme] 在已知 `isDark` 的入口调用。 */
@Composable
fun ProvideLogPalette(isDark: Boolean, content: @Composable () -> Unit) {
    val palette = if (isDark) DarkLogPalette else LightLogPalette
    CompositionLocalProvider(LocalLogPalette provides palette, content = content)
}

@Composable
fun LogColorRole.themedColor(): Color {
    val color = LocalLogPalette.current[this]
    return if (color == Color.Unspecified) LocalContentColor.current else color
}

@Composable
fun LogLevel.themedColor(): Color = colorRole.themedColor()
