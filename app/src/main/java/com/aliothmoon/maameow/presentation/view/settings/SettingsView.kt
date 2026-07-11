package com.aliothmoon.maameow.presentation.view.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.LogExportService
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.manager.ShizukuInstallHelper
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.ListItemDivider
import com.aliothmoon.maameow.presentation.components.ReInitializeConfirmDialog
import com.aliothmoon.maameow.presentation.components.ResourceInitDialog
import com.aliothmoon.maameow.presentation.components.SectionHeader
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import com.aliothmoon.maameow.utils.i18n.resolve
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

@Stable
private class WallpaperCropState {
    var scale by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var rotationDegrees by mutableFloatStateOf(0f)
    var initialized by mutableStateOf(false)
    var screenW by mutableFloatStateOf(0f)
    var screenH by mutableFloatStateOf(0f)
    var cropW by mutableFloatStateOf(0f)
    var cropH by mutableFloatStateOf(0f)
    var cropLeft by mutableFloatStateOf(0f)
    var cropTop by mutableFloatStateOf(0f)
    var restoredScreenW = 0f
    var restoredScreenH = 0f

    fun constrain(
        displayWidth: Float,
        displayHeight: Float,
        minimumScale: Float,
        cropWidth: Float,
        cropHeight: Float,
    ) {
        val constrained = WallpaperCropMath.constrainTransform(
            scale = scale,
            panX = panX,
            panY = panY,
            rotationDegrees = rotationDegrees,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            cropWidth = cropWidth,
            cropHeight = cropHeight,
            minimumScale = minimumScale,
        )
        scale = constrained.scale
        panX = constrained.panX
        panY = constrained.panY
    }

    fun getCroppedBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        scale: Float,
        panX: Float,
        panY: Float,
        rotationDegrees: Float,
    ): Bitmap? {
        val sw = screenW
        val sh = screenH
        if (sw <= 0f || sh <= 0f || cropW <= 0f || cropH <= 0f) return null

        val bw = source.width.toFloat()
        val bh = source.height.toFloat()
        val baseScale = min(sw / bw, sh / bh)

        val matrix = Matrix().apply {
            postScale(baseScale, baseScale)
            postTranslate((sw - bw * baseScale) / 2f, (sh - bh * baseScale) / 2f)
            postTranslate(-sw / 2f, -sh / 2f)
            postScale(scale, scale)
            postRotate(rotationDegrees)
            postTranslate(sw / 2f, sh / 2f)
            postTranslate(panX, panY)
            postTranslate(-cropLeft, -cropTop)
            postScale(targetWidth / cropW, targetHeight / cropH)
        }

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        return try {
            Canvas(output).drawBitmap(source, matrix, null)
            output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
    }

    companion object {
        val Saver = listSaver<WallpaperCropState, Any>(
            save = {
                listOf(
                    it.scale, it.panX, it.panY, it.rotationDegrees, it.initialized,
                    it.screenW, it.screenH,
                )
            },
            restore = {
                WallpaperCropState().apply {
                    scale = it[0] as Float
                    panX = it[1] as Float
                    panY = it[2] as Float
                    rotationDegrees = it[3] as Float
                    initialized = it[4] as Boolean
                    if (it.size >= 7) {
                        restoredScreenW = it[5] as Float
                        restoredScreenH = it[6] as Float
                    }
                }
            },
        )
    }
}

