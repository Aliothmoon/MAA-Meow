package com.aliothmoon.maameow.presentation.view.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.domain.models.UnlockStep
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.manager.ShizukuInstallHelper
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.ChangelogDialog
import com.aliothmoon.maameow.presentation.components.CollapsibleSection
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.ListItemDivider
import com.aliothmoon.maameow.presentation.components.LogExportController
import com.aliothmoon.maameow.presentation.components.ReInitializeConfirmDialog
import com.aliothmoon.maameow.presentation.components.ResourceInitDialog
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.AchievementEffect
import com.aliothmoon.maameow.presentation.viewmodel.AchievementEvent
import com.aliothmoon.maameow.presentation.viewmodel.AchievementViewModel
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.UiScale
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import com.aliothmoon.maameow.utils.i18n.resolve
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun SettingsView(
    navController: NavController,
    onViewAnnouncement: () -> Unit = {},
    onViewOnboarding: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
    achievementViewModel: AchievementViewModel = koinViewModel(),
    resourceInitService: ResourceInitService = koinInject(),
    achievementReporter: AchievementReporter = koinInject(),
) {
    val resourceInitState by resourceInitService.state.collectAsStateWithLifecycle()
    val showChangelog by viewModel.showChangelog.collectAsStateWithLifecycle()
    val currentChangelog by viewModel.currentChangelog.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val autoCheckUpdate by viewModel.autoCheckUpdate.collectAsStateWithLifecycle()
    val autoDownloadUpdate by viewModel.autoDownloadUpdate.collectAsStateWithLifecycle()
    val startupBackend by viewModel.startupBackend.collectAsStateWithLifecycle()
    val skipShizukuCheck by viewModel.skipShizukuCheck.collectAsStateWithLifecycle()
    val shizukuShortcutEnabled by viewModel.shizukuShortcutEnabled.collectAsStateWithLifecycle()
    val shizukuLaunchPackage by viewModel.shizukuLaunchPackage.collectAsStateWithLifecycle()
    val deployWithPause by viewModel.deployWithPause.collectAsStateWithLifecycle()
    val reportToPenguin by viewModel.reportToPenguin.collectAsStateWithLifecycle()
    val reportToYituliu by viewModel.reportToYituliu.collectAsStateWithLifecycle()
    val penguinId by viewModel.penguinId.collectAsStateWithLifecycle()
    val forceFullscreenOnVirtualDisplay by viewModel.forceFullscreenOnVirtualDisplay.collectAsStateWithLifecycle()
    val pipOnHome by viewModel.pipOnHome.collectAsStateWithLifecycle()
    val wakeUnlockType by viewModel.wakeUnlockType.collectAsStateWithLifecycle()
    val wakeCredential by viewModel.wakeCredential.collectAsStateWithLifecycle()
    val wakeTestState by viewModel.wakeTestState.collectAsStateWithLifecycle()
    val unlockGesture by viewModel.unlockGesture.collectAsStateWithLifecycle()
    val gestureRecordState by viewModel.gestureRecordState.collectAsStateWithLifecycle()
    val tasksOverrideEnabled by viewModel.tasksOverrideEnabled.collectAsStateWithLifecycle()
    val updateChannel by viewModel.updateChannel.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val useSystemMonetColor by viewModel.useSystemMonetColor.collectAsStateWithLifecycle()
    val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    val showAchievementSnackbar by viewModel.showAchievementSnackbar.collectAsStateWithLifecycle()
    val backgroundResolution by viewModel.backgroundResolution.collectAsStateWithLifecycle()
    val customBackgroundEnabled by viewModel.customBackgroundEnabled.collectAsStateWithLifecycle()
    val customBackgroundImageAlpha by viewModel.customBackgroundImageAlpha.collectAsStateWithLifecycle()
    val customBackgroundScrim by viewModel.customBackgroundScrim.collectAsStateWithLifecycle()
    val customBackgroundBlur by viewModel.customBackgroundBlur.collectAsStateWithLifecycle()
    val backgroundImage by viewModel.backgroundImage.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val showRestartDialog by viewModel.showRestartDialog.collectAsStateWithLifecycle()
    val achievementUiState by achievementViewModel.uiState.collectAsStateWithLifecycle()
    // 对齐 WPF：进入 Debug 弹 DrunkAndStaggering，再点退出弹 Hangover
    var pallasFlavorDialog by remember { mutableStateOf<PallasFlavorDialog?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(achievementViewModel) {
        achievementViewModel.effects.collect { effect ->
            when (effect) {
                AchievementEffect.PallasEnteredDebug ->
                    pallasFlavorDialog = PallasFlavorDialog.Drunk

                AchievementEffect.PallasExitedDebug ->
                    pallasFlavorDialog = PallasFlavorDialog.Hangover

                AchievementEffect.UnlockedAll -> Toast.makeText(
                    context,
                    R.string.achievement_debug_unlock_all_done,
                    Toast.LENGTH_SHORT,
                ).show()

                AchievementEffect.Cleared -> Toast.makeText(
                    context,
                    R.string.achievement_debug_clear_done,
                    Toast.LENGTH_SHORT,
                ).show()

                AchievementEffect.Unlocked -> Unit
            }
        }
    }

    wakeTestState?.let { state ->
        LaunchedEffect(state) {
            val done = state as? SettingsViewModel.WakeTestState.Done ?: return@LaunchedEffect
            Toast.makeText(
                context,
                done.result.message.resolve(context),
                Toast.LENGTH_LONG,
            ).show()
            viewModel.clearWakeTestResult()
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshGestureRecord() }

    gestureRecordState?.let { state ->
        val doneTemplate = stringResource(R.string.settings_wake_gesture_done)
        LaunchedEffect(state) {
            val message = when (state) {
                is SettingsViewModel.GestureRecordState.Done ->
                    doneTemplate.format(state.steps)

                is SettingsViewModel.GestureRecordState.Failed ->
                    state.result.message.resolve(context)

                else -> return@LaunchedEffect
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearGestureRecordState()
        }
    }

    pallasFlavorDialog?.let { flavor ->
        // 禁止点外部/返回立刻关掉：连点与弹窗同帧时容易穿透 dismiss
        val bodyRes = when (flavor) {
            PallasFlavorDialog.Drunk -> R.string.settings_pallas_drunk_hint
            PallasFlavorDialog.Hangover -> R.string.settings_pallas_hangover
        }
        AlertDialog(
            onDismissRequest = { /* 仅允许确认按钮关闭，避免点击穿透 */ },
            title = { Text(stringResource(R.string.settings_pallas_burping)) },
            text = { Text(stringResource(bodyRes)) },
            confirmButton = {
                TextButton(onClick = { pallasFlavorDialog = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        )
    }

    val backgroundCrop = rememberBackgroundCropController(viewModel)
    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(backgroundCrop::pick) }

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

    var showShizukuAppPicker by remember { mutableStateOf(false) }
    var shizukuAppPickerLoadKey by remember { mutableIntStateOf(0) }
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
    var showForceFullscreenConfirm by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    LogExportController(
        sheetVisible = showExportSheet,
        onSheetDismiss = { showExportSheet = false },
    )
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

    // 全屏裁剪弹窗：选图后先裁剪，确认保存为背景，取消则清理源图片缓存。
    val cropSourceBitmap = backgroundCrop.sourceBitmap
    if (backgroundCrop.sourcePath != null && cropSourceBitmap != null) {
        WallpaperCropFullScreen(
            sourceBitmap = cropSourceBitmap,
            cropState = backgroundCrop.cropState,
            onCancel = backgroundCrop::cancel,
            onConfirm = backgroundCrop::confirm,
        )
    }

    if (showChangelog) {
        ChangelogDialog(
            content = currentChangelog?.markdown
                ?: stringResource(R.string.settings_about_changelog_empty),
            title = stringResource(R.string.settings_about_changelog),
            onDismiss = { viewModel.onDismissChangelog() }
        )
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

    if (showForceFullscreenConfirm) {
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.dialog_enable_force_fullscreen_title),
            message = stringResource(R.string.dialog_enable_force_fullscreen_message),
            onConfirm = {
                showForceFullscreenConfirm = false
                viewModel.setForceFullscreenOnVirtualDisplay(true)
            },
            onDismissRequest = { showForceFullscreenConfirm = false },
            confirmText = stringResource(R.string.common_confirm),
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
                CollapsibleSection(
                    title = stringResource(R.string.settings_section_update),
                    sectionKey = "settings_section_update",
                ) {
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
            }

            // 日志
            item {
                CollapsibleSection(
                    title = stringResource(R.string.settings_section_log),
                    sectionKey = "settings_section_log",
                ) {
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
                        SettingClickItem(
                            title = stringResource(R.string.settings_log_export_title),
                            description = stringResource(R.string.settings_log_export_desc),
                            contentColor = contentColor
                        ) {
                            showExportSheet = true
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
            }

            // 显示设置
            item {
                CollapsibleSection(
                    title = stringResource(R.string.settings_section_display),
                    sectionKey = "settings_section_display",
                ) {
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
                            fontSizeScale = fontSizeScale,
                            onFontSizeScaleChanged = { viewModel.setFontSizeScale(it) }
                        )
                        ListItemDivider()
                        SettingCustomBackgroundSection(
                            contentColor = contentColor,
                            enabled = customBackgroundEnabled,
                            previewImage = backgroundImage,
                            imageAlpha = customBackgroundImageAlpha,
                            scrim = customBackgroundScrim,
                            blur = customBackgroundBlur,
                            onEnabledChange = { viewModel.setCustomBackgroundEnabled(it) },
                            onPickImage = {
                                pickBackgroundLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRemoveImage = { viewModel.removeBackgroundImage() },
                            onImageAlphaChange = { viewModel.setCustomBackgroundImageAlpha(it) },
                            onScrimChange = { viewModel.setCustomBackgroundScrim(it) },
                            onBlurChange = { viewModel.setCustomBackgroundBlur(it) },
                        )
                    }
                }
            }

            // 其他设置
            item {
                CollapsibleSection(
                    title = stringResource(R.string.settings_section_other),
                    sectionKey = "settings_section_other",
                ) {
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
                            MaaAnimatedVisibility(
                                visible = shizukuShortcutEnabled,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column {
                                    val shizukuLaunchAppName =
                                        ShizukuInstallHelper.getLaunchAppLabel(
                                            context,
                                            shizukuLaunchPackage
                                        )
                                    val shizukuLaunchAppDescription =
                                        if (shizukuLaunchPackage == OFFICIAL_SHIZUKU_PACKAGE) {
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
                            title = stringResource(R.string.settings_force_fullscreen_on_virtual_display),
                            description = stringResource(R.string.settings_force_fullscreen_on_virtual_display_desc),
                            contentColor = contentColor,
                            checked = forceFullscreenOnVirtualDisplay,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showForceFullscreenConfirm = true
                                } else {
                                    viewModel.setForceFullscreenOnVirtualDisplay(false)
                                }
                            }
                        )
                        ListItemDivider()
                        SettingSwitchItem(
                            title = stringResource(R.string.settings_pip_on_home),
                            description = stringResource(R.string.settings_pip_on_home_desc),
                            contentColor = contentColor,
                            checked = pipOnHome,
                            onCheckedChange = { viewModel.setPipOnHome(it) }
                        )
                        ListItemDivider()
                        SettingWakeUnlockTypeItem(
                            contentColor = contentColor,
                            selectedType = wakeUnlockType,
                            onTypeSelected = { viewModel.setWakeUnlockType(it) },
                        )
                        MaaAnimatedVisibility(
                            visible = wakeUnlockType == AppSettingsManager.WAKE_TYPE_PIN,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column {
                                ListItemDivider()
                                SettingWakePinSection(
                                    contentColor = contentColor,
                                    wakeCredential = wakeCredential,
                                    onCredentialChange = { viewModel.setWakeCredential(it) },
                                    onTest = { viewModel.runWakeTest() },
                                )
                            }
                        }
                        MaaAnimatedVisibility(
                            visible = wakeUnlockType == AppSettingsManager.WAKE_TYPE_GESTURE,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column {
                                ListItemDivider()
                                SettingWakeGestureSection(
                                    contentColor = contentColor,
                                    gesture = unlockGesture,
                                    recordState = gestureRecordState,
                                    onRecord = { viewModel.startGestureRecord() },
                                    onCancelRecord = { viewModel.cancelGestureRecord() },
                                    onClear = { viewModel.clearGesture() },
                                    onTest = { viewModel.runWakeTest() },
                                )
                            }
                        }
                    }
                }
            }

            // 任务设置：划火柴模式、MAA 任务覆盖
            item {
                CollapsibleSection(
                    title = stringResource(R.string.settings_section_task),
                    sectionKey = "settings_section_task",
                ) {
                    SettingsGroupCard {
                        SettingSwitchItem(
                            title = stringResource(R.string.settings_deploy_with_pause),
                            description = stringResource(R.string.settings_deploy_with_pause_desc),
                            contentColor = contentColor,
                            checked = deployWithPause,
                            onCheckedChange = { viewModel.setDeployWithPause(it) }
                        )
                        ListItemDivider()
                        SettingSwitchItem(
                            title = stringResource(R.string.settings_report_penguin),
                            description = stringResource(R.string.settings_report_penguin_desc),
                            contentColor = contentColor,
                            checked = reportToPenguin,
                            onCheckedChange = { viewModel.setReportToPenguin(it) }
                        )
                        ListItemDivider()
                        SettingSwitchItem(
                            title = stringResource(R.string.settings_report_yituliu),
                            description = stringResource(R.string.settings_report_yituliu_desc),
                            contentColor = contentColor,
                            checked = reportToYituliu,
                            onCheckedChange = { viewModel.setReportToYituliu(it) }
                        )
                        // ID 只归企鹅物流，一图流的掉落上报不带 ID
                        MaaAnimatedVisibility(
                            visible = reportToPenguin,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column {
                                ListItemDivider()
                                SettingPenguinIdField(
                                    penguinId = penguinId,
                                    onIdChange = { viewModel.setPenguinId(it) },
                                )
                            }
                        }
                        ListItemDivider()
                        SettingSwitchItem(
                            title = stringResource(R.string.settings_tasks_override_title),
                            description = stringResource(R.string.settings_tasks_override_desc),
                            contentColor = contentColor,
                            checked = tasksOverrideEnabled,
                            onCheckedChange = { viewModel.setTasksOverrideEnabled(it) }
                        )
                        MaaAnimatedVisibility(
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
            }

            // 数据管理
            item {
                CollapsibleSection(
                    title = stringResource(R.string.settings_section_data),
                    sectionKey = "settings_section_data",
                ) {
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
            }

            // 通知
            item {
                CollapsibleSection(
                    title = stringResource(R.string.settings_section_notification),
                    sectionKey = "settings_section_notification",
                ) {
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
            }

            // 成就（帕拉斯头像在分栏卡片内第一项）
            item {
                CollapsibleSection(
                    title = stringResource(R.string.settings_section_achievement),
                    sectionKey = "settings_section_achievement",
                ) {
                    SettingsGroupCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaaDesignTokens.Spacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
                        ) {
                            PallasMedal(
                                debugActive = achievementUiState.pallasDebugActive,
                                onClick = {
                                    achievementViewModel.onEvent(AchievementEvent.PallasAvatarClicked)
                                },
                            )
                            MaaAnimatedVisibility(
                                visible = achievementUiState.pallasDebugActive,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        MaaDesignTokens.Spacing.sm,
                                        Alignment.CenterHorizontally,
                                    ),
                                ) {
                                    Button(
                                        onClick = {
                                            achievementViewModel.onEvent(AchievementEvent.UnlockAll)
                                        },
                                    ) {
                                        Text(stringResource(R.string.achievement_debug_unlock_all))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            achievementViewModel.onEvent(AchievementEvent.ClearAllRecords)
                                        },
                                    ) {
                                        Text(stringResource(R.string.achievement_debug_clear_all))
                                    }
                                }
                            }
                        }
                        ListItemDivider()
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
            }

            // 关于
            item {
                CollapsibleSection(
                    title = stringResource(R.string.settings_section_about),
                    sectionKey = "settings_section_about",
                ) {
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
                            Misc.openUriSafely(context, "https://join.maameow.com/")
                        }
                        ListItemDivider()
                        SettingClickItem(
                            title = stringResource(R.string.settings_about_changelog),
                            contentColor = contentColor
                        ) {
                            viewModel.onShowChangelog()
                        }
                        ListItemDivider()
                        SettingClickItem(
                            title = stringResource(R.string.settings_about_announcement),
                            contentColor = contentColor
                        ) {
                            onViewAnnouncement()
                        }
                        ListItemDivider()
                        SettingClickItem(
                            title = stringResource(R.string.settings_about_onboarding),
                            description = stringResource(R.string.settings_about_onboarding_desc),
                            contentColor = contentColor
                        ) {
                            onViewOnboarding()
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
                                    Misc.openUriSafely(
                                        context,
                                        "https://github.com/Aliothmoon/MAA-Meow"
                                    )
                                }
                                .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
                            textAlign = TextAlign.Center
                        )
                    }
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
    fontSizeScale: Int,
    onFontSizeScaleChanged: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(
                    AppSettingsManager.ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                    AppSettingsManager.ThemeMode.WHITE to stringResource(R.string.settings_theme_white),
                    AppSettingsManager.ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                    AppSettingsManager.ThemeMode.PURE_DARK to stringResource(R.string.settings_theme_pure_dark),
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
                                role = Role.RadioButton,
                            ),
                    ) {
                        RadioButton(
                            selected = mode == selectedMode,
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ListItemDivider()
            SettingRow(
                title = stringResource(R.string.settings_monet_color_title),
                description = stringResource(R.string.settings_monet_color_desc),
                titleColor = contentColor,
                descriptionColor = contentColor.copy(alpha = 0.7f),
                trailing = {
                    Switch(
                        checked = useSystemMonetColor,
                        onCheckedChange = onMonetColorChanged,
                    )
                },
            )
        }
        ListItemDivider()
        FontSizeSetting(
            contentColor = contentColor,
            value = fontSizeScale,
            onValueChange = onFontSizeScaleChanged,
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

@Composable
private fun SettingWakeUnlockTypeItem(
    contentColor: Color,
    selectedType: String,
    onTypeSelected: (String) -> Unit,
) {
    // 三个选项字宽差得多，单选圆点排一行会左右不齐
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_wake_unlock_type),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
        val options = listOf(
            AppSettingsManager.WAKE_TYPE_SWIPE to
                    stringResource(R.string.settings_wake_unlock_type_swipe),
            AppSettingsManager.WAKE_TYPE_GESTURE to
                    stringResource(R.string.settings_wake_unlock_type_gesture),
            AppSettingsManager.WAKE_TYPE_PIN to
                    stringResource(R.string.settings_wake_unlock_type_pin),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (type, label) ->
                SegmentedButton(
                    selected = type == selectedType,
                    onClick = { onTypeSelected(type) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                        baseShape = RoundedCornerShape(4.dp),
                    ),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingWakeGestureSection(
    contentColor: Color,
    gesture: UnlockGesture?,
    recordState: SettingsViewModel.GestureRecordState?,
    onRecord: () -> Unit,
    onCancelRecord: () -> Unit,
    onClear: () -> Unit,
    onTest: () -> Unit,
) {
    val recording = recordState is SettingsViewModel.GestureRecordState.Preparing ||
            recordState is SettingsViewModel.GestureRecordState.Recording

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (gesture == null) {
            Text(
                text = stringResource(R.string.settings_wake_gesture_none),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
        } else {
            // 步骤只在需要核对时才看，默认收起
            CollapsibleSection(
                sectionKey = "settings_wake_gesture_steps",
                initiallyExpanded = false,
                title = {
                    Text(
                        text = stringResource(
                            R.string.settings_wake_gesture_summary,
                            gesture.steps.size,
                            gesture.screenWidth,
                            gesture.screenHeight,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                        modifier = Modifier.weight(1f),
                    )
                },
            ) {
                gesture.steps.forEachIndexed { index, step ->
                    GestureStepRow(index = index, step = step, contentColor = contentColor)
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_wake_gesture_hint),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.7f),
        )

        if (recording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(
                        if (recordState is SettingsViewModel.GestureRecordState.Preparing) {
                            R.string.settings_wake_gesture_preparing
                        } else {
                            R.string.settings_wake_gesture_waiting
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
            OutlinedButton(
                onClick = onCancelRecord,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.settings_wake_gesture_cancel))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRecord,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (gesture == null) {
                                R.string.settings_wake_gesture_record
                            } else {
                                R.string.settings_wake_gesture_rerecord
                            },
                        ),
                    )
                }
                if (gesture != null) {
                    OutlinedButton(
                        onClick = onClear,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.settings_wake_gesture_clear))
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_wake_gesture_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        if (gesture != null) {
            SettingRow(
                title = stringResource(R.string.settings_wake_test_button),
                description = stringResource(R.string.settings_wake_gesture_test_hint),
                titleColor = contentColor,
                descriptionColor = contentColor.copy(alpha = 0.7f),
                onClick = onTest,
            )
        }
    }
}

@Composable
private fun GestureStepRow(
    index: Int,
    step: UnlockStep,
    contentColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.6f),
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gestureStepText(step),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            if (step.delayBeforeMs > 0) {
                Text(
                    text = stringResource(
                        R.string.settings_wake_gesture_step_delay,
                        step.delayBeforeMs,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun gestureStepText(step: UnlockStep): String = when (step) {
    is UnlockStep.Tap ->
        stringResource(R.string.settings_wake_gesture_step_tap, step.x, step.y)

    is UnlockStep.LongPress ->
        stringResource(
            R.string.settings_wake_gesture_step_long_press,
            step.x,
            step.y,
            step.holdMs,
        )

    is UnlockStep.Swipe -> {
        val first = step.points.firstOrNull()
        val last = step.points.lastOrNull()
        stringResource(
            R.string.settings_wake_gesture_step_swipe,
            first?.x ?: 0,
            first?.y ?: 0,
            last?.x ?: 0,
            last?.y ?: 0,
            last?.tMs ?: 0,
        )
    }
}

@Composable
private fun SettingPenguinIdField(
    penguinId: String,
    onIdChange: (String) -> Unit,
) {
    var localId by rememberSaveable { mutableStateOf(penguinId) }
    LaunchedEffect(penguinId) {
        if (penguinId != localId) localId = penguinId
    }
    OutlinedTextField(
        value = localId,
        onValueChange = { raw ->
            localId = raw
            onIdChange(raw)
        },
        label = { Text(stringResource(R.string.settings_penguin_id)) },
        placeholder = { Text(stringResource(R.string.settings_penguin_id_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaaDesignTokens.Spacing.lg, vertical = 8.dp),
    )
}

@Composable
private fun SettingWakePinSection(
    contentColor: Color,
    wakeCredential: String,
    onCredentialChange: (String) -> Unit,
    onTest: () -> Unit,
) {
    var localPin by rememberSaveable { mutableStateOf(wakeCredential) }
    var pinVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(wakeCredential) {
        if (wakeCredential != localPin) localPin = wakeCredential
    }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = localPin,
            onValueChange = {
                val digits = it.filter { c -> c.isDigit() }
                    .take(AppSettingsManager.MAX_PIN_LENGTH)
                localPin = digits
                onCredentialChange(digits)
            },
            label = { Text(stringResource(R.string.settings_wake_credential)) },
            placeholder = { Text(stringResource(R.string.settings_wake_credential_hint)) },
            singleLine = true,
            visualTransformation = if (pinVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = {
                IconButton(onClick = { pinVisible = !pinVisible }) {
                    Icon(
                        imageVector = if (pinVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = stringResource(
                            if (pinVisible) {
                                R.string.settings_wake_credential_hide
                            } else {
                                R.string.settings_wake_credential_show
                            },
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.settings_wake_credential_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        SettingRow(
            title = stringResource(R.string.settings_wake_test_button),
            description = stringResource(R.string.settings_wake_test_hint),
            titleColor = contentColor,
            descriptionColor = contentColor.copy(alpha = 0.7f),
            onClick = onTest,
        )
    }
}

@Composable
private fun SettingCustomBackgroundSection(
    contentColor: Color,
    enabled: Boolean,
    previewImage: ImageBitmap?,
    imageAlpha: Int,
    scrim: Int,
    blur: Int,
    onEnabledChange: (Boolean) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onImageAlphaChange: (Int) -> Unit,
    onScrimChange: (Int) -> Unit,
    onBlurChange: (Int) -> Unit,
) {
    val hasImage = previewImage != null
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingSwitchItem(
            title = stringResource(R.string.settings_background_title),
            description = stringResource(R.string.settings_background_desc),
            contentColor = contentColor,
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )

        MaaAnimatedVisibility(
            visible = enabled,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(bottom = MaaDesignTokens.Spacing.listItemVertical),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (previewImage != null) {
                    Image(
                        bitmap = previewImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPickImage,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(
                                if (hasImage) R.string.settings_background_replace
                                else R.string.settings_background_pick
                            )
                        )
                    }
                    if (hasImage) {
                        OutlinedButton(
                            onClick = onRemoveImage,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.settings_background_remove))
                        }
                    }
                }
                if (hasImage) {
                    BackgroundPercentSlider(
                        label = stringResource(R.string.settings_background_image_alpha),
                        value = imageAlpha,
                        contentColor = contentColor,
                        onValueChange = onImageAlphaChange
                    )
                    BackgroundPercentSlider(
                        label = stringResource(R.string.settings_background_scrim),
                        value = scrim,
                        contentColor = contentColor,
                        onValueChange = onScrimChange
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BackgroundPercentSlider(
                            label = stringResource(R.string.settings_background_blur),
                            value = blur,
                            contentColor = contentColor,
                            onValueChange = onBlurChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundPercentSlider(
    label: String,
    value: Int,
    contentColor: Color,
    onValueChange: (Int) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { sliderValue = value.toFloat() }
    val current = sliderValue.roundToInt().coerceIn(0, 100)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$current%",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                onValueChange(sliderValue.roundToInt().coerceIn(0, 100))
            },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 页面缩放：自动（按屏幕推荐）或手动 80~110。
 * 拖动滑块即进入手动；可一键「使用推荐」回到自动。
 */
@Composable
private fun FontSizeSetting(
    contentColor: Color,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    val isAuto = AppSettingsManager.isFontSizeScaleAuto(value)
    val recommended = remember(configuration.smallestScreenWidthDp, baseDensity.fontScale) {
        UiScale.recommendedFontSizeScale(
            smallestWidthDp = configuration.smallestScreenWidthDp,
            fontScale = baseDensity.fontScale,
        )
    }
    val effective = AppSettingsManager.resolveFontSizeScale(
        stored = value,
        smallestWidthDp = configuration.smallestScreenWidthDp,
        fontScale = baseDensity.fontScale,
    )

    var sliderValue by remember {
        mutableFloatStateOf(
            (if (isAuto) recommended else value).toFloat()
        )
    }
    LaunchedEffect(value, recommended, isAuto) {
        sliderValue = (if (isAuto) recommended else value).toFloat()
    }
    val current = sliderValue.roundToInt()
        .coerceIn(AppSettingsManager.FONT_SIZE_SCALE_MIN, AppSettingsManager.FONT_SIZE_SCALE_MAX)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
    ) {
        // 标题行 + 说明：与 SettingRow / 其它设置项一致用 rowTitleGap
        Column(
            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.rowTitleGap),
        ) {
            // 数值只与标题同行，避免贴在多行说明文案右侧
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_font_size_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = if (isAuto) {
                        stringResource(R.string.settings_font_size_auto_value, effective)
                    } else {
                        current.toString()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    modifier = Modifier.padding(start = MaaDesignTokens.Spacing.md),
                )
            }
            Text(
                text = stringResource(R.string.settings_font_size_summary),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!isAuto) {
            OutlinedButton(
                onClick = { onValueChange(AppSettingsManager.FONT_SIZE_SCALE_AUTO) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_font_size_use_recommended),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    onValueChange(
                        sliderValue.roundToInt().coerceIn(
                            AppSettingsManager.FONT_SIZE_SCALE_MIN,
                            AppSettingsManager.FONT_SIZE_SCALE_MAX
                        )
                    )
                },
                valueRange = AppSettingsManager.FONT_SIZE_SCALE_MIN.toFloat()..AppSettingsManager.FONT_SIZE_SCALE_MAX.toFloat(),
                steps = 0,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    AppSettingsManager.FONT_SIZE_SCALE_MIN,
                    90,
                    100,
                    AppSettingsManager.FONT_SIZE_SCALE_MAX
                ).forEach { kp ->
                    Text(
                        text = kp.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.5f)
                    )
                }
            }
        }
        // 预览：全局 density 已是 D0*effective/100；滑到 current 时按比例还原
        val previewDensity = LocalDensity.current
        val previewFactor = if (effective == 0) {
            1f
        } else {
            current.toFloat() / effective.toFloat()
        }
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = previewDensity.density * previewFactor,
                fontScale = previewDensity.fontScale
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = stringResource(R.string.settings_font_size_preview_text),
                    modifier = Modifier.padding(MaaDesignTokens.Spacing.lg),
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.rowTitleGap)
        ) {
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.rowTitleGap)
        ) {
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.rowTitleGap)
        ) {
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


/** 帕拉斯彩蛋弹窗：进入 Debug = Drunk，再点退出 = Hangover（对齐 WPF）。 */
private enum class PallasFlavorDialog {
    Drunk,
    Hangover,
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
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
