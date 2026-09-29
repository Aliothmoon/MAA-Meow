package com.aliothmoon.maameow.presentation.view.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.notification.live.LiveUpdateStyle
import com.aliothmoon.maameow.data.notification.live.TrackerIconStore
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.AppSettingsManager.LiveUpdateColorScheme
import com.aliothmoon.maameow.data.preferences.AppSettingsManager.LiveUpdateTrackerIcon
import com.aliothmoon.maameow.domain.notification.LiveBackend
import com.aliothmoon.maameow.domain.notification.LiveUpdatePublisher
import com.aliothmoon.maameow.presentation.LocalToaster
import com.aliothmoon.maameow.presentation.components.SectionHeader
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

// 自定义色板：只放预设单色之外的补充色
private class ColorSwatch(val argb: Long, @param:StringRes val labelRes: Int) {
    val hex = "#%06X".format(argb and 0xFFFFFF)
}

private val ColorSwatches = listOf(
    ColorSwatch(0xFFF44336, R.string.live_update_color_swatch_red),
    ColorSwatch(0xFF8BC34A, R.string.live_update_color_swatch_light_green),
    ColorSwatch(0xFF00BCD4, R.string.live_update_color_swatch_cyan),
    ColorSwatch(0xFFFFC107, R.string.live_update_color_swatch_amber),
    ColorSwatch(0xFF795548, R.string.live_update_color_swatch_brown),
    ColorSwatch(0xFF607D8B, R.string.live_update_color_swatch_blue_grey),
)

private val SemanticColors = listOf(
    LiveUpdateStyle.COLOR_COMPLETED,
    LiveUpdateStyle.COLOR_ACTIVE,
    LiveUpdateStyle.COLOR_ERROR,
)

