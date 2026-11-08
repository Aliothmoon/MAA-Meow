package com.aliothmoon.maameow.presentation.view.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.presentation.components.CollapsibleSection
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.theme.MaaDesignTokens

@Composable
fun SettingIcon(icon: ImageVector, enabled: Boolean = true) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Icon(
        icon,
        contentDescription = null,
        tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        modifier = Modifier.size(22.dp),
    )
}

@Composable
fun SettingsSection(
    title: String,
    sectionKey: String,
    revealToken: Any?,
    forceExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    CollapsibleSection(
        title = title,
        sectionKey = sectionKey,
        forceExpanded = forceExpanded,
        revealToken = revealToken,
    ) {
        SettingsGroupCard {
            SettingsDividedColumn(content = content)
        }
    }
}

/** 相邻非空子项间画分割线 */
@Composable
fun SettingsDividedColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    // 布局阶段写、绘制阶段读，绘制总在布局之后
    val lines = remember { ArrayList<Int>() }
    Layout(
        content = content,
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val inset = MaaDesignTokens.Separator.inset.toPx()
                val thickness = MaaDesignTokens.Separator.thickness.toPx()
                lines.forEach { y ->
                    drawRect(
                        color = color,
                        topLeft = Offset(inset, y.toFloat()),
                        size = Size(size.width - inset, thickness),
                    )
                }
            },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val height = placeables.sumOf { it.height }
        layout(constraints.maxWidth, height) {
            lines.clear()
            var y = 0
            placeables.forEach { p ->
                if (y > 0 && p.height > 0) lines += y
                p.place(0, y)
                y += p.height
            }
        }
    }
}

@Composable
fun SettingsSegment(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), content = content)
}

enum class RowTrailing { NONE, CHEVRON, EXTERNAL }

@Composable
fun RowTrailingIcon(kind: RowTrailing) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    when (kind) {
        RowTrailing.NONE -> Unit
        RowTrailing.CHEVRON -> Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = tint,
        )

        RowTrailing.EXTERNAL -> Icon(
            Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

private val IconSlot = 22.dp

/** 与标题同行放不下时选项换行 */
@Composable
fun <T> SettingChoiceItem(
    title: String,
    icon: ImageVector,
    entries: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    description: String? = null,
    info: String? = null,
    enabled: Boolean = true,
) {
    val options: @Composable (stacked: Boolean) -> Unit = { stacked ->
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(
                    if (stacked) MaaDesignTokens.Spacing.xxl else MaaDesignTokens.Spacing.md
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entries.forEach { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .selectable(
                                selected = entry == selected,
                                onClick = { onSelected(entry) },
                                enabled = enabled,
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = entry == selected, onClick = null, enabled = enabled)
                        // 换行时圆点占 24dp，文字起点与标题对齐
                        Spacer(
                            Modifier.width(
                                if (stacked) IconSlot + MaaDesignTokens.Spacing.lg - 24.dp else 6.dp
                            )
                        )
                        Text(
                            text = label(entry),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    val titleStyle = MaterialTheme.typography.bodyLarge
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val optionsWidth = subcompose("probeOptions") { options(false) }
            .maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val titleWidth = subcompose("probeTitle") { Text(title, style = titleStyle) }
            .maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val chrome = (IconSlot + MaaDesignTokens.Spacing.lg * 2 + if (info != null) 28.dp else 0.dp).roundToPx()
        val inline = chrome + titleWidth + optionsWidth <= constraints.maxWidth
        val placeables = subcompose(if (inline) "inline" else "stacked") {
            if (inline) {
                SettingRow(
                    title = title,
                    description = description,
                    info = info,
                    enabled = enabled,
                    leading = { SettingIcon(icon, enabled) },
                    trailing = { options(false) },
                )
            } else {
                SettingRow(
                    title = title,
                    description = description,
                    info = info,
                    enabled = enabled,
                    leading = { SettingIcon(icon, enabled) },
                    footer = {
                        Box(Modifier.padding(bottom = MaaDesignTokens.Spacing.listItemVertical)) {
                            options(true)
                        }
                    },
                )
            }
        }.map { it.measure(constraints.copy(minHeight = 0)) }
        layout(constraints.maxWidth, placeables.maxOf { it.height }) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

@Composable
fun ThemeModePicker(
    selected: AppSettingsManager.ThemeMode,
    onSelected: (AppSettingsManager.ThemeMode) -> Unit,
    icon: ImageVector,
) {
    val modes = listOf(
        AppSettingsManager.ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        AppSettingsManager.ThemeMode.WHITE to stringResource(R.string.settings_theme_white),
        AppSettingsManager.ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
        AppSettingsManager.ThemeMode.PURE_DARK to stringResource(R.string.settings_theme_pure_dark),
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingRow(
            title = stringResource(R.string.settings_theme_title),
            leading = { SettingIcon(icon) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MaaDesignTokens.Spacing.listItemVertical),
            horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
        ) {
            modes.forEach { (mode, label) ->
                ThemeTile(
                    mode = mode,
                    label = label,
                    selected = mode == selected,
                    onClick = { onSelected(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val LightPreview = Color(0xFFF4F4F7) to Color(0xFFFFFFFF)
private val DarkPreview = Color(0xFF1E1E22) to Color(0xFF2E2E34)
private val PureDarkPreview = Color(0xFF000000) to Color(0xFF1A1A1A)

@Composable
private fun ThemeTile(
    mode: AppSettingsManager.ThemeMode,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(MaaDesignTokens.CornerRadius.card)
    Column(
        modifier = modifier
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(MaaDesignTokens.Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(shape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                ),
        ) {
            when (mode) {
                AppSettingsManager.ThemeMode.SYSTEM -> Row(Modifier.fillMaxSize()) {
                    MiniScreen(LightPreview, primary, Modifier.weight(1f), clipEnd = true)
                    MiniScreen(DarkPreview, primary, Modifier.weight(1f), clipStart = true)
                }

                AppSettingsManager.ThemeMode.WHITE -> MiniScreen(LightPreview, primary)
                AppSettingsManager.ThemeMode.DARK -> MiniScreen(DarkPreview, primary)
                AppSettingsManager.ThemeMode.PURE_DARK -> MiniScreen(PureDarkPreview, primary)
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MiniScreen(
    colors: Pair<Color, Color>,
    accent: Color,
    modifier: Modifier = Modifier,
    clipStart: Boolean = false,
    clipEnd: Boolean = false,
) {
    val (bg, card) = colors
    val bar = if (bg.luminance() > 0.5f) Color(0x22000000) else Color(0x33FFFFFF)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .padding(
                start = if (clipStart) 0.dp else 6.dp,
                end = if (clipEnd) 0.dp else 6.dp,
                top = 8.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.55f)
                .height(5.dp)
                .clip(CircleShape)
                .background(accent)
        )
        repeat(2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(card)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(bar)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(bar)
                )
            }
        }
    }
}

@Composable
fun SettingAboutHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(R.color.ic_launcher_background)),
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.5f),
            )
        }
        Spacer(Modifier.width(MaaDesignTokens.Spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            VersionPill(BuildConfig.VERSION_NAME, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun VersionPill(text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.12f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
