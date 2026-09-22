package com.aliothmoon.maameow.presentation.view.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import com.aliothmoon.maameow.data.notification.live.TrackerIconDecoder
import com.aliothmoon.maameow.data.notification.live.TrackerIconStore
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.notification.LiveCapability
import com.aliothmoon.maameow.domain.notification.LiveUpdatePublisher
import com.aliothmoon.maameow.presentation.LocalToaster
import com.aliothmoon.maameow.presentation.components.SectionHeader
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.dokar.sonner.ToastType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

// 自定义色板：只列预设单色之外的补充色，避免与上面的单色方案重复
private data class ColorSwatch(
    val hex: String,
    val color: Color,
    @param:StringRes val labelRes: Int,
)

private val ColorSwatches = listOf(
    ColorSwatch("#F44336", Color(0xFFF44336), R.string.live_update_color_swatch_red),
    ColorSwatch("#8BC34A", Color(0xFF8BC34A), R.string.live_update_color_swatch_light_green),
    ColorSwatch("#00BCD4", Color(0xFF00BCD4), R.string.live_update_color_swatch_cyan),
    ColorSwatch("#FFC107", Color(0xFFFFC107), R.string.live_update_color_swatch_amber),
    ColorSwatch("#795548", Color(0xFF795548), R.string.live_update_color_swatch_brown),
    ColorSwatch("#607D8B", Color(0xFF607D8B), R.string.live_update_color_swatch_blue_grey),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveUpdateSettingsView(navController: NavController) {
    val appSettingsManager: AppSettingsManager = koinInject()
    val livePublisher: LiveUpdatePublisher = koinInject()
    val trackerIconStore: TrackerIconStore = koinInject()
    val useHyperIsland by appSettingsManager.liveUpdateUseHyperIsland.collectAsStateWithLifecycle()
    val bypassEnabled by appSettingsManager.liveIslandXmsfBypass.collectAsStateWithLifecycle()
    val chipContent by appSettingsManager.liveUpdateChipContent.collectAsStateWithLifecycle()
    val colorScheme by appSettingsManager.liveUpdateColorScheme.collectAsStateWithLifecycle()
    val customColor by appSettingsManager.liveUpdateCustomColor.collectAsStateWithLifecycle()
    val trackerIcon by appSettingsManager.liveUpdateTrackerIcon.collectAsStateWithLifecycle()
    val customTrackerPath by appSettingsManager.liveUpdateCustomTrackerPath.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val contentColor = MaterialTheme.colorScheme.onSurface
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val pickFailedMessage = stringResource(R.string.live_update_icon_pick_failed)

    // 同一设置在不同系统作用面不同：超级岛设备作用于岛，原生设备作用于状态栏/进度条，
    // 文案随实际生效的展示方式变化，避免误导。
    // capability 查询含跨进程权限检查，放 IO，避免组合期在主线程做 IPC
    val capability by produceState<LiveCapability?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching { livePublisher.capability }.getOrNull()
        }
    }
    // 文案绑「使用小米超级岛」开关，但只有岛真的会生效时才用岛的口径：
    // 关掉兼容模式或缺焦点通知权限时路由会退到原生，这里跟着按原生显示，避免误导
    val onIsland = Build.VERSION.SDK_INT >= 36 &&
        capability?.focusLikely == true &&
        capability?.focusGranted == true &&
        useHyperIsland &&
        bypassEnabled
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
            // 选图失败（复制失败/不是有效图片）给出反馈，避免静默不生效
            if (!pickCustomIcon(context, uri, appSettingsManager, trackerIconStore)) {
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
                                bottom = if (colorScheme == AppSettingsManager.LiveUpdateColorScheme.CUSTOM)
                                    MaaDesignTokens.Spacing.xs else MaaDesignTokens.Spacing.md,
                            )
                    ) {
                        AppSettingsManager.LiveUpdateColorScheme.entries.forEach { scheme ->
                            val isSelected = scheme == colorScheme
                            val color = SchemeColor[scheme] ?: Color.Gray
                            val label = stringResource(scheme.labelRes)

                            Surface(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        if (scheme != colorScheme) {
                                            coroutineScope.launch {
                                                appSettingsManager.setLiveUpdateColorScheme(scheme)
                                            }
                                        }
                                    }
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = label
                                        selected = isSelected
                                        role = Role.RadioButton
                                    },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    if (scheme == AppSettingsManager.LiveUpdateColorScheme.DEFAULT) {
                                        // 默认：状态语义色用三色小条示意
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
                                        )
                                        Box(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
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
                                        .background(swatch.color)
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
                                        // 色块没有可见文字，补无障碍标签与选中语义
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
                                bottom = if (trackerIcon == AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM)
                                    MaaDesignTokens.Spacing.xs else MaaDesignTokens.Spacing.md,
                            )
                    ) {
                        AppSettingsManager.LiveUpdateTrackerIcon.entries.forEach { icon ->
                            val isSelected = icon == trackerIcon
                            val label = stringResource(icon.labelRes)
                            val previewId = TrackerIconPreview[icon]

                            Surface(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        if (icon != trackerIcon) {
                                            coroutineScope.launch {
                                                appSettingsManager.setLiveUpdateTrackerIcon(icon)
                                            }
                                        }
                                    }
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = label
                                        selected = isSelected
                                        role = Role.RadioButton
                                    },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium,
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
                                                // 白色图标（合成玉/圆点）在浅色底上几乎不可见，预览底改用中灰
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        )
                                    } else {
                                        // 自定义：灰色圆 + 加号示意
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
                                    Box(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    // 自定义图片：预览 + 选择 / 清除
                    if (trackerIcon == AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM) {
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
                            // 预览解码放到 IO，避免组合期做磁盘 IO 与位图解码
                            val iconBitmap by produceState<Bitmap?>(
                                initialValue = null,
                                key1 = customTrackerPath,
                            ) {
                                value = if (customTrackerPath.isNotEmpty()) {
                                    withContext(Dispatchers.IO) {
                                        TrackerIconDecoder.decode(customTrackerPath, targetSize = 72)
                                    }
                                } else {
                                    null
                                }
                            }
                            val bitmap = iconBitmap
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        // 同上：预览底用中灰，白/透明图标才看得见
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                )
                                Box(modifier = Modifier.size(MaaDesignTokens.Spacing.md))
                            }
                            Text(
                                text = stringResource(R.string.live_update_icon_custom_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Box(modifier = Modifier.size(MaaDesignTokens.Spacing.sm))
                            OutlinedButton(
                                onClick = { customTrackerLauncher.launch("image/*") },
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(text = stringResource(R.string.live_update_icon_pick))
                            }
                            if (customTrackerPath.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            clearCustomIcon(context, customTrackerPath, appSettingsManager)
                                        }
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

/**
 * 把选中的图片复制到应用内部存储，返回文件路径。
 *
 * 文件名带时间戳：路径变化才能让缓存与预览解码重新执行，避免同路径重选不生效。
 * 文件操作与设置写入放在同一临界区内按顺序完成，避免连续选图时后一次清理删掉前一次
 * 刚写入的路径；复制失败或产物为空时删掉半成品，不留下失效路径。
 */
/**
 * 选图入库：复制到应用内部存储、校验可解码、写设置、清理旧图。返回是否成功。
 *
 * 先校验再写设置：非图片或损坏文件直接丢弃，不把无效路径落盘；
 * 成功时顺带预热通知侧的解码缓存，结果/测试通知也能立刻用上自定义图标。
 */
private suspend fun pickCustomIcon(
    context: Context,
    uri: Uri,
    settings: AppSettingsManager,
    iconStore: TrackerIconStore,
): Boolean {
    iconFileMutex.withLock {
        val path = copyIconToFiles(context, uri) ?: return false
        if (!isDecodableIcon(path)) {
            deleteIconFile(context, path)
            return false
        }
        // 先让新路径生效再清理旧图：即便设置写入失败，也只是残留旧文件，不会让设置指向已删除的图标
        settings.setLiveUpdateCustomTrackerPath(path)
        deleteOtherIcons(context, path)
        iconStore.warmUp()
        return true
    }
}

private suspend fun isDecodableIcon(path: String): Boolean =
    withContext(Dispatchers.IO) { TrackerIconDecoder.decode(path) != null }

/** 清除自定义图标：删掉已复制的图片，并清空设置（同一临界区，避免与选图交错） */
private suspend fun clearCustomIcon(
    context: Context,
    path: String,
    settings: AppSettingsManager,
) {
    iconFileMutex.withLock {
        deleteIconFile(context, path)
        // 与备份回退一致：路径清空后图标类型回落内置方案，避免设置页停在「自定义」却没有图片
        settings.setLiveUpdateTrackerIcon(AppSettingsManager.LiveUpdateTrackerIcon.DEFAULT)
        settings.setLiveUpdateCustomTrackerPath("")
    }
}

private suspend fun copyIconToFiles(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, ICON_DIR).apply { mkdirs() }
            val target = File(dir, "tracker_icon_${System.currentTimeMillis()}")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
            } catch (e: Exception) {
                target.delete()
                throw e
            }
            if (!target.exists() || target.length() == 0L) {
                target.delete()
                return@runCatching null
            }
            target.absolutePath
        }.onFailure { Timber.e(it, "copy custom icon failed") }.getOrNull()
    }

/** 复制成功后清掉历史图标，不残留旧图 */
private suspend fun deleteOtherIcons(context: Context, keepPath: String) =
    withContext(Dispatchers.IO) {
        runCatching {
            val keep = File(keepPath)
            val dir = File(context.filesDir, ICON_DIR)
            dir.listFiles()?.forEach { file ->
                if (file != keep && file.name.startsWith(ICON_PREFIX)) file.delete()
            }
        }.onFailure { Timber.e(it, "clean stale custom icons failed") }
    }

private suspend fun deleteIconFile(context: Context, path: String) =
    withContext(Dispatchers.IO) {
        if (path.isEmpty()) return@withContext
        runCatching {
            val file = File(path)
            // 只删应用内部 live_update 目录下的文件，避免异常路径越界删除
            if (file.parentFile == File(context.filesDir, ICON_DIR)) file.delete()
        }.onFailure { Timber.e(it, "delete custom icon failed") }
    }

private val iconFileMutex = Mutex()
private const val ICON_DIR = "live_update"
private const val ICON_PREFIX = "tracker_icon_"