@Composable
fun LiveUpdateSettingsView(navController: NavController) {
    val appSettingsManager: AppSettingsManager = koinInject()
    val livePublisher: LiveUpdatePublisher = koinInject()
    val trackerIconStore: TrackerIconStore = koinInject()
    val chipContent by appSettingsManager.liveUpdateChipContent.collectAsStateWithLifecycle()
    val colorScheme by appSettingsManager.liveUpdateColorScheme.collectAsStateWithLifecycle()
    val customColor by appSettingsManager.liveUpdateCustomColor.collectAsStateWithLifecycle()
    val trackerIcon by appSettingsManager.liveUpdateTrackerIcon.collectAsStateWithLifecycle()
    val customTrackerPath by appSettingsManager.liveUpdateCustomTrackerPath.collectAsStateWithLifecycle()
    val currentIcon by trackerIconStore.icon.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val pickFailedMessage = stringResource(R.string.live_update_icon_pick_failed)

    // 文案跟随实际生效的后端；capability 含跨进程查询，放 IO
    val onIsland by produceState(initialValue = false) {
        value = withContext(Dispatchers.IO) {
            runCatching { livePublisher.capability.backend == LiveBackend.HYPER_OS_FOCUS }
                .getOrDefault(false)
        }
    }
    val chipLabelRes =
        if (onIsland) R.string.live_update_chip_label_island else R.string.live_update_chip_label
    val iconLabelRes =
        if (onIsland) R.string.live_update_icon_label_island else R.string.live_update_icon_label
    val hintRes = if (onIsland) R.string.live_update_hint_island else R.string.live_update_hint

    val customTrackerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            if (!trackerIconStore.importCustom(uri)) {
                toaster.show(pickFailedMessage, type = ToastType.Error)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.live_update_settings_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() })
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            contentPadding = PaddingValues(
                horizontal = MaaDesignTokens.Spacing.listHorizontal,
                vertical = MaaDesignTokens.Spacing.sm
            )
        ) {
            // ── 显示内容 ──
            item {
                SectionHeader(stringResource(chipLabelRes))
                SettingsGroupCard {
                    SelectableChipGroup(
                        label = "",
                        selectedValue = chipContent,
                        options = AppSettingsManager.LiveUpdateChipContent.entries.map {
                            it to stringResource(it.labelRes)
                        },
                        onSelected = { value ->
                            if (value != chipContent) {
                                coroutineScope.launch {
                                    appSettingsManager.setLiveUpdateChipContent(value)
                                }
                            }
                        },
                        modifier = Modifier
                            .selectableGroup()
                            .padding(
                                start = MaaDesignTokens.Spacing.lg,
                                top = MaaDesignTokens.Spacing.lg,
                                end = MaaDesignTokens.Spacing.lg,
                                bottom = MaaDesignTokens.Spacing.md,
                            )
                    )
                }
            }

            // ── 进度条颜色 ──
            item {
                SectionHeader(stringResource(R.string.live_update_color_label))
                SettingsGroupCard {
                    val expanded = colorScheme == LiveUpdateColorScheme.CUSTOM
                    OptionFlowRow(expanded = expanded) {
                        LiveUpdateColorScheme.entries.forEach { scheme ->
                            OptionChip(
                                label = stringResource(scheme.labelRes),
                                selected = scheme == colorScheme,
                                onClick = {
                                    if (scheme != colorScheme) {
                                        coroutineScope.launch {
                                            appSettingsManager.setLiveUpdateColorScheme(scheme)
                                        }
                                    }
                                },
                            ) {
                                SchemePreview(scheme)
                            }
                        }
                    }

                    if (expanded) {
                        Text(
                            text = stringResource(R.string.live_update_color_custom_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = MaaDesignTokens.Spacing.lg,
                                end = MaaDesignTokens.Spacing.lg,
                                bottom = MaaDesignTokens.Spacing.xs,
                            )
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup()
                                .padding(
                                    start = MaaDesignTokens.Spacing.lg,
                                    end = MaaDesignTokens.Spacing.lg,
                                    bottom = MaaDesignTokens.Spacing.md,
                                )
                        ) {
                            ColorSwatches.forEach { swatch ->
                                val isSelected = customColor.equals(swatch.hex, ignoreCase = true)
                                val label = stringResource(swatch.labelRes)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(swatch.argb))
                                        .then(
                                            if (isSelected) Modifier.border(
                                                2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape
                                            ) else Modifier
                                        )
                                        .clickable {
                                            coroutineScope.launch {
                                                appSettingsManager.setLiveUpdateCustomColor(swatch.hex)
                                            }
                                        }
                                        .semantics {
                                            contentDescription = label
                                            selected = isSelected
                                            role = Role.RadioButton
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // ── 图标 ──
            item {
                SectionHeader(stringResource(iconLabelRes))
                SettingsGroupCard {
                    val expanded = trackerIcon == LiveUpdateTrackerIcon.CUSTOM
                    OptionFlowRow(expanded = expanded) {
                        LiveUpdateTrackerIcon.entries.forEach { icon ->
                            OptionChip(
                                label = stringResource(icon.labelRes),
                                selected = icon == trackerIcon,
                                onClick = {
                                    if (icon != trackerIcon) {
                                        coroutineScope.launch {
                                            appSettingsManager.setLiveUpdateTrackerIcon(icon)
                                        }
                                    }
                                },
                            ) {
                                IconPreview(icon)
                            }
                        }
                    }

                    if (expanded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = MaaDesignTokens.Spacing.lg,
                                    end = MaaDesignTokens.Spacing.lg,
                                    bottom = MaaDesignTokens.Spacing.md,
                                )
                        ) {
                            currentIcon?.let { bitmap ->
                                val image = remember(bitmap) { bitmap.asImageBitmap() }
                                Image(
                                    bitmap = image,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        // 中灰底，白色/透明图标才看得见
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.size(MaaDesignTokens.Spacing.md))
                            }
                            Text(
                                text = stringResource(R.string.live_update_icon_custom_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.size(MaaDesignTokens.Spacing.sm))
                            OutlinedButton(
                                onClick = { customTrackerLauncher.launch("image/*") },
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(text = stringResource(R.string.live_update_icon_pick))
                            }
                            if (customTrackerPath.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch { trackerIconStore.clearCustom() }
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.padding(start = MaaDesignTokens.Spacing.xs),
                                ) {
                                    Text(text = stringResource(R.string.live_update_icon_clear))
                                }
                            }
                        }
                    }
                }
            }

            // ── 提示 ──
            item {
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(
                        start = MaaDesignTokens.Spacing.lg,
                        top = MaaDesignTokens.Spacing.xs,
                        end = MaaDesignTokens.Spacing.lg,
                        bottom = MaaDesignTokens.Spacing.lg,
                    )
                )
            }
        }
    }
}

/** 选项行；展开附加区时收紧底部间距 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionFlowRow(expanded: Boolean, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(
                start = MaaDesignTokens.Spacing.lg,
                top = MaaDesignTokens.Spacing.lg,
                end = MaaDesignTokens.Spacing.lg,
                bottom = if (expanded) MaaDesignTokens.Spacing.xs else MaaDesignTokens.Spacing.md,
            ),
        content = content,
    )
}

/** 带前置预览的单选胶囊 */
@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
                role = Role.RadioButton
            },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            leading()
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SchemePreview(scheme: LiveUpdateColorScheme) {
    if (scheme == LiveUpdateColorScheme.DEFAULT) {
        // 默认是状态语义色，三色点示意
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SemanticColors.forEach { ColorDot(Color(it), 8) }
        }
    } else {
        ColorDot(scheme.argb?.let(::Color) ?: Color(0xFF9E9E9E), 12)
    }
}

@Composable
private fun ColorDot(color: Color, sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun IconPreview(icon: LiveUpdateTrackerIcon) {
    val iconRes = icon.iconRes
    if (iconRes != null) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                // 白色图标在浅色底上看不见，预览底用中灰
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
    } else {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