@Composable
fun SettingsView(
    navController: NavController,
    onViewAnnouncement: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
    resourceInitService: ResourceInitService = koinInject(),
    logExportService: LogExportService = koinInject(),
    achievementReporter: AchievementReporter = koinInject(),
) {
    val resourceInitState by resourceInitService.state.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val autoCheckUpdate by viewModel.autoCheckUpdate.collectAsStateWithLifecycle()
    val autoDownloadUpdate by viewModel.autoDownloadUpdate.collectAsStateWithLifecycle()
    val startupBackend by viewModel.startupBackend.collectAsStateWithLifecycle()
    val skipShizukuCheck by viewModel.skipShizukuCheck.collectAsStateWithLifecycle()
    val shizukuShortcutEnabled by viewModel.shizukuShortcutEnabled.collectAsStateWithLifecycle()
    val shizukuLaunchPackage by viewModel.shizukuLaunchPackage.collectAsStateWithLifecycle()
    val deploymentWithPause by viewModel.deploymentWithPause.collectAsStateWithLifecycle()
    val forceFullscreenOnVirtualDisplay by viewModel.forceFullscreenOnVirtualDisplay.collectAsStateWithLifecycle()
    val allowForegroundScheduledTask by viewModel.allowForegroundScheduledTask.collectAsStateWithLifecycle()
    val runScheduleWhenLocked by viewModel.runScheduleWhenLocked.collectAsStateWithLifecycle()
    val tasksOverrideEnabled by viewModel.tasksOverrideEnabled.collectAsStateWithLifecycle()
    val updateChannel by viewModel.updateChannel.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val useSystemMonetColor by viewModel.useSystemMonetColor.collectAsStateWithLifecycle()
    val customWallpaperPath by viewModel.customWallpaperPath.collectAsStateWithLifecycle()
    val cardOpacity by viewModel.cardOpacity.collectAsStateWithLifecycle()

    val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    val showAchievementSnackbar by viewModel.showAchievementSnackbar.collectAsStateWithLifecycle()
    val backgroundResolution by viewModel.backgroundResolution.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val showRestartDialog by viewModel.showRestartDialog.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.let { viewModel.exportConfig(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.let { viewModel.importConfig(it) }
    }

    var wallpaperSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var wallpaperSourceUri by rememberSaveable { mutableStateOf<String?>(null) }
    val wallpaperCropState = rememberSaveable(saver = WallpaperCropState.Saver) { WallpaperCropState() }
    DisposableEffect(wallpaperSourceBitmap) {
        val bitmap = wallpaperSourceBitmap
        onDispose { bitmap?.recycle() }
    }
    LaunchedEffect(wallpaperSourceUri) {
        val uri = wallpaperSourceUri ?: return@LaunchedEffect
        if (wallpaperSourceBitmap == null) {
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(context, Uri.parse(uri)) }
            if (bitmap != null) wallpaperSourceBitmap = bitmap else {
                wallpaperSourceUri = null
                Toast.makeText(context, R.string.settings_custom_wallpaper_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val wallpaperLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(context, uri) }
            if (bitmap != null) {
                wallpaperCropState.initialized = false
                wallpaperSourceUri = uri.toString()
                wallpaperSourceBitmap = bitmap
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_custom_wallpaper_load_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    var showShizukuAppPicker by remember { mutableStateOf(false) }
    var shizukuAppPickerLoadKey by remember { mutableStateOf(0) }
    var shizukuAppSearch by remember { mutableStateOf("") }
    var shizukuAppOptions by remember { mutableStateOf<List<ShizukuLaunchAppOption>?>(null) }
    var shizukuAppLoadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(showShizukuAppPicker, shizukuAppPickerLoadKey) {
        if (!showShizukuAppPicker) return@LaunchedEffect

        shizukuAppLoadFailed = false
        shizukuAppOptions = null
        shizukuAppOptions = try {
            withContext(Dispatchers.IO) {
                loadShizukuLaunchApps(context.applicationContext)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            shizukuAppLoadFailed = true
            emptyList()
        }
    }

    backupMessage?.let { msg ->
        Toast.makeText(context, msg.resolve(context), Toast.LENGTH_SHORT).show()
        viewModel.clearBackupMessage()
    }

    var showReInitConfirm by remember { mutableStateOf(false) }
    var showDebugModeConfirm by remember { mutableStateOf(false) }
    var showRunScheduleWhenLockedConfirm by remember { mutableStateOf(false) }

    if (showRestartDialog) {
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.dialog_import_success_title),
            message = stringResource(R.string.dialog_import_success_message),
            icon = Icons.Rounded.Build,
            confirmText = stringResource(R.string.common_restart_now),
            dismissText = stringResource(R.string.common_restart_later),
            onConfirm = { viewModel.confirmRestart() },
            onDismissRequest = { viewModel.dismissRestartDialog() }
        )
    }

    if (wallpaperSourceUri != null && wallpaperSourceBitmap != null) {
        WallpaperCropFullScreen(
            sourceBitmap = wallpaperSourceBitmap!!,
            cropState = wallpaperCropState,
            onCancel = {
                wallpaperSourceBitmap = null
                wallpaperSourceUri = null
            },
            onConfirm = { bitmap ->
                val file = File(context.filesDir, "custom_wallpaper_${System.currentTimeMillis()}.jpg")
                withContext(NonCancellable) {
                    var committed = false
                    try {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                file.outputStream().use { out ->
                                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out))
                                }
                            }.isSuccess
                        }
                        committed = saved && viewModel.setCustomWallpaperPath(file.absolutePath)
                        if (committed) {
                            wallpaperSourceBitmap = null
                            wallpaperSourceUri = null
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_custom_wallpaper_load_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } finally {
                        bitmap.recycle()
                        if (!committed) withContext(Dispatchers.IO) { file.delete() }
                    }
                }
            },
        )
        return
    }

    if (showReInitConfirm) {
        ReInitializeConfirmDialog(
            onConfirm = {
                showReInitConfirm = false
                coroutineScope.launch {
                    resourceInitService.reInitialize()
                }
            },
            onDismiss = { showReInitConfirm = false }
        )
    }

    if (showDebugModeConfirm) {
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.dialog_enable_debug_title),
            message = stringResource(R.string.dialog_enable_debug_message),
            onConfirm = {
                showDebugModeConfirm = false
                viewModel.setDebugMode(true)
            },
            onDismissRequest = { showDebugModeConfirm = false },
            confirmText = stringResource(R.string.common_confirm_restart),
            dismissText = stringResource(R.string.common_cancel),
            icon = Icons.Rounded.Build
        )
    }

    if (showRunScheduleWhenLockedConfirm) {
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.dialog_run_schedule_when_locked_title),
            message = stringResource(R.string.dialog_run_schedule_when_locked_message),
            onConfirm = {
                showRunScheduleWhenLockedConfirm = false
                viewModel.setRunScheduleWhenLocked(true)
            },
            onDismissRequest = { showRunScheduleWhenLockedConfirm = false },
            confirmText = stringResource(R.string.dialog_run_schedule_when_locked_confirm),
            dismissText = stringResource(R.string.common_cancel),
            icon = Icons.Rounded.Build
        )
    }

    if (resourceInitState is ResourceInitState.Extracting) {
        ResourceInitDialog(
            state = resourceInitState,
            onRetry = {}
        )
    }

    if (showShizukuAppPicker) {
        val searchText = shizukuAppSearch.trim()
        val filteredOptions = shizukuAppOptions
            ?.filter { option ->
                searchText.isBlank() ||
                        option.label.contains(searchText, ignoreCase = true) ||
                        option.packageName.contains(searchText, ignoreCase = true)
            }
            .orEmpty()

        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.settings_shizuku_launch_app_picker_title),
            icon = Icons.Rounded.Build,
            confirmText = stringResource(R.string.common_close),
            dismissText = "",
            onConfirm = { showShizukuAppPicker = false },
            onDismissRequest = { showShizukuAppPicker = false },
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when {
                        shizukuAppOptions == null -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.settings_shizuku_launch_app_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        shizukuAppLoadFailed -> {
                            Text(
                                text = stringResource(R.string.settings_shizuku_launch_app_picker_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        else -> {
                            ITextField(
                                value = shizukuAppSearch,
                                onValueChange = { shizukuAppSearch = it },
                                placeholder = stringResource(R.string.settings_shizuku_launch_app_search_hint),
                                singleLine = true
                            )

                            if (filteredOptions.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_shizuku_launch_app_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 320.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filteredOptions, key = { it.packageName }) { option ->
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        viewModel.setShizukuLaunchPackage(option.packageName)
                                                        showShizukuAppPicker = false
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = option.label,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = option.packageName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_title)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        val contentColor = MaterialTheme.colorScheme.onSurface

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                horizontal = MaaDesignTokens.Spacing.listHorizontal,
                vertical = MaaDesignTokens.Spacing.sm
            ),
            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sectionGap)
        ) {
            // 更新管理
            item {
                SectionHeader(stringResource(R.string.settings_section_update))
                SettingsGroupCard {
                    SettingClickItem(
                        title = stringResource(R.string.settings_reinit_resource_title),
                        description = stringResource(R.string.settings_reinit_resource_desc),
                        contentColor = contentColor
                    ) {
                        showReInitConfirm = true
                    }
                    ListItemDivider()
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_auto_check_update_title),
                        description = stringResource(R.string.settings_auto_check_update_desc),
                        contentColor = contentColor,
                        checked = autoCheckUpdate,
                        onCheckedChange = { viewModel.setAutoCheckUpdate(it) }
                    )
                    ListItemDivider()
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_auto_download_update_title),
                        description = stringResource(R.string.settings_auto_download_update_desc),
                        contentColor = contentColor,
                        checked = autoDownloadUpdate,
                        enabled = autoCheckUpdate,
                        onCheckedChange = { viewModel.setAutoDownloadUpdate(it) }
                    )
                    ListItemDivider()
                    SettingChannelItem(
                        contentColor = contentColor,
                        selectedChannel = updateChannel,
                        onChannelSelected = { viewModel.setUpdateChannel(it) }
                    )
                }
            }

            // 日志
            item {
                SectionHeader(stringResource(R.string.settings_section_log))
                SettingsGroupCard {
                    SettingClickItem(
                        title = stringResource(R.string.settings_log_history_title),
                        description = stringResource(R.string.settings_log_history_desc),
                        contentColor = contentColor
                    ) {
                        navController.navigate("log_history")
                    }
                    ListItemDivider()
                    SettingClickItem(
                        title = stringResource(R.string.settings_log_error_title),
                        description = stringResource(R.string.settings_log_error_desc),
                        contentColor = contentColor
                    ) {
                        navController.navigate("error_log")
                    }
                    ListItemDivider()
                    val logExportChooserTitle = stringResource(R.string.settings_log_export_chooser_title)
                    SettingClickItem(
                        title = stringResource(R.string.settings_log_export_title),
                        description = stringResource(R.string.settings_log_export_desc),
                        contentColor = contentColor
                    ) {
                        coroutineScope.launch {
                            val intent = logExportService.exportAllLogs()
                            if (intent != null) {
                                context.startActivity(Intent.createChooser(intent, logExportChooserTitle))
                            }
                        }
                    }
                    ListItemDivider()
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_debug_mode_title),
                        description = stringResource(R.string.settings_debug_mode_desc),
                        contentColor = contentColor,
                        checked = debugMode,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showDebugModeConfirm = true
                            } else {
                                viewModel.setDebugMode(false)
                            }
                        }
                    )
                }
            }

            // 显示设置
            item {
                SectionHeader(stringResource(R.string.settings_section_display))
                SettingsGroupCard {
                    SettingLanguageItem(
                        contentColor = contentColor,
                        selectedLanguage = language,
                        onLanguageSelected = { viewModel.setLanguage(it) }
                    )
                    ListItemDivider()
                    SettingThemeSection(
                        contentColor = contentColor,
                        selectedMode = themeMode,
                        onModeSelected = { viewModel.setThemeMode(it) },
                        useSystemMonetColor = useSystemMonetColor,
                        onMonetColorChanged = { viewModel.setUseSystemMonetColor(it) },
                        customWallpaperPath = customWallpaperPath,
                        onPickWallpaper = { wallpaperLauncher.launch("image/*") },
                        onClearWallpaper = { viewModel.clearCustomWallpaper() },
                        cardOpacity = cardOpacity,
                        onCardOpacityChanged = { viewModel.setCardOpacity(it) },
                        fontSizeScale = fontSizeScale,
                        onFontSizeScaleChanged = { viewModel.setFontSizeScale(it) }
                    )
                }
            }

            // 其他设置
            item {
                SectionHeader(stringResource(R.string.settings_section_other))
                SettingsGroupCard {
                    SettingRemoteBackendItem(
                        contentColor = contentColor,
                        selectedBackend = startupBackend,
                        onBackendSelected = { viewModel.setStartupBackend(it) }
                    )
                    ListItemDivider()
                    if (startupBackend == RemoteBackend.SHIZUKU) {
                        SettingSwitchItem(
                            title = stringResource(R.string.settings_shizuku_launch_mode_title),
                            description = stringResource(R.string.settings_shizuku_launch_mode_desc),
                            contentColor = contentColor,
                            checked = shizukuShortcutEnabled,
                            onCheckedChange = { viewModel.setShizukuShortcutEnabled(it) }
                        )
                        ListItemDivider()
                        AnimatedVisibility(
                            visible = shizukuShortcutEnabled,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column {
                                val shizukuLaunchAppName = ShizukuInstallHelper.getLaunchAppLabel(
                                    context,
                                    shizukuLaunchPackage
                                )
                                val shizukuLaunchAppDescription = if (shizukuLaunchPackage == OFFICIAL_SHIZUKU_PACKAGE) {
                                    stringResource(R.string.settings_shizuku_launch_app_default_desc)
                                } else {
                                    stringResource(
                                        R.string.settings_shizuku_launch_app_selected_desc,
                                        shizukuLaunchAppName ?: shizukuLaunchPackage
                                    )
                                }
                                SettingClickItem(
                                    title = stringResource(R.string.settings_shizuku_launch_app_title),
                                    description = shizukuLaunchAppDescription,
                                    contentColor = contentColor
                                ) {
                                    // 先展示弹窗，再异步查询应用列表，避免点击后长时间无反馈。
                                    shizukuAppSearch = ""
                                    shizukuAppPickerLoadKey += 1
                                    showShizukuAppPicker = true
                                }
                                ListItemDivider()
                                SettingClickItem(
                                    title = stringResource(R.string.settings_shizuku_launch_app_reset_title),
                                    description = stringResource(R.string.settings_shizuku_launch_app_reset_desc),
                                    contentColor = contentColor
                                ) {
                                    viewModel.setShizukuLaunchPackage(OFFICIAL_SHIZUKU_PACKAGE)
                                }
                                ListItemDivider()
                            }
                        }
                    }
                    ListItemDivider()
                    SettingBackgroundResolutionItem(
                        contentColor = contentColor,
                        selectedPreference = backgroundResolution,
                        onPreferenceSelected = { viewModel.setBackgroundResolution(it) }
                    )
                    ListItemDivider()
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_skip_shizuku_check),
                        contentColor = contentColor,
                        checked = skipShizukuCheck,
                        enabled = startupBackend == RemoteBackend.SHIZUKU,
                        onCheckedChange = { viewModel.setSkipShizukuCheck(it) }
                    )
                    ListItemDivider()
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_deployment_with_pause),
                        description = stringResource(R.string.settings_deployment_with_pause_tip),
                        contentColor = contentColor,
                        checked = deploymentWithPause,
                        onCheckedChange = { viewModel.setDeploymentWithPause(it) }
                    )
                    ListItemDivider()
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_force_fullscreen_on_virtual_display),
                        contentColor = contentColor,
                        checked = forceFullscreenOnVirtualDisplay,
                        onCheckedChange = { viewModel.setForceFullscreenOnVirtualDisplay(it) }
                    )
                    ListItemDivider()
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_allow_foreground_scheduled_task),
                        contentColor = contentColor,
                        checked = allowForegroundScheduledTask,
                        onCheckedChange = { viewModel.setAllowForegroundScheduledTask(it) }
                    )
                    ListItemDivider()
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_run_schedule_when_locked),
                        contentColor = contentColor,
                        checked = runScheduleWhenLocked,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showRunScheduleWhenLockedConfirm = true
                            } else {
                                viewModel.setRunScheduleWhenLocked(false)
                            }
                        }
                    )
                    ListItemDivider()
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_tasks_override_title),
                        description = stringResource(R.string.settings_tasks_override_desc),
                        contentColor = contentColor,
                        checked = tasksOverrideEnabled,
                        onCheckedChange = { viewModel.setTasksOverrideEnabled(it) }
                    )
                    AnimatedVisibility(
                        visible = tasksOverrideEnabled,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            ListItemDivider()
                            SettingClickItem(
                                title = stringResource(R.string.settings_tasks_override_edit_title),
                                contentColor = contentColor
                            ) {
                                navController.navigate(Routes.TASK_OVERRIDE_EDITOR)
                            }
                        }
                    }
                }
            }

            // 数据管理
            item {
                SectionHeader(stringResource(R.string.settings_section_data))
                SettingsGroupCard {
                    SettingClickItem(
                        title = stringResource(R.string.settings_export_config_title),
                        description = stringResource(R.string.settings_export_config_desc),
                        contentColor = contentColor
                    ) {
                        exportLauncher.launch("maameow_config.json")
                    }
                    ListItemDivider()
                    SettingClickItem(
                        title = stringResource(R.string.settings_import_config_title),
                        description = stringResource(R.string.settings_import_config_desc),
                        contentColor = contentColor
                    ) {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                }
            }

            // 通知
            item {
                SectionHeader(stringResource(R.string.settings_section_notification))
                SettingsGroupCard {
                    SettingClickItem(
                        title = stringResource(R.string.settings_notification_title),
                        description = stringResource(R.string.settings_notification_desc),
                        contentColor = contentColor
                    ) {
                        navController.navigate(Routes.NOTIFICATION)
                    }
                }
            }

            // 成就
            item {
                SectionHeader(stringResource(R.string.settings_section_achievement))
                SettingsGroupCard {
                    SettingClickItem(
                        title = stringResource(R.string.settings_achievement_title),
                        description = stringResource(R.string.settings_achievement_desc),
                        contentColor = contentColor
                    ) {
                        navController.navigate(Routes.ACHIEVEMENT)
                    }
                    ListItemDivider()
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_achievement_snackbar_title),
                        description = stringResource(R.string.settings_achievement_snackbar_desc),
                        contentColor = contentColor,
                        checked = showAchievementSnackbar,
                        onCheckedChange = { viewModel.setShowAchievementSnackbar(it) }
                    )
                    if (BuildConfig.DEBUG) {
                        ListItemDivider()
                        SettingClickItem(
                            title = stringResource(R.string.settings_achievement_debug_title),
                            description = stringResource(R.string.settings_achievement_debug_desc),
                            contentColor = contentColor
                        ) {
                            navController.navigate(Routes.ACHIEVEMENT_DEBUG)
                        }
                    }
                }
            }

            // 关于
            item {
                SectionHeader(stringResource(R.string.settings_section_about))
                SettingsGroupCard {
                    SettingInfoRow(
                        label = stringResource(R.string.settings_about_version),
                        value = BuildConfig.VERSION_NAME,
                        contentColor = contentColor,
                    )
                    ListItemDivider()
                    SettingInfoRow(
                        label = stringResource(R.string.settings_about_developer),
                        value = "Aliothmoon",
                        contentColor = contentColor
                    )
                    ListItemDivider()
                    SettingClickItem(
                        title = stringResource(R.string.settings_about_qq_group_title),
                        description = stringResource(R.string.settings_about_qq_group_desc),
                        contentColor = contentColor
                    ) {
                        achievementReporter.reportFeedbackGroupOpened()
                        Misc.openUriSafely(context, "https://qm.qq.com/q/j4CFbeDQXu")
                    }
                    ListItemDivider()
                    SettingClickItem(
                        title = stringResource(R.string.settings_about_announcement),
                        contentColor = contentColor
                    ) {
                        onViewAnnouncement()
                    }
                    ListItemDivider()
                    Text(
                        text = stringResource(R.string.settings_about_star),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Misc.openUriSafely(context, "https://github.com/Aliothmoon/MAA-Meow")
                            }
                            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingThemeSection(
    contentColor: Color,
    selectedMode: AppSettingsManager.ThemeMode,
    onModeSelected: (AppSettingsManager.ThemeMode) -> Unit,
    useSystemMonetColor: Boolean,
    onMonetColorChanged: (Boolean) -> Unit,
    customWallpaperPath: String,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    cardOpacity: Int,
    onCardOpacityChanged: (Int) -> Unit,
    fontSizeScale: Int,
    onFontSizeScaleChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题
        Text(
            text = stringResource(R.string.settings_theme_title),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor
        )
        // 主题模式选择
        Row(modifier = Modifier.fillMaxWidth()) {
            val modes = listOf(
                AppSettingsManager.ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                AppSettingsManager.ThemeMode.WHITE to stringResource(R.string.settings_theme_white),
                AppSettingsManager.ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                AppSettingsManager.ThemeMode.PURE_DARK to stringResource(R.string.settings_theme_pure_dark)
            )
            modes.forEach { (mode, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = mode == selectedMode,
                            onClick = { onModeSelected(mode) },
                            role = Role.RadioButton
                        )
                ) {
                    RadioButton(
                        selected = mode == selectedMode,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        maxLines = 1
                    )
                }
            }
        }
        // Android 12 以下仅在自定义壁纸可提供取色来源时显示。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || customWallpaperPath.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_monet_color_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                    Text(
                        text = stringResource(R.string.settings_monet_color_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                }
                Switch(checked = useSystemMonetColor, onCheckedChange = onMonetColorChanged)
            }
        }
        SettingClickItem(
            title = stringResource(R.string.settings_custom_wallpaper_title),
            description = if (customWallpaperPath.isBlank()) {
                stringResource(R.string.settings_custom_wallpaper_desc)
            } else {
                stringResource(R.string.settings_custom_wallpaper_enabled_desc)
            },
            contentColor = contentColor,
            onClick = onPickWallpaper,
        )
        if (customWallpaperPath.isNotBlank()) {
            OutlinedButton(onClick = onClearWallpaper) {
                Text(stringResource(R.string.settings_custom_wallpaper_clear))
            }
        }
        CardOpacitySetting(
            contentColor = contentColor,
            value = cardOpacity,
            onValueChange = onCardOpacityChanged,
        )
        // 页面缩放
        FontSizeSetting(
            contentColor = contentColor,
            value = fontSizeScale,
            onValueChange = onFontSizeScaleChanged
        )
    }
}

