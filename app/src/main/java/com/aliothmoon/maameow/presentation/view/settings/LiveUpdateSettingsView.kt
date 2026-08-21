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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.notification.TrackerIconDecoder
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

// 颜色方案 → 代表色（用于色块预览）
private val SchemeColor: Map<AppSettingsManager.LiveUpdateColorScheme, Color> = mapOf(
    AppSettingsManager.LiveUpdateColorScheme.DEFAULT to Color(0xFF4CAF50),
    AppSettingsManager.LiveUpdateColorScheme.BLUE to Color(0xFF2196F3),
    AppSettingsManager.LiveUpdateColorScheme.GREEN to Color(0xFF4CAF50),
    AppSettingsManager.LiveUpdateColorScheme.ORANGE to Color(0xFFFF9800),
    AppSettingsManager.LiveUpdateColorScheme.PURPLE to Color(0xFF9C27B0),
    AppSettingsManager.LiveUpdateColorScheme.PINK to Color(0xFFE91E63),
    AppSettingsManager.LiveUpdateColorScheme.TEAL to Color(0xFF009688),
    AppSettingsManager.LiveUpdateColorScheme.CUSTOM to Color(0xFF9E9E9E),
)

// 图标方案 → 预览图标资源 ID（CUSTOM 无内置预览）
private val TrackerIconPreview: Map<AppSettingsManager.LiveUpdateTrackerIcon, Int?> = mapOf(
    AppSettingsManager.LiveUpdateTrackerIcon.DEFAULT to R.drawable.ic_progress_tracker,
    AppSettingsManager.LiveUpdateTrackerIcon.LOGO to R.drawable.ic_maa_logo,
    AppSettingsManager.LiveUpdateTrackerIcon.DOT to R.drawable.ic_tracker_dot,
    AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM to null,
)

