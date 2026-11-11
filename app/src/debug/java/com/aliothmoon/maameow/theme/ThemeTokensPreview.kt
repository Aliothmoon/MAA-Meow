package com.aliothmoon.maameow.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.preferences.AppSettingsManager

// 设备上查看：am start -n com.aliothmoon.maameow/androidx.compose.ui.tooling.PreviewActivity
//   --es composable com.aliothmoon.maameow.theme.ThemeTokensPreviewKt.ThemeTokensDemo

private enum class Mode(val label: String, val theme: AppSettingsManager.ThemeMode) {
    LIGHT("浅色", AppSettingsManager.ThemeMode.WHITE),
    DARK("深色", AppSettingsManager.ThemeMode.DARK),
    PURE("纯黑", AppSettingsManager.ThemeMode.PURE_DARK),
}

// 还原调整前的完整旧配色：旧中性色 / 主色，再叠上 token 回退
private fun ColorScheme.legacy(mode: Mode): ColorScheme {
    if (mode == Mode.LIGHT) {
        val container = Color(0xFFE5F1FF)
        return copy(primaryContainer = container, tertiaryContainer = container.copy(alpha = 0.5f))
            .withBaselineFallback(dark = false)
    }
    val pure = mode == Mode.PURE
    val primary = Color(0xFF2B6BCA)
    val container = Color(0xFF004088)
    val surfaceVariant = if (pure) Color(0xFF121212) else Color(0xFF2C2C2E)
    return copy(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = container,
        tertiary = primary.copy(alpha = 0.8f),
        onTertiary = Color.White,
        tertiaryContainer = container.copy(alpha = 0.5f),
        secondary = Color(0xFF98989D),
        secondaryContainer = Color(0xFF2C2C2E),
        onSecondaryContainer = Color(0xFFE5E5EA),
        background = if (pure) Color.Black else Color(0xFF121212),
        onBackground = Color.White,
        surface = if (pure) Color.Black else Color(0xFF1C1C1E),
        onSurface = Color.White,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFF98989D),
        outline = Color(0xFF3A3A3C),
        outlineVariant = surfaceVariant,
    ).withBaselineFallback(dark = true)
}

// token 回退到 M3 默认值，surfaceTint 回到 primary
private fun ColorScheme.withBaselineFallback(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return copy(
        surfaceTint = primary,
        inversePrimary = base.inversePrimary,
        inverseSurface = base.inverseSurface,
        inverseOnSurface = base.inverseOnSurface,
        surfaceBright = base.surfaceBright,
        surfaceDim = base.surfaceDim,
        surfaceContainerLowest = base.surfaceContainerLowest,
        surfaceContainerLow = base.surfaceContainerLow,
        surfaceContainer = base.surfaceContainer,
        surfaceContainerHigh = base.surfaceContainerHigh,
        surfaceContainerHighest = base.surfaceContainerHighest,
    )
}

@Preview
@Composable
fun ThemeTokensDemo() {
    var mode by remember { mutableStateOf(Mode.LIGHT) }
    var before by remember { mutableStateOf(false) }
    MaaMeowTheme(themeMode = mode.theme, useSystemMonetColor = false) {
        val fixed = MaterialTheme.colorScheme
        val scheme = if (before) fixed.legacy(mode) else fixed
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            Surface(color = scheme.background, modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Mode.entries.forEach { m ->
                            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                        }
                        FilterChip(selected = before, onClick = { before = !before }, label = { Text("调整前") })
                    }
                    Swatches(scheme)
                    Components()
                }
            }
        }
    }
}

@Composable
private fun Swatches(scheme: ColorScheme) {
    val roles = listOf(
        "primary" to scheme.primary,
        "onPrimary" to scheme.onPrimary,
        "primaryContainer" to scheme.primaryContainer,
        "onSurface" to scheme.onSurface,
        "onSurfaceVariant" to scheme.onSurfaceVariant,
        "outline" to scheme.outline,
        "background" to scheme.background,
        "surface" to scheme.surface,
        "surfaceVariant" to scheme.surfaceVariant,
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        "surfaceDim" to scheme.surfaceDim,
        "surfaceBright" to scheme.surfaceBright,
        "inverseSurface" to scheme.inverseSurface,
        "inversePrimary" to scheme.inversePrimary,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        roles.forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 48.dp, height = 24.dp)
                        .background(color, RoundedCornerShape(4.dp))
                        .border(1.dp, scheme.outline, RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    "#%06X".format(color.toArgb() and 0xFFFFFF),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Components() {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {}) { Text("主要按钮") }
        TextButton(onClick = {}) { Text("文字按钮") }
        FilterChip(selected = true, onClick = {}, label = { Text("已选") })
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = scheme.primaryContainer) {
        Text(
            "primaryContainer 上的文字",
            color = scheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = false, onCheckedChange = {})
        Switch(checked = true, onCheckedChange = {})
        Text("Switch 关闭态轨道 = Highest", style = MaterialTheme.typography.bodySmall)
    }
    Card(Modifier.fillMaxWidth()) { Sample("Card 默认底色（Highest）") }
    ElevatedCard(Modifier.fillMaxWidth()) { Sample("ElevatedCard（Low）：定时列表") }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = scheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) { Sample("提示弹窗底色（surface + tonalElevation 6dp）") }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = scheme.surfaceContainerHigh) {
        Sample("AlertDialog / DatePicker（High）")
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = scheme.surfaceContainerLow) {
        Sample("底部弹窗 ModalBottomSheet（Low）：日志导出")
    }
    Surface(Modifier.fillMaxWidth(), color = scheme.surfaceContainer) { Sample("TopAppBar 滚动后（Container）") }
    Snackbar { Text("Snackbar（inverseSurface）") }
    DatePicker(state = rememberDatePickerState(), modifier = Modifier.fillMaxWidth(), showModeToggle = false)
}

@Composable
private fun Sample(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
}