@Composable
private fun WallpaperCropFullScreen(
    sourceBitmap: Bitmap,
    cropState: WallpaperCropState,
    onCancel: () -> Unit,
    onConfirm: suspend (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var isTouching by remember { mutableStateOf(false) }

    BackHandler(enabled = !isSaving, onBack = onCancel)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val totalW = constraints.maxWidth.toFloat()
        val totalH = constraints.maxHeight.toFloat()
        val targetWidth = rootView.width.takeIf { it > 0 } ?: constraints.maxWidth
        val targetHeight = rootView.height.takeIf { it > 0 } ?: constraints.maxHeight
        val screenRatio = targetWidth.toFloat() / targetHeight
        val maxCropW = totalW * 0.70f
        val maxCropH = totalH * 0.50f
        val cropW: Float
        val cropH: Float
        if (maxCropW / screenRatio <= maxCropH) {
            cropW = maxCropW
            cropH = cropW / screenRatio
        } else {
            cropH = maxCropH
            cropW = cropH * screenRatio
        }
        val cropLeft = (totalW - cropW) / 2f
        val cropTop = (totalH - cropH) / 2f

        cropState.screenW = totalW
        cropState.screenH = totalH
        cropState.cropW = cropW
        cropState.cropH = cropH
        cropState.cropLeft = cropLeft
        cropState.cropTop = cropTop

        val bitmapW = sourceBitmap.width.toFloat()
        val bitmapH = sourceBitmap.height.toFloat()
        val baseScale = min(totalW / bitmapW, totalH / bitmapH)
        val displayW = bitmapW * baseScale
        val displayH = bitmapH * baseScale
        val initScale = WallpaperCropMath.minScaleForRotation(cropW, cropH, displayW, displayH, 0f)

        LaunchedEffect(sourceBitmap, totalW, totalH) {
            if (!cropState.initialized) {
                cropState.scale = initScale
                cropState.panX = 0f
                cropState.panY = 0f
                cropState.rotationDegrees = 0f
                cropState.initialized = true
            } else {
                if (cropState.restoredScreenW > 0f && cropState.restoredScreenH > 0f) {
                    cropState.panX *= totalW / cropState.restoredScreenW
                    cropState.panY *= totalH / cropState.restoredScreenH
                    cropState.restoredScreenW = 0f
                    cropState.restoredScreenH = 0f
                }
                val minScale = WallpaperCropMath.minScaleForRotation(
                    cropW, cropH, displayW, displayH, cropState.rotationDegrees,
                )
                cropState.constrain(displayW, displayH, minScale, cropW, cropH)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isSaving) {
                    if (isSaving) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            isTouching = event.changes.any { it.pressed }
                        }
                    }
                }
                .pointerInput(isSaving) {
                    if (isSaving) return@pointerInput
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        val anchoredPan = WallpaperCropMath.transformAroundCentroid(
                            cropState.panX, cropState.panY,
                            centroid.x, centroid.y, totalW / 2f, totalH / 2f,
                            pan.x, pan.y, zoom, rotation,
                        )
                        cropState.panX = anchoredPan.first
                        cropState.panY = anchoredPan.second
                        cropState.rotationDegrees += rotation
                        val minScale = WallpaperCropMath.minScaleForRotation(
                            cropW,
                            cropH,
                            displayW,
                            displayH,
                            cropState.rotationDegrees,
                        )
                        cropState.scale *= zoom
                        cropState.constrain(displayW, displayH, minScale, cropW, cropH)
                    }
                }
        ) {
            val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = cropState.scale
                        scaleY = cropState.scale
                        translationX = cropState.panX
                        translationY = cropState.panY
                        rotationZ = cropState.rotationDegrees
                    },
            )

            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                val maskAlpha = if (isTouching) 0.45f else 0.78f
                drawRect(Color.Black.copy(alpha = maskAlpha), topLeft = Offset.Zero, size = Size(size.width, cropTop))
                drawRect(
                    Color.Black.copy(alpha = maskAlpha),
                    topLeft = Offset(0f, cropTop + cropH),
                    size = Size(size.width, size.height - cropTop - cropH),
                )
                drawRect(Color.Black.copy(alpha = maskAlpha), topLeft = Offset(0f, cropTop), size = Size(cropLeft, cropH))
                drawRect(
                    Color.Black.copy(alpha = maskAlpha),
                    topLeft = Offset(cropLeft + cropW, cropTop),
                    size = Size(size.width - cropLeft - cropW, cropH),
                )
                drawRect(
                    Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(cropLeft, cropTop),
                    size = Size(cropW, cropH),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(enabled = !isSaving, onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        enabled = !isSaving,
                        onClick = {
                            cropState.scale = initScale
                            cropState.panX = 0f
                            cropState.panY = 0f
                            cropState.rotationDegrees = 0f
                        },
                    ) {
                        Text(stringResource(R.string.settings_custom_wallpaper_reset), color = Color.White)
                    }
                    OutlinedButton(
                        enabled = !isSaving,
                        onClick = {
                            cropState.rotationDegrees = (cropState.rotationDegrees + 90f) % 360f
                            val minScale = WallpaperCropMath.minScaleForRotation(
                                cropW,
                                cropH,
                                displayW,
                                displayH,
                                cropState.rotationDegrees,
                            )
                            cropState.constrain(displayW, displayH, minScale, cropW, cropH)
                        },
                    ) {
                        Text(stringResource(R.string.settings_custom_wallpaper_rotate), color = Color.White)
                    }
                    OutlinedButton(
                        enabled = !isSaving,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.settings_custom_wallpaper_zoom_out)
                        },
                        onClick = {
                            cropState.scale *= 0.8f
                            val minScale = WallpaperCropMath.minScaleForRotation(
                                cropW, cropH, displayW, displayH, cropState.rotationDegrees,
                            )
                            cropState.constrain(displayW, displayH, minScale, cropW, cropH)
                        },
                    ) { Text("-", color = Color.White) }
                    OutlinedButton(
                        enabled = !isSaving,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.settings_custom_wallpaper_zoom_in)
                        },
                        onClick = {
                            cropState.scale *= 1.25f
                            val minScale = WallpaperCropMath.minScaleForRotation(
                                cropW, cropH, displayW, displayH, cropState.rotationDegrees,
                            )
                            cropState.constrain(displayW, displayH, minScale, cropW, cropH)
                        },
                    ) { Text("+", color = Color.White) }
                }
                Button(
                    enabled = !isSaving,
                    onClick = {
                        if (!isSaving) coroutineScope.launch {
                            isSaving = true
                            try {
                                val scale = cropState.scale
                                val panX = cropState.panX
                                val panY = cropState.panY
                                val rotationDegrees = cropState.rotationDegrees
                                val bitmap = withContext(NonCancellable + Dispatchers.Default) {
                                    runCatching {
                                        cropState.getCroppedBitmap(
                                            source = sourceBitmap,
                                            targetWidth = targetWidth,
                                            targetHeight = targetHeight,
                                            scale = scale,
                                            panX = panX,
                                            panY = panY,
                                            rotationDegrees = rotationDegrees,
                                        )
                                    }.getOrNull()
                                }
                                if (bitmap != null) {
                                    onConfirm(bitmap)
                                } else {
                                    Toast.makeText(
                                        context,
                                        R.string.settings_custom_wallpaper_load_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.settings_custom_wallpaper_save))
                    }
                }
            }
        }
    }
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val maxPreviewSide = 2400
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxPreviewSide || bounds.outHeight / sampleSize > maxPreviewSide) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return@runCatching null
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
        }
    }
    if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
        decoded
    } else {
        try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } finally {
            decoded.recycle()
        }
    }
}.getOrNull()

