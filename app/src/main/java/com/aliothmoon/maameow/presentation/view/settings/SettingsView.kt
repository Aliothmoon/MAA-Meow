package com.aliothmoon.maameow.presentation.view.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.CoreDataLocation
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.VirtualDisplayBackend
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.manager.ShizukuInstallHelper
import com.aliothmoon.maameow.presentation.StaySober
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.ChangelogDialog
import com.aliothmoon.maameow.presentation.components.InstalledAppPickerDialog
import com.aliothmoon.maameow.presentation.components.LocalSettingRowBleed
import com.aliothmoon.maameow.presentation.components.LogExportController
import com.aliothmoon.maameow.presentation.components.SettingInfoText
import com.aliothmoon.maameow.presentation.components.SettingInfoToggle
import com.aliothmoon.maameow.presentation.components.ReInitializeConfirmDialog
import com.aliothmoon.maameow.presentation.components.ResourceInitDialog
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.components.horizontalBleed
import com.aliothmoon.maameow.presentation.navigation.BottomNavTab
import com.aliothmoon.maameow.presentation.navigation.MainTabNavigator
import com.aliothmoon.maameow.presentation.onboarding.LocalOnboardingState
import com.aliothmoon.maameow.presentation.onboarding.OnboardingTarget
import com.aliothmoon.maameow.presentation.onboarding.onboardingTarget
import com.aliothmoon.maameow.presentation.search.ProvideSettingSearch
import com.aliothmoon.maameow.presentation.search.SettingLocation
import com.aliothmoon.maameow.presentation.search.SettingSearchEntry
import com.aliothmoon.maameow.presentation.search.SettingSearchField
import com.aliothmoon.maameow.presentation.search.SettingSearchNavigator
import com.aliothmoon.maameow.presentation.search.SettingSearchResults
import com.aliothmoon.maameow.presentation.search.SettingSearchTarget
import com.aliothmoon.maameow.presentation.search.SettingsSections
import com.aliothmoon.maameow.presentation.viewmodel.AchievementEffect
import com.aliothmoon.maameow.presentation.viewmodel.AchievementEvent
import com.aliothmoon.maameow.presentation.viewmodel.AchievementViewModel
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.UiScale
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import com.aliothmoon.maameow.utils.i18n.resolve
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.roundToInt