// 自定义色板
private val ColorSwatches = listOf(
    "#2196F3" to Color(0xFF2196F3),
    "#4CAF50" to Color(0xFF4CAF50),
    "#FF9800" to Color(0xFFFF9800),
    "#9C27B0" to Color(0xFF9C27B0),
    "#E91E63" to Color(0xFFE91E63),
    "#009688" to Color(0xFF009688),
    "#F44336" to Color(0xFFF44336),
    "#607D8B" to Color(0xFF607D8B),
    "#795548" to Color(0xFF795548),
    "#FFC107" to Color(0xFFFFC107),
    "#00BCD4" to Color(0xFF00BCD4),
    "#8BC34A" to Color(0xFF8BC34A),
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

    val customTrackerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            try {
                val targetDir = File(context.filesDir, "live_update")
                targetDir.mkdirs()
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
            // ── 启用开关 ──
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

            // ── 状态栏显示内容 ──
            item {
                SectionHeader(stringResource(R.string.live_update_section_display))
                SettingsGroupCard {
                    Text(
                        text = stringResource(R.string.live_update_chip_label),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = MaaDesignTokens.Spacing.lg,
                            top = MaaDesignTokens.Spacing.lg,
                            end = MaaDesignTokens.Spacing.lg,
                            bottom = MaaDesignTokens.Spacing.xs,
                        )
                    )
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
                        modifier = Modifier.padding(horizontal = MaaDesignTokens.Spacing.lg)
                    )
                    // 底部留白
                    Box(modifier = Modifier.height(MaaDesignTokens.Spacing.md))
                }
            }

            // ── 进度条颜色（视觉色块 + 标签） ──
            item {
                SectionHeader(stringResource(R.string.live_update_color_label))
                SettingsGroupCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = MaaDesignTokens.Spacing.lg,
                                    top = MaaDesignTokens.Spacing.lg,
                                    end = MaaDesignTokens.Spacing.lg,
                                    bottom = if (colorScheme == AppSettingsManager.LiveUpdateColorScheme.CUSTOM) MaaDesignTokens.Spacing.xs
                                    else MaaDesignTokens.Spacing.md,
                                )
                        ) {
                            AppSettingsManager.LiveUpdateColorScheme.entries.forEach { scheme ->
                                val selected = scheme == colorScheme
                                val color = SchemeColor[scheme] ?: Color.Gray
                                val label = stringResource(scheme.labelRes)

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (scheme != colorScheme) {
                                                coroutineScope.launch {
                                                    appSettingsManager.setLiveUpdateColorScheme(scheme)
                                                }
                                            }
                                        },
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        if (scheme == AppSettingsManager.LiveUpdateColorScheme.DEFAULT) {
                                            // 默认语义色：三色小条
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(end = 6.dp)
                                            ) {
                                                listOf(0xFF4CAF50, 0xFF2196F3, 0xFFD32F2F).forEach { c ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(c))
                                                    )
                                                }
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .then(
                                                        if (selected && scheme == AppSettingsManager.LiveUpdateColorScheme.CUSTOM)
                                                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                        else Modifier
                                                    )
                                            )
                                            if (scheme == AppSettingsManager.LiveUpdateColorScheme.CUSTOM) {
                                                // 自定义：无颜色色块，仅用灰色圆 + 文字
                                            }
                                        }
                                        Box(modifier = Modifier.width(6.dp))
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
                        }

                        // 自定义色板
                        if (colorScheme == AppSettingsManager.LiveUpdateColorScheme.CUSTOM) {
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
                                    .padding(
                                        start = MaaDesignTokens.Spacing.lg,
                                        end = MaaDesignTokens.Spacing.lg,
                                        bottom = MaaDesignTokens.Spacing.md,
                                    )
                            ) {
                                ColorSwatches.forEach { (hex, color) ->
                                    val isSelected = customColor.equals(hex, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape
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
                }
            }

            // ── 进度条图标（图标预览 + 标签） ──
            item {
                SectionHeader(stringResource(R.string.live_update_icon_label))
                SettingsGroupCard {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = MaaDesignTokens.Spacing.lg,
                                top = MaaDesignTokens.Spacing.lg,
                                end = MaaDesignTokens.Spacing.lg,
                                bottom = if (trackerIcon == AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM) MaaDesignTokens.Spacing.xs
                                else MaaDesignTokens.Spacing.md,
                            )
                    ) {
                        AppSettingsManager.LiveUpdateTrackerIcon.entries.forEach { icon ->
                            val selected = icon == trackerIcon
                            val label = stringResource(icon.labelRes)
                            val previewId = TrackerIconPreview[icon]

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (icon != trackerIcon) {
                                            coroutineScope.launch {
                                                appSettingsManager.setLiveUpdateTrackerIcon(icon)
                                            }
                                        }
                                    },
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    if (previewId != null) {
                                        Image(
                                            painter = painterResource(previewId),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                        Box(modifier = Modifier.width(6.dp))
                                    } else {
                                        // CUSTOM：用灰色圆 + 加号示意
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
                                        Box(modifier = Modifier.width(6.dp))
                                    }
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
                    }

                    // 自定义图片选择器
                    if (trackerIcon == AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM) {
                        Text(
                            text = stringResource(R.string.live_update_icon_custom_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = MaaDesignTokens.Spacing.lg,
                                end = MaaDesignTokens.Spacing.lg,
                                bottom = MaaDesignTokens.Spacing.xs,
                            )
                        )
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
                            val iconBitmap = remember(customTrackerPath) {
                                if (customTrackerPath.isNotEmpty())
                                    TrackerIconDecoder.decode(customTrackerPath, targetSize = 72)
                                else null
                            }
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Box(modifier = Modifier.size(MaaDesignTokens.Spacing.md))
                            }
                            OutlinedButton(
                                onClick = { customTrackerLauncher.launch("*/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = stringResource(R.string.live_update_icon_pick))
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
                    text = stringResource(R.string.live_update_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(
                        start = MaaDesignTokens.Spacing.listHorizontal,
                        top = MaaDesignTokens.Spacing.xs,
                        bottom = MaaDesignTokens.Spacing.xl,
                    )
                )
            }
        }
    }
}