@Composable
private fun CardOpacitySetting(
    contentColor: Color,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderValue by remember { mutableStateOf(value.toFloat()) }
    LaunchedEffect(value) { sliderValue = value.toFloat() }
    val current = sliderValue.roundToInt().coerceIn(
        AppSettingsManager.CARD_OPACITY_MIN,
        AppSettingsManager.CARD_OPACITY_MAX,
    )
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = MaaDesignTokens.Spacing.listItemVertical)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_card_opacity_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
                Text(
                    text = stringResource(R.string.settings_card_opacity_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.6f),
                )
            }
            Text("$current%", style = MaterialTheme.typography.bodyMedium, color = contentColor)
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChange(current) },
            valueRange = AppSettingsManager.CARD_OPACITY_MIN.toFloat()..AppSettingsManager.CARD_OPACITY_MAX.toFloat(),
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingClickItem(
    title: String,
    description: String = "",
    contentColor: Color,
    onClick: () -> Unit
) {
    SettingRow(
        title = title,
        description = description.ifEmpty { null },
        titleColor = contentColor,
        descriptionColor = contentColor.copy(alpha = 0.7f),
        onClick = onClick,
    )
}

/**
 * 字体大小（页面缩放）设置：整数 80~110，默认 100。
 * 松手后才提交全局缩放。滑块下方带实时预览框。
 */
