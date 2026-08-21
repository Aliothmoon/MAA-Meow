package com.aliothmoon.maameow.presentation.view.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.notification.TrackerIconDecoder
import com.aliothmoon.maameow.presentation.components.ListItemDivider
import com.aliothmoon.maameow.presentation.components.SectionHeader
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.theme.MaaDesignTokens
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber

// 色板预设色值，与 TaskExecutionService 颜色常量匹配
private val ColorSwatches = listOf(
    "#2196F3" to Color(0xFF2196F3), // Blue
    "#4CAF50" to Color(0xFF4CAF50), // Green
    "#FF9800" to Color(0xFFFF9800), // Orange
    "#9C27B0" to Color(0xFF9C27B0), // Purple
    "#E91E63" to Color(0xFFE91E63), // Pink
    "#009688" to Color(0xFF009688), // Teal
    "#F44336" to Color(0xFFF44336), // Red
    "#607D8B" to Color(0xFF607D8B), // Blue Grey
    "#795548" to Color(0xFF795548), // Brown
    "#FFC107" to Color(0xFFFFC107), // Amber
    "#00BCD4" to Color(0xFF00BCD4), // Cyan
    "#8BC34A" to Color(0xFF8BC34A), // Light Green
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveUpdateSettingsView(navController: NavController) {
    val appSettingsManager: AppSettingsManager = koinInject()
    val enabled by appSettingsManager.liveUpdateEnabled.collectAsStateWithLifecycle()
    val chipContent by appSettingsManager.liveUpdateChipContent.collectAsStateWithLifecycle()
    val colorScheme by appSettingsManager.liveUpdateColorScheme.collectAsStateWithLifecycle()
    val customColor by appSettingsManager.liveUpdateCustomColor.collectAsStateWithLifecycle()
    val trackerIcon by appSettingsManager.liveUpdateTrackerIcon.collectAsStateWithLifecycle()
    val customTrackerPath by appSettingsManager.liveUpdateCustomTrackerPath.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val contentColor = MaterialTheme.colorScheme.onSurface
    val context = LocalContext.current

    // 自定义图标选择：从文件选择器取图片（PNG/JPG/WebP 等），复制到内部存储后持久化路径
    val customTrackerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            try {
                val targetDir = File(context.filesDir, "live_update")
                targetDir.mkdirs()
                // 保留原始文件扩展名（BitmapFactory 按内容解码，jpg/webp 均可）
                val displayName = context.contentResolver
                    .query(uri, null, null, null, null)
                    ?.use { cursor ->
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                    }
                val fileName = displayName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { name ->
                        val dot = name.lastIndexOf('.')
                        if (dot > 0) name.substring(dot).lowercase() else null
                    }
                    ?.takeIf { ext -> ext.length in 2..5 && ext.all { it.isLetterOrDigit() || it == '.' } }
                    ?: ".png"
                val target = File(targetDir, "tracker_icon$fileName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                appSettingsManager.setLiveUpdateCustomTrackerPath(target.absolutePath)
            } catch (e: Exception) {
                Timber.e(e, "copy tracker icon failed")
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
            item {
                SectionHeader(stringResource(R.string.live_update_section_general))
                SettingsGroupCard {
                    SettingRow(
                        title = stringResource(R.string.live_update_enable_title),
                        description = stringResource(R.string.live_update_enable_desc),
                        titleColor = contentColor,
                        trailing = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { value ->
                                    coroutineScope.launch {
                                        appSettingsManager.setLiveUpdateEnabled(value)
                                    }
                                }
                            )
                        },
                    )
                }
            }

            item {
                SectionHeader(stringResource(R.string.live_update_section_display))
                SettingsGroupCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SelectableChipGroup(
                            label = stringResource(R.string.live_update_chip_label),
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
                        )
                    }
                    ListItemDivider()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SelectableChipGroup(
                            label = stringResource(R.string.live_update_color_label),
                            selectedValue = colorScheme,
                            options = AppSettingsManager.LiveUpdateColorScheme.entries.map {
                                it to stringResource(it.labelRes)
                            },
                            onSelected = { value ->
                                if (value != colorScheme) {
                                    coroutineScope.launch {
                                        appSettingsManager.setLiveUpdateColorScheme(value)
                                    }
                                }
                            },
                        )
                        // 自定义颜色色板：仅在 CUSTOM 方案时显示
                        if (colorScheme == AppSettingsManager.LiveUpdateColorScheme.CUSTOM) {
                            Text(
                                text = stringResource(R.string.live_update_color_custom_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    top = MaaDesignTokens.Spacing.md,
                                    bottom = MaaDesignTokens.Spacing.xs
                                )
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ColorSwatches.forEach { (hex, color) ->
                                    val isSelected = customColor.equals(hex, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    2.5.dp,
                                                    MaterialTheme.colorScheme.onSurface,
                                                    CircleShape
                                                ) else Modifier
                                            )
                                            .clickable {
                                                coroutineScope.launch {
                                                    appSettingsManager.setLiveUpdateCustomColor(hex)
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                    ListItemDivider()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SelectableChipGroup(
                            label = stringResource(R.string.live_update_icon_label),
                            selectedValue = trackerIcon,
                            options = AppSettingsManager.LiveUpdateTrackerIcon.entries.map {
                                it to stringResource(it.labelRes)
                            },
                            onSelected = { value ->
                                if (value != trackerIcon) {
                                    coroutineScope.launch {
                                        appSettingsManager.setLiveUpdateTrackerIcon(value)
                                    }
                                }
                            },
                        )
                        // 自定义图片：选择 PNG、预览当前已选图片、支持移除
                        if (trackerIcon == AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM) {
                            Text(
                                text = stringResource(R.string.live_update_icon_custom_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    top = MaaDesignTokens.Spacing.md,
                                    bottom = MaaDesignTokens.Spacing.xs
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val iconBitmap = if (customTrackerPath.isNotEmpty()) {
                                    TrackerIconDecoder.decode(customTrackerPath, targetSize = 72)
                                } else null
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    androidx.compose.foundation.layout.Spacer(
                                        modifier = Modifier.size(MaaDesignTokens.Spacing.md)
                                    )
                                }
                                OutlinedButton(
                                    onClick = { customTrackerLauncher.launch("*/*") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.live_update_icon_pick)
                                    )
                                }
                                if (customTrackerPath.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                appSettingsManager.setLiveUpdateCustomTrackerPath("")
                                            }
                                        },
                                        modifier = Modifier.padding(start = MaaDesignTokens.Spacing.xs)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.live_update_icon_clear)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.live_update_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(
                        start = MaaDesignTokens.Spacing.listHorizontal,
                        top = MaaDesignTokens.Spacing.xs
                    )
                )
            }
        }
    }
}