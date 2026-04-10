package com.aliothmoon.maameow.presentation.view.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.service.LogExportService
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.components.ReInitializeConfirmDialog
import com.aliothmoon.maameow.presentation.components.ResourceInitDialog
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.utils.Misc
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.compose.ui.res.stringResource
import android.app.Activity
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.utils.LocaleHelper

@Composable
fun SettingsView(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
    resourceInitService: ResourceInitService = koinInject(),
    logExportService: LogExportService = koinInject()
) {
    val resourceInitState by resourceInitService.state.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val autoCheckUpdate by viewModel.autoCheckUpdate.collectAsStateWithLifecycle()
    val autoDownloadUpdate by viewModel.autoDownloadUpdate.collectAsStateWithLifecycle()
    val startupBackend by viewModel.startupBackend.collectAsStateWithLifecycle()
    val skipShizukuCheck by viewModel.skipShizukuCheck.collectAsStateWithLifecycle()
    val updateChannel by viewModel.updateChannel.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
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

    backupMessage?.let { msg ->
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearBackupMessage()
    }

    var showReInitConfirm by remember { mutableStateOf(false) }
    var showDebugModeConfirm by remember { mutableStateOf(false) }

    if (showRestartDialog) {
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.import_success_title),
            message = stringResource(R.string.import_success_msg),
            icon = Icons.Rounded.Build,
            confirmText = stringResource(R.string.restart_now),
            dismissText = stringResource(R.string.restart_later),
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
            title = stringResource(R.string.enable_debug_mode_title),
            message = stringResource(R.string.enable_debug_mode_msg),
            onConfirm = {
                showDebugModeConfirm = false
                viewModel.setDebugMode(true)
            },
            onDismissRequest = { showDebugModeConfirm = false },
            confirmText = stringResource(R.string.confirm_restart),
            dismissText = stringResource(R.string.cancel),
            icon = Icons.Rounded.Build
        )
    }

    if (resourceInitState is ResourceInitState.Extracting) {
        ResourceInitDialog(
            state = resourceInitState,
            onRetry = {}
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
                SectionHeader(stringResource(R.string.update_management))
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentColor = contentColor
                ) {
                    SettingClickItem(stringResource(R.string.reinit_resources), stringResource(R.string.reinit_resources_desc), contentColor) {
                        showReInitConfirm = true
                    }
                    SettingsDivider(contentColor)
                    SettingSwitchItem(
                        title = stringResource(R.string.auto_check_update_title),
                        description = stringResource(R.string.auto_check_update_desc),
                        contentColor = contentColor,
                        checked = autoCheckUpdate,
                        onCheckedChange = { viewModel.setAutoCheckUpdate(it) }
                    )
                    SettingsDivider(contentColor)
                    SettingSwitchItem(
                        title = stringResource(R.string.auto_download_update_title),
                        description = stringResource(R.string.auto_download_update_desc),
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
                SectionHeader(stringResource(R.string.logs_section))
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentColor = contentColor
                ) {
                    SettingClickItem(stringResource(R.string.log_history), stringResource(R.string.log_history_desc), contentColor) {
                        navController.navigate("log_history")
                    }
                    SettingsDivider(contentColor)
                    SettingClickItem(stringResource(R.string.error_log), stringResource(R.string.error_log_desc), contentColor) {
                        navController.navigate("error_log")
                    }
                    SettingsDivider(contentColor)
                    SettingClickItem(stringResource(R.string.export_log_zip), stringResource(R.string.export_log_zip_desc), contentColor) {
                        coroutineScope.launch {
                            val intent = logExportService.exportAllLogs()
                            if (intent != null) {
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_logs)))
                            }
                        }
                    }
                    SettingsDivider(contentColor)
                    SettingSwitchItem(
                        title = stringResource(R.string.debug_mode_title),
                        description = stringResource(R.string.debug_mode_desc),
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
                SectionHeader(stringResource(R.string.other_settings))
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentColor = contentColor
                ) {
                    SettingRemoteBackendItem(
                        contentColor = contentColor,
                        selectedBackend = startupBackend,
                        onBackendSelected = { viewModel.setStartupBackend(it) }
                    )
                    SettingsDivider(contentColor)
                    SettingThemeModeItem(
                        contentColor = contentColor,
                        selectedMode = themeMode,
                        onModeSelected = { viewModel.setThemeMode(it) }
                    )
                    SettingSwitchItem(
                        title = stringResource(R.string.skip_shizuku_check),
                        contentColor = contentColor,
                        checked = skipShizukuCheck,
                        enabled = startupBackend == RemoteBackend.SHIZUKU,
                        onCheckedChange = { viewModel.setSkipShizukuCheck(it) }
                    )
                }
            }

            // 语言
            item {
                SectionHeader(stringResource(R.string.language_section))
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentColor = contentColor
                ) {
                    SettingLanguageItem(
                        contentColor = contentColor,
                        selectedLanguage = appLanguage,
                        onLanguageSelected = { tag ->
                            LocaleHelper.setLocaleTag(context, tag)
                            coroutineScope.launch { viewModel.setAppLanguage(tag) }
                            (context as? Activity)?.recreate()
                        }
                    )
                }
            }

            // 数据管理
            item {
                SectionHeader(stringResource(R.string.data_management))
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentColor = contentColor
                ) {
                    SettingClickItem(stringResource(R.string.export_config), stringResource(R.string.export_config_desc), contentColor) {
                        exportLauncher.launch("maameow_config.json")
                    }
                    SettingsDivider(contentColor)
                    SettingClickItem(stringResource(R.string.import_config), stringResource(R.string.import_config_desc), contentColor) {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                }
            }

            // 关于
            item {
                SectionHeader(stringResource(R.string.about_section))
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentColor = contentColor
                ) {

                    SettingInfoRow(stringResource(R.string.version), BuildConfig.VERSION_NAME, contentColor)
                    SettingsDivider(contentColor)
                    SettingInfoRow(stringResource(R.string.developer), "Aliothmoon", contentColor)
                    SettingsDivider(contentColor)
                    SettingClickItem(
                        title = stringResource(R.string.qq_group_title),
                        description = stringResource(R.string.qq_group_desc),
                        contentColor = contentColor
                    ) {
                        Misc.openUriSafely(context, "https://qm.qq.com/q/j4CFbeDQXu")
                    }
                    SettingsDivider(contentColor)
                    Text(
                        text = stringResource(R.string.give_star),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Misc.openUriSafely(context, "https://github.com/Aliothmoon/MAA-Meow")
                            }
                            .padding(vertical = 8.dp),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaaDesignTokens.Spacing.listItemVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.theme_mode),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modes = listOf(
                AppSettingsManager.ThemeMode.WHITE to stringResource(R.string.theme_white),
                AppSettingsManager.ThemeMode.DARK to stringResource(R.string.theme_dark),
                AppSettingsManager.ThemeMode.PURE_DARK to stringResource(R.string.theme_pure_dark)
            )
            modes.forEach { (mode, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
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
private fun SettingClickItem(
    title: String,
    description: String,
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
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
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
    contentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
                text = stringResource(R.string.update_channel_section),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            Text(
                text = stringResource(R.string.update_channel_section_desc),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UpdateChannel.entries.forEach { channel ->
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
                        text = channel.displayName,
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
                text = stringResource(R.string.startup_mode_section),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            Text(
                text = stringResource(R.string.startup_mode_section_desc),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
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

@Composable
private fun SettingLanguageItem(
    contentColor: Color,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
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
                text = stringResource(R.string.language_section),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val languages = listOf(
                "" to stringResource(R.string.language_system),
                "zh" to stringResource(R.string.language_zh),
                "en" to stringResource(R.string.language_en)
            )
            languages.forEach { (tag, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = tag == selectedLanguage,
                            onClick = { onLanguageSelected(tag) },
                            role = Role.RadioButton
                        )
                ) {
                    RadioButton(selected = tag == selectedLanguage, onClick = null)
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