@Composable
private fun FontSizeSetting(
    contentColor: Color,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    var sliderValue by remember { mutableStateOf(value.toFloat()) }
    LaunchedEffect(value) {
        sliderValue = value.toFloat()
    }
    val current = sliderValue.roundToInt().coerceIn(AppSettingsManager.FONT_SIZE_SCALE_MIN, AppSettingsManager.FONT_SIZE_SCALE_MAX)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = MaaDesignTokens.Spacing.listItemVertical)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_font_size_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )
                Text(
                    text = stringResource(R.string.settings_font_size_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
            Text(
                text = current.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                onValueChange(sliderValue.roundToInt().coerceIn(
                    AppSettingsManager.FONT_SIZE_SCALE_MIN,
                    AppSettingsManager.FONT_SIZE_SCALE_MAX
                ))
            },
            valueRange = AppSettingsManager.FONT_SIZE_SCALE_MIN.toFloat()..AppSettingsManager.FONT_SIZE_SCALE_MAX.toFloat(),
            steps = 0,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(AppSettingsManager.FONT_SIZE_SCALE_MIN, 90, 100, AppSettingsManager.FONT_SIZE_SCALE_MAX).forEach { kp ->
                Text(
                    text = kp.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.5f)
                )
            }
        }
        // 实时预览框：previewDensity 已被全局缩放（D0 * value/100），
        // 故按 current/value 还原到 D0 * current/100，避免与全局缩放叠加造成重复缩放。
        val previewDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = previewDensity.density * current / value.toFloat(),
                fontScale = previewDensity.fontScale
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaaDesignTokens.Spacing.sm),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = stringResource(R.string.settings_font_size_preview_text),
                    modifier = Modifier.padding(16.dp),
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    description: String? = null,
    contentColor: Color,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingRow(
        title = title,
        description = description,
        titleColor = contentColor,
        descriptionColor = contentColor.copy(alpha = 0.7f),
        enabled = enabled,
        trailing = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        },
    )
}

