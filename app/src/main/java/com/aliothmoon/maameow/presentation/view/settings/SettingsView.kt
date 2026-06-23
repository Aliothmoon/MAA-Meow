package com.aliothmoon.maameow.presentation.view.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.CircularProgressIndicator
import android.os.Build
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.ShizukuLaunchMode
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.LogExportService
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.manager.ShizukuInstallHelper
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.ReInitializeConfirmDialog
import com.aliothmoon.maameow.presentation.components.ResourceInitDialog
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import com.aliothmoon.maameow.utils.i18n.resolve
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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
    val shizukuLaunchMode by viewModel.shizukuLaunchMode.collectAsStateWithLifecycle()
    val shizukuLaunchPackage by viewModel.shizukuLaunchPackage.collectAsStateWithLifecycle()
    val deploymentWithPause by viewModel.deploymentWithPause.collectAsStateWithLifecycle()
    val forceFullscreenOnVirtualDisplay by viewModel.forceFullscreenOnVirtualDisplay.collectAsStateWithLifecycle()
    val allowForegroundScheduledTask by viewModel.allowForegroundScheduledTask.collectAsStateWithLifecycle()
    val tasksOverrideEnabled by viewModel.tasksOverrideEnabled.collectAsStateWithLifecycle()
    val updateChannel by viewModel.updateChannel.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val useSystemMonetColor by viewModel.useSystemMonetColor.collectAsStateWithLifecycle()
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
                title = stringResource(R.string.settings_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() }
            )
        }
    ) { paddingValues ->
        val contentColor = MaterialTheme.colorScheme.onSurface

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // 更新管理
            item {
                SectionHeader(stringResource(R.string.settings_section_update))
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentColor = contentColor,
                    contentPadding = PaddingValues(
                        horizontal = MaaDesignTokens.Card.innerPadding,
                        vertical = MaaDesignTokens.Spacing.listItemVertical
                    )
                ) {
                    SettingClickItem(
                        title = stringResource(R.string.settings_reinit_resource_title),
                        description = stringResource(R.string.settings_reinit_resource_desc),
                        contentColor = contentColor
                    ) {
                        showReInitConfirm = true
                    }
                    SettingsDivider(contentColor)
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_auto_check_update_title),
                        description = stringResource(R.string.settings_auto_check_update_desc),
                        contentColor = contentColor,
                        checked = autoCheckUpdate,
                        onCheckedChange = { viewModel.setAutoCheckUpdate(it) }
                    )
                    SettingsDivider(contentColor)
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_auto_download_update_title),
                        description = stringResource(R.string.settings_auto_download_update_desc),
                        contentColor = contentColor,
                        checked = autoDownloadUpdate,
                        enabled = autoCheckUpdate,
                        onCheckedChange = { viewModel.setAutoDownloadUpdate(it) }
                    )
                    SettingsDivider(contentColor)
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
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentColor = contentColor,
                    contentPadding = PaddingValues(
                        horizontal = MaaDesignTokens.Card.innerPadding,
                        vertical = MaaDesignTokens.Spacing.listItemVertical
                    )
                ) {
                    SettingClickItem(
                        title = stringResource(R.string.settings_log_history_title),
                        description = stringResource(R.string.settings_log_history_desc),
                        contentColor = contentColor
                    ) {
                        navController.navigate("log_history")
                    }
                    SettingsDivider(contentColor)
                    SettingClickItem(
                        title = stringResource(R.string.settings_log_error_title),
                        description = stringResource(R.string.settings_log_error_desc),
                        contentColor = contentColor
                    ) {
                        navController.navigate("error_log")
                    }
                    SettingsDivider(contentColor)
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
                    SettingsDivider(contentColor)
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

            // 其他设置
            item {
                SectionHeader(stringResource(R.string.settings_section_other))
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentColor = contentColor,
                    contentPadding = PaddingValues(
                        horizontal = MaaDesignTokens.Card.innerPadding,
                        vertical = MaaDesignTokens.Spacing.listItemVertical
                    )
                ) {
                    SettingRemoteBackendItem(
                        contentColor = contentColor,
                        selectedBackend = startupBackend,
                        onBackendSelected = { viewModel.setStartupBackend(it) }
                    )
                    SettingsDivider(contentColor)
                    if (startupBackend == RemoteBackend.SHIZUKU) {
                        SettingSwitchItem(
                            title = stringResource(R.string.settings_shizuku_launch_mode_title),
                            description = stringResource(R.string.settings_shizuku_launch_mode_desc),
                            contentColor = contentColor,
                            checked = shizukuLaunchMode != ShizukuLaunchMode.OFF,
                            onCheckedChange = { checked ->
                                viewModel.setShizukuLaunchMode(
                                    if (checked) ShizukuLaunchMode.CUSTOM else ShizukuLaunchMode.OFF
                                )
                            }
                        )
                        SettingsDivider(contentColor)
                        AnimatedVisibility(
                            visible = shizukuLaunchMode != ShizukuLaunchMode.OFF,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column {
                                val shizukuLaunchAppName = ShizukuInstallHelper.getLaunchAppLabel(
                                    context,
                                    shizukuLaunchPackage
                                )
                                val shizukuLaunchAppDescription = if (shizukuLaunchPackage.isBlank()) {
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
                                SettingsDivider(contentColor)
                                SettingClickItem(
                                    title = stringResource(R.string.settings_shizuku_launch_app_reset_title),
                                    description = stringResource(
                                        if (shizukuLaunchPackage.isBlank()) {
                                            R.string.settings_shizuku_launch_app_reset_default_desc
                                        } else {
                                            R.string.settings_shizuku_launch_app_reset_desc
                                        }
                                    ),
                                    contentColor = contentColor
                                ) {
                                    viewModel.setShizukuLaunchPackage("")
                                }
                                SettingsDivider(contentColor)
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingSwitchItem(
                            title = stringResource(R.string.settings_monet_color_title),
                            description = stringResource(R.string.settings_monet_color_desc),
                            contentColor = contentColor,
                            checked = useSystemMonetColor,
                            onCheckedChange = { viewModel.setUseSystemMonetColor(it) }
                        )
                        SettingsDivider(contentColor)
                    }
                    SettingClickItem(
                        title = stringResource(R.string.settings_achievement_title),
                        description = stringResource(R.string.settings_achievement_desc),
                        contentColor = contentColor
                    ) {
                        navController.navigate(Routes.ACHIEVEMENT)
                    }
                    if (BuildConfig.DEBUG) {
                        SettingsDivider(contentColor)
                        SettingClickItem(
                            title = stringResource(R.string.settings_achievement_debug_title),
                            description = stringResource(R.string.settings_achievement_debug_desc),
                            contentColor = contentColor
                        ) {
                            navController.navigate(Routes.ACHIEVEMENT_DEBUG)
                        }
                    }
                    SettingsDivider(contentColor)
                    SettingThemeModeItem(
                        contentColor = contentColor,
                        selectedMode = themeMode,
                        onModeSelected = { viewModel.setThemeMode(it) }
                    )
                    SettingsDivider(contentColor)
                    SettingLanguageItem(
                        contentColor = contentColor,
                        selectedLanguage = language,
                        onLanguageSelected = { viewModel.setLanguage(it) }
                    )
                    SettingsDivider(contentColor)
                    SettingBackgroundResolutionItem(
                        contentColor = contentColor,
                        selectedPreference = backgroundResolution,
                        onPreferenceSelected = { viewModel.setBackgroundResolution(it) }
                    )
                    SettingsDivider(contentColor)
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_skip_shizuku_check),
                        contentColor = contentColor,
                        checked = skipShizukuCheck,
                        enabled = startupBackend == RemoteBackend.SHIZUKU,
                        onCheckedChange = { viewModel.setSkipShizukuCheck(it) }
                    )
                    SettingsDivider(contentColor)
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_deployment_with_pause),
                        description = stringResource(R.string.settings_deployment_with_pause_tip),
                        contentColor = contentColor,
                        checked = deploymentWithPause,
                        onCheckedChange = { viewModel.setDeploymentWithPause(it) }
                    )
                    SettingsDivider(contentColor)
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_force_fullscreen_on_virtual_display),
                        contentColor = contentColor,
                        checked = forceFullscreenOnVirtualDisplay,
                        onCheckedChange = { viewModel.setForceFullscreenOnVirtualDisplay(it) }
                    )
                    SettingsDivider(contentColor)
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_allow_foreground_scheduled_task),
                        contentColor = contentColor,
                        checked = allowForegroundScheduledTask,
                        onCheckedChange = { viewModel.setAllowForegroundScheduledTask(it) }
                    )
                    SettingsDivider(contentColor)
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
                            SettingsDivider(contentColor)
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
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentColor = contentColor,
                    contentPadding = PaddingValues(
                        horizontal = MaaDesignTokens.Card.innerPadding,
                        vertical = MaaDesignTokens.Spacing.listItemVertical
                    )
                ) {
                    SettingClickItem(
                        title = stringResource(R.string.settings_export_config_title),
                        description = stringResource(R.string.settings_export_config_desc),
                        contentColor = contentColor
                    ) {
                        exportLauncher.launch("maameow_config.json")
                    }
                    SettingsDivider(contentColor)
                    SettingClickItem(
                        title = stringResource(R.string.settings_import_config_title),
                        description = stringResource(R.string.settings_import_config_desc),
                        contentColor = contentColor
                    ) {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                }
            }

            // 关于
            item {
                SectionHeader(stringResource(R.string.settings_section_about))
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentColor = contentColor,
                    contentPadding = PaddingValues(
                        horizontal = MaaDesignTokens.Card.innerPadding,
                        vertical = MaaDesignTokens.Spacing.listItemVertical
                    )
                ) {
                    SettingInfoRow(
                        label = stringResource(R.string.settings_about_version),
                        value = BuildConfig.VERSION_NAME,
                        contentColor = contentColor,
                    )
                    SettingsDivider(contentColor)
                    SettingInfoRow(
                        label = stringResource(R.string.settings_about_developer),
                        value = "Aliothmoon",
                        contentColor = contentColor
                    )
                    SettingsDivider(contentColor)
                    SettingClickItem(
                        title = stringResource(R.string.settings_about_qq_group_title),
                        description = stringResource(R.string.settings_about_qq_group_desc),
                        contentColor = contentColor
                    ) {
                        achievementReporter.reportFeedbackGroupOpened()
                        Misc.openUriSafely(context, "https://qm.qq.com/q/j4CFbeDQXu")
                    }
                    SettingsDivider(contentColor)
                    SettingClickItem(
                        title = stringResource(R.string.settings_about_announcement),
                        contentColor = contentColor
                    ) {
                        onViewAnnouncement()
                    }
                    SettingsDivider(contentColor)
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
private fun SettingThemeModeItem(
    contentColor: Color,
    selectedMode: AppSettingsManager.ThemeMode,
    onModeSelected: (AppSettingsManager.ThemeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_theme_title),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor
        )
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
    }
}

@Composable
private fun SettingClickItem(
    title: String,
    description: String = "",
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = if (description != null) Arrangement.spacedBy(4.dp) else Arrangement.Top
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = if (enabled) 1f else 0.6f)
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = if (enabled) 0.7f else 0.4f)
                )
            }

        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingInfoRow(
    label: String,
    value: String,
    contentColor: Color,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SettingsDivider(contentColor: Color) {
    HorizontalDivider(
        modifier = Modifier.padding(start = MaaDesignTokens.Separator.inset),
        thickness = MaaDesignTokens.Separator.thickness,
        color = contentColor.copy(alpha = 0.12f)
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = 32.dp,
            top = MaaDesignTokens.Spacing.lg,
            bottom = MaaDesignTokens.Spacing.xs
        )
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