// 第 0 项是搜索框
private fun sectionItemIndex(sectionKey: String): Int = SettingsSections.ORDER.indexOf(sectionKey) + 1
private val ONBOARDING_TARGET_SECTIONS = mapOf(
    OnboardingTarget.RUNTIME_BACKEND to SettingsSections.RUNTIME,
    OnboardingTarget.ABOUT_HELP to SettingsSections.ABOUT,
)

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
    val coreDataLocation by viewModel.coreDataLocation.collectAsStateWithLifecycle()
    val pendingCoreDataLocation by viewModel.pendingCoreDataLocation.collectAsStateWithLifecycle()
    val showClearCoreDataDialog by viewModel.showClearCoreDataDialog.collectAsStateWithLifecycle()
    val skipShizukuCheck by viewModel.skipShizukuCheck.collectAsStateWithLifecycle()
    val shizukuShortcutEnabled by viewModel.shizukuShortcutEnabled.collectAsStateWithLifecycle()
    val shizukuLaunchPackage by viewModel.shizukuLaunchPackage.collectAsStateWithLifecycle()
    val liveUpdateEntryVisible by viewModel.liveUpdateEntryVisible.collectAsStateWithLifecycle()
    val deployWithPause by viewModel.deployWithPause.collectAsStateWithLifecycle()
    val telemetryEnabled by viewModel.telemetryEnabled.collectAsStateWithLifecycle()
    val reportToPenguin by viewModel.reportToPenguin.collectAsStateWithLifecycle()
    val penguinId by viewModel.penguinId.collectAsStateWithLifecycle()
    val forceFullscreenOnVirtualDisplay by viewModel.forceFullscreenOnVirtualDisplay.collectAsStateWithLifecycle()
    val pipOnHome by viewModel.pipOnHome.collectAsStateWithLifecycle()
    val tasksOverrideEnabled by viewModel.tasksOverrideEnabled.collectAsStateWithLifecycle()
    val updateChannel by viewModel.updateChannel.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val useSystemMonetColor by viewModel.useSystemMonetColor.collectAsStateWithLifecycle()
    val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    val showAchievementSnackbar by viewModel.showAchievementSnackbar.collectAsStateWithLifecycle()
    val backgroundResolution by viewModel.backgroundResolution.collectAsStateWithLifecycle()
    val virtualDisplayBackend by viewModel.virtualDisplayBackend.collectAsStateWithLifecycle()
    val coreIdle by viewModel.coreIdle.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val settingsMessage by viewModel.settingsMessage.collectAsStateWithLifecycle()
    val showRestartDialog by viewModel.showRestartDialog.collectAsStateWithLifecycle()
    val achievementUiState by achievementViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 首启引导：靶点所在分区先滚进视野并强制展开；pager 重建后 effect 重跑自动补滚
    val onboarding = LocalOnboardingState.current
    val settingsListState = rememberLazyListState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    // 只让列表读是否在搜，输入时不重组整页
    val isSearching by remember { derivedStateOf { searchQuery.isNotBlank() } }
    val searchNavigator: SettingSearchNavigator = koinInject()
    val mainTabNavigator: MainTabNavigator = koinInject()
    val focusManager = LocalFocusManager.current
    val pendingSearch by searchNavigator.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSearch) {
        val request = pendingSearch ?: return@LaunchedEffect
        // 锚点没出现过的请求会一直挂着，回到本页时清掉，免得又跳又展开
        if (!request.isFresh()) {
            searchNavigator.consume(request)
            return@LaunchedEffect
        }
        val location = request.entry.location as? SettingLocation.Section ?: return@LaunchedEffect
        settingsListState.scrollToItem(sectionItemIndex(location.sectionKey))
    }
    fun revealToken(sectionKey: String) = pendingSearch?.takeIf {
        it.isFresh() && (it.entry.location as? SettingLocation.Section)?.sectionKey == sectionKey
    }
    fun openSearchResult(entry: SettingSearchEntry) {
        focusManager.clearFocus()
        searchQuery = ""
        searchNavigator.request(entry)
        when (val location = entry.location) {
            is SettingLocation.Section -> Unit
            is SettingLocation.Page -> navController.navigate(location.route)
            SettingLocation.BackgroundActions -> mainTabNavigator.navigateTo(BottomNavTab.BACKGROUND)
        }
    }
    val reduceMotion = LocalReduceMotion.current
    val onboardingSection = onboarding?.takeIf { it.active }?.currentStep?.target
        ?.let { ONBOARDING_TARGET_SECTIONS[it] }
    LaunchedEffect(onboarding, settingsListState, reduceMotion) {
        if (onboarding == null) return@LaunchedEffect
        snapshotFlow { onboarding.takeIf { it.active }?.currentStep?.target }
            .collectLatest { target ->
                val section = ONBOARDING_TARGET_SECTIONS[target] ?: return@collectLatest
                if (reduceMotion) {
                    settingsListState.scrollToItem(sectionItemIndex(section))
                } else {
                    settingsListState.animateScrollToItem(sectionItemIndex(section))
                }
            }
    }

    LaunchedEffect(achievementViewModel) {
        achievementViewModel.effects.collect { effect ->
            when (effect) {
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

    settingsMessage?.let { msg ->
        Toast.makeText(context, msg.resolve(context), Toast.LENGTH_SHORT).show()
        viewModel.clearSettingsMessage()
    }

    var showReInitConfirm by remember { mutableStateOf(false) }
    var showDebugModeConfirm by remember { mutableStateOf(false) }
    var showForceFullscreenConfirm by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    LogExportController(
        sheetVisible = showExportSheet,
        onSheetDismiss = { showExportSheet = false },
    )
    pendingCoreDataLocation?.let { target ->
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.dialog_core_data_location_switch_title),
            message = stringResource(
                R.string.dialog_core_data_location_switch_message,
                stringResource(target.labelRes())
            ),
            icon = Icons.Rounded.Build,
            confirmText = stringResource(R.string.dialog_core_data_location_switch_confirm),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = { viewModel.confirmCoreDataLocationChange() },
            onDismissRequest = { viewModel.dismissCoreDataLocationChange() }
        )
    }
    if (showClearCoreDataDialog) {
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.settings_core_data_clear_title),
            message = stringResource(R.string.dialog_core_data_clear_message),
            icon = Icons.Rounded.Warning,
            confirmText = stringResource(R.string.common_delete),
            dismissText = stringResource(R.string.common_cancel),
            confirmColor = MaterialTheme.colorScheme.error,
            onConfirm = { viewModel.confirmClearCoreData() },
            onDismissRequest = { viewModel.dismissClearCoreData() }
        )
    }
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
                viewModel.reInitializeResources()
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
        InstalledAppPickerDialog(
            title = stringResource(R.string.settings_shizuku_launch_app_picker_title),
            onPick = {
                viewModel.setShizukuLaunchPackage(it.packageName)
                showShizukuAppPicker = false
            },
            onDismiss = { showShizukuAppPicker = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_title),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        val contentColor = MaterialTheme.colorScheme.onSurface

        ProvideSettingSearch(pendingSearch, searchNavigator) {
            LazyColumn(
                state = settingsListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    horizontal = MaaDesignTokens.Spacing.listHorizontal,
                    vertical = MaaDesignTokens.Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sectionGap)
            ) {
                item {
                    SettingSearchField(query = searchQuery, onQueryChange = { searchQuery = it })
                }
                if (isSearching) {
                    item {
                        SettingSearchResults(query = searchQuery, onClick = ::openSearchResult)
                    }
                    return@LazyColumn
                }

                // 更新管理
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_update),
                        sectionKey = SettingsSections.UPDATE,
                        revealToken = revealToken(SettingsSections.UPDATE),
                    ) {
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_auto_check_update_title) {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_auto_check_update_title),
                                    contentColor = contentColor,
                                    checked = autoCheckUpdate,
                                    icon = Icons.Rounded.Update,
                                    onCheckedChange = { viewModel.setAutoCheckUpdate(it) }
                                )
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_auto_download_update_title) {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_auto_download_update_title),
                                    contentColor = contentColor,
                                    checked = autoDownloadUpdate,
                                    enabled = autoCheckUpdate,
                                    icon = Icons.Rounded.Download,
                                    onCheckedChange = { viewModel.setAutoDownloadUpdate(it) }
                                )
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_update_channel_title) {
                                SettingChoiceItem(
                                    title = stringResource(R.string.settings_update_channel_title),
                                    icon = Icons.Rounded.Science,
                                    entries = UpdateChannel.entries,
                                    selected = updateChannel,
                                    label = { stringResource(it.resId) },
                                    onSelected = { viewModel.setUpdateChannel(it) },
                                )
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_reinit_resource_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_reinit_resource_title),
                                    info = stringResource(R.string.settings_reinit_resource_desc),
                                    icon = Icons.Rounded.RestartAlt,
                                    trailing = RowTrailing.NONE,
                                ) {
                                    showReInitConfirm = true
                                }
                            }
                        }
                    }
                }

                // 日志
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_log),
                        sectionKey = SettingsSections.LOG,
                        revealToken = revealToken(SettingsSections.LOG),
                    ) {
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_log_history_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_log_history_title),
                                    icon = Icons.Rounded.History,
                                ) {
                                    navController.navigate("log_history")
                                }
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_log_error_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_log_error_title),
                                    icon = Icons.Rounded.ErrorOutline,
                                ) {
                                    navController.navigate("error_log")
                                }
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_log_export_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_log_export_title),
                                    icon = Icons.Rounded.IosShare,
                                    trailing = RowTrailing.NONE,
                                ) {
                                    showExportSheet = true
                                }
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_debug_mode_title) {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_debug_mode_title),
                                    info = stringResource(R.string.settings_debug_mode_desc),
                                    contentColor = contentColor,
                                    checked = debugMode,
                                    icon = Icons.Rounded.BugReport,
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
                }

                // 显示设置
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_display),
                        sectionKey = SettingsSections.DISPLAY,
                        revealToken = revealToken(SettingsSections.DISPLAY),
                    ) {
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_language_title) {
                                val options = listOf(
                                    AppSettingsManager.AppLanguage.ZH,
                                    AppSettingsManager.AppLanguage.EN,
                                    AppSettingsManager.AppLanguage.KO,
                                )
                                SettingChoiceItem(
                                    title = stringResource(R.string.settings_language_title),
                                    icon = Icons.Rounded.Translate,
                                    entries = options,
                                    selected = resolveSelectedLanguage(language),
                                    label = { it.label() },
                                    onSelected = { viewModel.setLanguage(it) },
                                )
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_theme_title) {
                                ThemeModePicker(
                                    selected = themeMode,
                                    onSelected = { viewModel.setThemeMode(it) },
                                    icon = Icons.Rounded.Palette,
                                )
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SettingsSegment {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_monet_color_title),
                                    contentColor = contentColor,
                                    checked = useSystemMonetColor,
                                    icon = Icons.Rounded.ColorLens,
                                    onCheckedChange = { viewModel.setUseSystemMonetColor(it) }
                                )
                            }
                        }
                        SettingsSegment {
                            FontSizeSetting(
                                contentColor = contentColor,
                                value = fontSizeScale,
                                onValueChange = { viewModel.setFontSizeScale(it) },
                            )
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_background_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_background_title),
                                    icon = Icons.Rounded.Wallpaper,
                                ) {
                                    navController.navigate(Routes.WALLPAPER)
                                }
                            }
                        }
                    }
                }

                // 运行环境
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_runtime),
                        sectionKey = SettingsSections.RUNTIME,
                        forceExpanded = onboardingSection == SettingsSections.RUNTIME,
                        revealToken = revealToken(SettingsSections.RUNTIME),
                    ) {
                        SettingsSegment(
                            modifier = Modifier.onboardingTarget(OnboardingTarget.RUNTIME_BACKEND),
                        ) {
                            SettingSearchTarget(R.string.settings_startup_backend_title) {
                                SettingChoiceItem(
                                    title = stringResource(R.string.settings_startup_backend_title),
                                    icon = Icons.Rounded.AdminPanelSettings,
                                    entries = RemoteBackend.entries,
                                    selected = startupBackend,
                                    label = { it.display },
                                    onSelected = { viewModel.setStartupBackend(it) },
                                )
                            }
                        }
                        if (startupBackend == RemoteBackend.SHIZUKU) {
                            SettingsSegment {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_shizuku_launch_mode_title),
                                    info = stringResource(R.string.settings_shizuku_launch_mode_desc),
                                    contentColor = contentColor,
                                    checked = shizukuShortcutEnabled,
                                    icon = Icons.Rounded.RocketLaunch,
                                    onCheckedChange = { viewModel.setShizukuShortcutEnabled(it) }
                                )
                            }
                            MaaAnimatedVisibility(
                                visible = shizukuShortcutEnabled,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                SettingsDividedColumn {
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
                                    SettingsSegment {
                                        SettingClickItem(
                                            title = stringResource(R.string.settings_shizuku_launch_app_title),
                                            description = shizukuLaunchAppDescription,
                                            icon = Icons.Rounded.Apps,
                                        ) {
                                            showShizukuAppPicker = true
                                        }
                                    }
                                    SettingsSegment {
                                        SettingClickItem(
                                            title = stringResource(R.string.settings_shizuku_launch_app_reset_title),
                                            icon = Icons.Rounded.SettingsBackupRestore,
                                            trailing = RowTrailing.NONE,
                                        ) {
                                            viewModel.setShizukuLaunchPackage(OFFICIAL_SHIZUKU_PACKAGE)
                                        }
                                    }
                                }
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_skip_shizuku_check) {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_skip_shizuku_check),
                                    contentColor = contentColor,
                                    checked = skipShizukuCheck,
                                    enabled = startupBackend == RemoteBackend.SHIZUKU,
                                    icon = Icons.Rounded.FactCheck,
                                    onCheckedChange = { viewModel.setSkipShizukuCheck(it) }
                                )
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_core_data_location_title) {
                                SettingChoiceItem(
                                    title = stringResource(R.string.settings_core_data_location_title),
                                    icon = Icons.Rounded.FolderOpen,
                                    entries = CoreDataLocation.entries,
                                    selected = coreDataLocation,
                                    label = { stringResource(it.labelRes()) },
                                    onSelected = { viewModel.requestCoreDataLocationChange(it) },
                                )
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_core_data_clear_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_core_data_clear_title),
                                    icon = Icons.Rounded.DeleteSweep,
                                    trailing = RowTrailing.NONE,
                                ) {
                                    viewModel.requestClearCoreData()
                                }
                            }
                        }
                    }
                }

                // 后台运行
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_background_run),
                        sectionKey = SettingsSections.BACKGROUND_RUN,
                        revealToken = revealToken(SettingsSections.BACKGROUND_RUN),
                    ) {
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_background_resolution_title) {
                                SettingChoiceItem(
                                    title = stringResource(R.string.settings_background_resolution_title),
                                    icon = Icons.Rounded.HighQuality,
                                    entries = listOf(
                                        DefaultDisplayConfig.ResolutionPreference.P720,
                                        DefaultDisplayConfig.ResolutionPreference.P1080,
                                    ),
                                    selected = backgroundResolution,
                                    label = {
                                        if (it == DefaultDisplayConfig.ResolutionPreference.P720) "720p" else "1080p"
                                    },
                                    onSelected = { viewModel.setBackgroundResolution(it) },
                                )
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_force_fullscreen_on_virtual_display) {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_force_fullscreen_on_virtual_display),
                                    info = stringResource(R.string.settings_force_fullscreen_on_virtual_display_desc),
                                    contentColor = contentColor,
                                    checked = forceFullscreenOnVirtualDisplay,
                                    icon = Icons.Rounded.Fullscreen,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            showForceFullscreenConfirm = true
                                        } else {
                                            viewModel.setForceFullscreenOnVirtualDisplay(false)
                                        }
                                    }
                                )
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_pip_on_home) {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_pip_on_home),
                                    contentColor = contentColor,
                                    checked = pipOnHome,
                                    icon = Icons.Rounded.PictureInPicture,
                                    onCheckedChange = { viewModel.setPipOnHome(it) }
                                )
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            SettingsSegment {
                                SettingSearchTarget(R.string.settings_virtual_display_backend_title) {
                                    SettingChoiceItem(
                                        title = stringResource(R.string.settings_virtual_display_backend_title),
                                        info = stringResource(R.string.settings_virtual_display_backend_desc),
                                        icon = Icons.Rounded.DesktopWindows,
                                        entries = VirtualDisplayBackend.entries,
                                        selected = virtualDisplayBackend,
                                        label = { backend ->
                                            stringResource(
                                                if (backend == VirtualDisplayBackend.LEGACY) {
                                                    R.string.settings_virtual_display_backend_legacy
                                                } else {
                                                    R.string.settings_virtual_display_backend_vdm
                                                }
                                            )
                                        },
                                        onSelected = viewModel::setVirtualDisplayBackend,
                                        enabled = coreIdle,
                                    )
                                }
                            }
                        }
                    }
                }

                // 三方服务：数据上报与一图流 OpenAPI
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_third_party),
                        sectionKey = SettingsSections.THIRD_PARTY,
                        revealToken = revealToken(SettingsSections.THIRD_PARTY),
                    ) {
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_telemetry) {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_telemetry),
                                    contentColor = contentColor,
                                    checked = telemetryEnabled,
                                    icon = Icons.Rounded.Insights,
                                    onCheckedChange = { viewModel.setTelemetryEnabled(it) }
                                )
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_report_penguin) {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_report_penguin),
                                    info = stringResource(R.string.settings_report_penguin_desc),
                                    contentColor = contentColor,
                                    checked = reportToPenguin,
                                    icon = Icons.Rounded.CloudUpload,
                                    onCheckedChange = { viewModel.setReportToPenguin(it) }
                                )
                            }
                            // ID 只归企鹅物流，一图流的掉落上报不带 ID
                            MaaAnimatedVisibility(
                                visible = reportToPenguin,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                SettingPenguinIdField(
                                    penguinId = penguinId,
                                    onIdChange = { viewModel.setPenguinId(it) },
                                )
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_yituliu_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_yituliu_title),
                                    description = stringResource(R.string.settings_yituliu_desc),
                                    icon = Icons.Rounded.Leaderboard,
                                ) {
                                    navController.navigate(Routes.YITULIU)
                                }
                            }
                        }
                    }
                }

                // 任务设置：划火柴模式、MAA 任务覆盖、自定义游戏包名
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_task),
                        sectionKey = SettingsSections.TASK,
                        revealToken = revealToken(SettingsSections.TASK),
                    ) {
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_deploy_with_pause) {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_deploy_with_pause),
                                    info = stringResource(R.string.settings_deploy_with_pause_desc),
                                    contentColor = contentColor,
                                    checked = deployWithPause,
                                    icon = Icons.Rounded.PauseCircle,
                                    onCheckedChange = { viewModel.setDeployWithPause(it) }
                                )
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_tasks_override_title) {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_tasks_override_title),
                                    info = stringResource(R.string.settings_tasks_override_desc),
                                    contentColor = contentColor,
                                    checked = tasksOverrideEnabled,
                                    icon = Icons.Rounded.DataObject,
                                    onCheckedChange = { viewModel.setTasksOverrideEnabled(it) }
                                )
                            }
                        }
                        MaaAnimatedVisibility(
                            visible = tasksOverrideEnabled,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            SettingsSegment {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_tasks_override_edit_title),
                                    icon = Icons.Rounded.Edit,
                                ) {
                                    navController.navigate(Routes.TASK_OVERRIDE_EDITOR)
                                }
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_game_package_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_game_package_title),
                                    icon = Icons.Rounded.SportsEsports,
                                ) {
                                    navController.navigate(Routes.GAME_PACKAGE)
                                }
                            }
                        }
                    }
                }

                // 数据管理
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_data),
                        sectionKey = SettingsSections.DATA,
                        revealToken = revealToken(SettingsSections.DATA),
                    ) {
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_export_config_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_export_config_title),
                                    icon = Icons.Rounded.FileUpload,
                                    trailing = RowTrailing.NONE,
                                ) {
                                    exportLauncher.launch("maameow_config.json")
                                }
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_import_config_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_import_config_title),
                                    icon = Icons.Rounded.FileDownload,
                                    trailing = RowTrailing.NONE,
                                ) {
                                    importLauncher.launch(arrayOf("application/json"))
                                }
                            }
                        }
                    }
                }

                // 通知
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_notification),
                        sectionKey = SettingsSections.NOTIFICATION,
                        revealToken = revealToken(SettingsSections.NOTIFICATION),
                    ) {
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_notification_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_notification_title),
                                    icon = Icons.Rounded.Notifications,
                                ) {
                                    navController.navigate(Routes.NOTIFICATION)
                                }
                            }
                        }
                        if (liveUpdateEntryVisible) {
                            SettingsSegment {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_live_update_title),
                                    icon = Icons.Rounded.Bolt,
                                ) {
                                    navController.navigate(Routes.LIVE_UPDATE)
                                }
                            }
                        }
                    }
                }

                // 成就（帕拉斯头像在分栏卡片内第一项）
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_achievement),
                        sectionKey = SettingsSections.ACHIEVEMENT,
                        revealToken = revealToken(SettingsSections.ACHIEVEMENT),
                    ) {
                        SettingsSegment {
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
                                        StaySober {
                                            Button(
                                                onClick = {
                                                    achievementViewModel.onEvent(AchievementEvent.UnlockAll)
                                                },
                                                shape = MaterialTheme.shapes.small,
                                            ) {
                                                Text(stringResource(R.string.achievement_debug_unlock_all))
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    achievementViewModel.onEvent(AchievementEvent.ClearAllRecords)
                                                },
                                                shape = MaterialTheme.shapes.small,
                                            ) {
                                                Text(stringResource(R.string.achievement_debug_clear_all))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_achievement_title) {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_achievement_title),
                                    icon = Icons.Rounded.EmojiEvents,
                                ) {
                                    navController.navigate(Routes.ACHIEVEMENT)
                                }
                            }
                        }
                        SettingsSegment {
                            SettingSearchTarget(R.string.settings_achievement_snackbar_title) {
                                SettingSwitchItem(
                                    title = stringResource(R.string.settings_achievement_snackbar_title),
                                    contentColor = contentColor,
                                    checked = showAchievementSnackbar,
                                    icon = Icons.Rounded.Celebration,
                                    onCheckedChange = { viewModel.setShowAchievementSnackbar(it) }
                                )
                            }
                        }
                        if (BuildConfig.DEBUG) {
                            SettingsSegment {
                                SettingClickItem(
                                    title = stringResource(R.string.settings_achievement_debug_title),
                                    icon = Icons.Rounded.BugReport,
                                ) {
                                    navController.navigate(Routes.ACHIEVEMENT_DEBUG)
                                }
                            }
                        }
                    }
                }

                // 关于
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_about),
                        sectionKey = SettingsSections.ABOUT,
                        forceExpanded = onboardingSection == SettingsSections.ABOUT,
                        revealToken = revealToken(SettingsSections.ABOUT),
                    ) {
                        SettingsSegment {
                            SettingAboutHeader()
                        }
                        // 两行合为一个引导靶点
                        SettingsDividedColumn(
                            modifier = Modifier.onboardingTarget(OnboardingTarget.ABOUT_HELP),
                        ) {
                            SettingsSegment {
                                SettingSearchTarget(R.string.settings_about_faq_title) {
                                    SettingClickItem(
                                        title = stringResource(R.string.settings_about_faq_title),
                                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                                        trailing = RowTrailing.EXTERNAL,
                                    ) {
                                        Misc.openUriSafely(context, MaaApi.FAQ_URL)
                                    }
                                }
                            }
                            SettingsSegment {
                                SettingSearchTarget(R.string.settings_about_feedback_title) {
                                    SettingClickItem(
                                        title = stringResource(R.string.settings_about_feedback_title),
                                        icon = Icons.Rounded.Feedback,
                                        trailing = RowTrailing.EXTERNAL,
                                    ) {
                                        Misc.openUriSafely(context, MaaApi.FEEDBACK_URL)
                                    }
                                }
                            }
                        }
                        SettingsSegment {
                            SettingClickItem(
                                title = stringResource(R.string.settings_about_qq_group_title),
                                icon = Icons.Rounded.Forum,
                                trailing = RowTrailing.EXTERNAL,
                            ) {
                                achievementReporter.reportFeedbackGroupOpened()
                                Misc.openUriSafely(context, "https://join.maameow.com/")
                            }
                        }
                        SettingsSegment {
                            SettingClickItem(
                                title = stringResource(R.string.settings_about_changelog),
                                icon = Icons.Rounded.NewReleases,
                            ) {
                                viewModel.onShowChangelog()
                            }
                        }
                        SettingsSegment {
                            SettingClickItem(
                                title = stringResource(R.string.settings_about_announcement),
                                icon = Icons.Rounded.Campaign,
                            ) {
                                onViewAnnouncement()
                            }
                        }
                        SettingsSegment {
                            SettingClickItem(
                                title = stringResource(R.string.settings_about_onboarding),
                                icon = Icons.Rounded.School,
                            ) {
                                onViewOnboarding()
                            }
                        }
                        SettingsSegment {
                            Text(
                                text = stringResource(R.string.settings_about_star),
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalBleed(LocalSettingRowBleed.current)
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
}

@Composable
private fun SettingClickItem(
    title: String,
    description: String = "",
    info: String? = null,
    icon: ImageVector? = null,
    trailing: RowTrailing = RowTrailing.CHEVRON,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    SettingRow(
        title = title,
        description = description.ifEmpty { null },
        info = info,
        titleColor = contentColor,
        descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant,
        leading = icon?.let { { SettingIcon(it) } },
        trailing = if (trailing == RowTrailing.NONE) null else ({ RowTrailingIcon(trailing) }),
        onClick = onClick,
    )
}

@Composable
private fun SettingPenguinIdField(
    penguinId: String,
    onIdChange: (String) -> Unit,
) {
    var localId by rememberSaveable { mutableStateOf(penguinId) }
    var focused by remember { mutableStateOf(false) }
    // 见 SettingSecretField
    LaunchedEffect(penguinId, focused) {
        if (!focused && penguinId != localId) localId = penguinId
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
            .padding(horizontal = MaaDesignTokens.Spacing.lg, vertical = 8.dp)
            .onFocusChanged { focused = it.isFocused },
    )
}

@Composable
internal fun SettingSecretField(
    value: String,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
    transform: (String) -> String = { it },
) {
    var local by rememberSaveable { mutableStateOf(value) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // 每次按键各自落盘，输入期间回流的是上一拍的旧值，接了就会冲掉刚打的字；失焦后再对齐
    LaunchedEffect(value, focused) {
        if (!focused && value != local) local = value
    }
    OutlinedTextField(
        value = local,
        onValueChange = {
            val next = transform(it)
            local = next
            onValueChange(next)
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.settings_secret_hide else R.string.settings_secret_show,
                    ),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
    )
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
    var infoExpanded by rememberSaveable { mutableStateOf(false) }
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
                SettingIcon(Icons.Rounded.FormatSize)
                Spacer(modifier = Modifier.width(MaaDesignTokens.Spacing.lg))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_font_size_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                    )
                    SettingInfoToggle(infoExpanded) { infoExpanded = !infoExpanded }
                }
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
        SettingInfoText(
            text = stringResource(R.string.settings_font_size_summary),
            expanded = infoExpanded,
        )
    }
}

@Composable
internal fun SettingSwitchItem(
    title: String,
    description: String? = null,
    info: String? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    checked: Boolean,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingRow(
        title = title,
        description = description,
        info = info,
        titleColor = contentColor,
        descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant,
        enabled = enabled,
        leading = icon?.let { { SettingIcon(it, enabled) } },
        // 开关不占 48dp 触控区，改由整行点击
        trailing = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Switch(checked = checked, enabled = enabled, onCheckedChange = null)
            }
        },
        toggled = checked,
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
private fun AppSettingsManager.AppLanguage.label(): String = stringResource(
    when (this) {
        AppSettingsManager.AppLanguage.EN -> R.string.settings_language_en
        AppSettingsManager.AppLanguage.KO -> R.string.settings_language_ko
        else -> R.string.settings_language_zh
    }
)

@StringRes
private fun CoreDataLocation.labelRes(): Int = when (this) {
    CoreDataLocation.APP_DIR -> R.string.settings_core_data_location_app_dir
    CoreDataLocation.LOCAL_TMP -> R.string.settings_core_data_location_local_tmp
}