@Composable
private fun SettingInfoRow(
    label: String,
    value: String,
    contentColor: Color,
    onClick: (() -> Unit)? = null,
) {
    SettingRow(
        title = label,
        titleColor = contentColor,
        trailing = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = 0.7f)
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun SettingChannelItem(
    contentColor: Color,
    selectedChannel: UpdateChannel,
    onChannelSelected: (UpdateChannel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.rowTitleGap)) {
            Text(
                text = stringResource(R.string.settings_update_channel_title),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            Text(
                text = stringResource(R.string.settings_update_channel_desc),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UpdateChannel.entries.forEach { channel ->
                val channelName = stringResource(channel.resId)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = channel == selectedChannel,
                            onClick = { onChannelSelected(channel) },
                            role = Role.RadioButton
                        )
                ) {
                    RadioButton(
                        selected = channel == selectedChannel,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = channelName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingBackgroundResolutionItem(
    contentColor: Color,
    selectedPreference: DefaultDisplayConfig.ResolutionPreference,
    onPreferenceSelected: (DefaultDisplayConfig.ResolutionPreference) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.rowTitleGap)) {
            Text(
                text = stringResource(R.string.settings_background_resolution_title),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val options = listOf(
                DefaultDisplayConfig.ResolutionPreference.P720 to "720p",
                DefaultDisplayConfig.ResolutionPreference.P1080 to "1080p"
            )
            options.forEach { (pref, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = pref == selectedPreference,
                            onClick = { onPreferenceSelected(pref) },
                            role = Role.RadioButton
                        )
                ) {
                    RadioButton(
                        selected = pref == selectedPreference,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingLanguageItem(
    contentColor: Color,
    selectedLanguage: AppSettingsManager.AppLanguage,
    onLanguageSelected: (AppSettingsManager.AppLanguage) -> Unit
) {
    val effectiveSelectedLanguage = resolveSelectedLanguage(selectedLanguage)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val options = listOf(
                AppSettingsManager.AppLanguage.ZH to stringResource(R.string.settings_language_zh),
                AppSettingsManager.AppLanguage.EN to stringResource(R.string.settings_language_en)
            )
            options.forEach { (lang, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = lang == effectiveSelectedLanguage,
                            onClick = { onLanguageSelected(lang) },
                            role = Role.RadioButton
                        )
                ) {
                    RadioButton(
                        selected = lang == effectiveSelectedLanguage,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRemoteBackendItem(
    contentColor: Color,
    selectedBackend: RemoteBackend,
    onBackendSelected: (RemoteBackend) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.rowTitleGap)) {
            Text(
                text = stringResource(R.string.settings_startup_backend_title),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteBackend.entries.forEach { backend ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = backend == selectedBackend,
                            onClick = { onBackendSelected(backend) },
                            role = Role.RadioButton
                        )
                ) {
                    RadioButton(
                        selected = backend == selectedBackend,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = backend.display,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}


private data class ShizukuLaunchAppOption(
    val label: String,
    val packageName: String
)

private fun loadShizukuLaunchApps(context: Context): List<ShizukuLaunchAppOption> {
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    // 应用列表查询较慢，调用方应在 IO 线程执行。
    return packageManager.queryIntentActivities(launcherIntent, 0)
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager).toString()
                .takeIf { it.isNotBlank() }
                ?: packageName
            ShizukuLaunchAppOption(label = label, packageName = packageName)
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<ShizukuLaunchAppOption, String>(String.CASE_INSENSITIVE_ORDER) { it.label })
}
