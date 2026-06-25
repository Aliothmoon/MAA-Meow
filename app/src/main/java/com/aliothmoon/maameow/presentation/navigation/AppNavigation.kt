package com.aliothmoon.maameow.presentation.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.announcement.AnnouncementConfig
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.achievement.achievementText
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.ExternalNotificationService
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.presentation.components.AnnouncementDialog
import com.aliothmoon.maameow.presentation.components.ResourceLoadingOverlay
import com.aliothmoon.maameow.presentation.view.notification.NotificationSettingsView
import com.aliothmoon.maameow.presentation.view.settings.AchievementDebugView
import com.aliothmoon.maameow.presentation.view.settings.AchievementView
import com.aliothmoon.maameow.presentation.view.settings.ErrorLogView
import com.aliothmoon.maameow.presentation.view.settings.LogHistoryView
import com.aliothmoon.maameow.presentation.view.settings.TaskOverrideEditorView
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.schedule.ui.CountdownDialog
import com.aliothmoon.maameow.schedule.ui.ScheduleEditView
import com.aliothmoon.maameow.schedule.ui.ScheduleTriggerLogView
import com.aliothmoon.maameow.theme.MaaAnimations
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AppNavigation(
    backgroundTaskViewModel: BackgroundTaskViewModel,
    appSettings: AppSettingsManager = koinInject(),
    achievementRepository: AchievementRepository = koinInject(),
    notificationService: ExternalNotificationService = koinInject(),
    overlayController: OverlayController = koinInject(),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentNavRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isFullscreen by remember { mutableStateOf(false) }
    var forceShowAnnouncement by remember { mutableStateOf(false) }
    var announcementDismissedOnce by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val runMode by appSettings.runMode.collectAsStateWithLifecycle()
    val announcementReadVersion by appSettings.announcementReadVersion.collectAsStateWithLifecycle()
    val showAchievementSnackbar by appSettings.showAchievementSnackbar.collectAsStateWithLifecycle()
    val language by appSettings.language.collectAsStateWithLifecycle()
    val scheduledCountdownState by backgroundTaskViewModel.coordinator.countdownState.collectAsStateWithLifecycle()

    // 判断是否处于主 Tab 页面（由 MainScreen HorizontalPager 处理）
    val isOnMainTab = currentNavRoute in listOf(
        Routes.HOME, Routes.BACKGROUND_TASK, Routes.SCHEDULE, Routes.SETTINGS
    ) || currentNavRoute == null

    LaunchedEffect(backgroundTaskViewModel) {
        backgroundTaskViewModel.coordinator.feedbackMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(backgroundTaskViewModel) {
        backgroundTaskViewModel.coordinator.countdownState.collect { state ->
            overlayController.updateCountdownState(state)
        }
    }
    LaunchedEffect(backgroundTaskViewModel) {
        overlayController.onCountdownClick = {
            backgroundTaskViewModel.onScheduledStartNow()
        }
    }
    LaunchedEffect(notificationService) {
        notificationService.feedbackMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(achievementRepository, showAchievementSnackbar) {
        if (showAchievementSnackbar) {
            achievementRepository.unlockEvents.collect { id ->
                val title = context.achievementText(id, "title")
                snackbarHostState.showSnackbar(
                    message = context.getString(
                        R.string.achievement_unlocked_message,
                        title,
                    ),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    // Shared axis transitions for sub-pages
    val forwardEnterTransition = MaaAnimations.sharedAxisForwardEnter()
    val forwardExitTransition = MaaAnimations.sharedAxisForwardExit()
    val popEnterTransition = MaaAnimations.sharedAxisPopEnter()
    val popExitTransition = MaaAnimations.sharedAxisPopExit()

    Box(modifier = Modifier.fillMaxSize()) {
        // MainScreen with HorizontalPager for smooth tab switching
        MainScreen(
            navController = navController,
            backgroundTaskViewModel = backgroundTaskViewModel,
            onFullscreenChanged = { isFullscreen = it },
            onViewAnnouncement = { forceShowAnnouncement = true },
            visible = isOnMainTab,
        )

        // NavHost for sub-pages only (notifications, achievement, logs, etc.)
        // Tab switching is handled entirely by MainScreen's HorizontalPager.
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
        ) {
            // Main tab routes - MainScreen handles rendering via HorizontalPager
            composable(route = Routes.HOME) {}
            composable(route = Routes.BACKGROUND_TASK) {}
            composable(route = Routes.SCHEDULE) {}
            composable(route = Routes.SETTINGS) {}

            // ── Sub-pages with forward navigation transitions ──
            composable(
                route = Routes.NOTIFICATION,
                enterTransition = { forwardEnterTransition },
                exitTransition = { forwardExitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition }
            ) {
                NotificationSettingsView(navController = navController)
            }
            composable(
                route = Routes.ACHIEVEMENT,
                enterTransition = { forwardEnterTransition },
                exitTransition = { forwardExitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition }
            ) {
                AchievementView(
                    navController = navController,
                )
            }
            composable(
                route = Routes.ACHIEVEMENT_DEBUG,
                enterTransition = { forwardEnterTransition },
                exitTransition = { forwardExitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition }
            ) {
                AchievementDebugView(navController = navController)
            }
            composable(
                route = Routes.LOG_HISTORY,
                enterTransition = { forwardEnterTransition },
                exitTransition = { forwardExitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition }
            ) {
                LogHistoryView(navController = navController)
            }
            composable(
                route = Routes.ERROR_LOG,
                enterTransition = { forwardEnterTransition },
                exitTransition = { forwardExitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition }
            ) {
                ErrorLogView(navController = navController)
            }
            composable(
                route = Routes.SCHEDULE_EDIT,
                enterTransition = { forwardEnterTransition },
                exitTransition = { forwardExitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition }
            ) { backStackEntry ->
                val strategyId = backStackEntry.arguments?.getString("strategyId")
                    .let { if (it == "new") null else it }
                ScheduleEditView(navController = navController, strategyId = strategyId)
            }
            composable(
                route = Routes.SCHEDULE_TRIGGER_LOG,
                enterTransition = { forwardEnterTransition },
                exitTransition = { forwardExitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition }
            ) {
                ScheduleTriggerLogView(navController = navController)
            }
            composable(
                route = Routes.TASK_OVERRIDE_EDITOR,
                enterTransition = { forwardEnterTransition },
                exitTransition = { forwardExitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition }
            ) {
                TaskOverrideEditorView(navController = navController)
            }
        }
        ResourceLoadingOverlay()
        // SnackbarHost overlaid on top
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        // 全局定时任务倒计时弹窗（前台所有控制模式均不弹出对话框，静默处理）
        val countdown = scheduledCountdownState
        val hideCountdownDialog = runMode == RunMode.FOREGROUND
        if (countdown is CountdownState.Counting && !hideCountdownDialog) {
            CountdownDialog(
                state = countdown,
                onCancel = { backgroundTaskViewModel.onScheduledCountdownCancel() },
                onStartNow = { backgroundTaskViewModel.onScheduledStartNow() },
            )
        }
        // 长期公告弹窗：每次公告版本变更后首次启动自动弹出，或从设置中手动打开
        val needsToShow = announcementReadVersion != AnnouncementConfig.CURRENT_VERSION
        val showAnnouncement = forceShowAnnouncement || (needsToShow && !announcementDismissedOnce)
        val announcementMarkdown = remember(showAnnouncement, language) {
            if (showAnnouncement) {
                AnnouncementConfig.loadContent(context, language)
            } else {
                null
            }
        }
        if (announcementMarkdown != null) {
            AnnouncementDialog(
                imageAssetPath = remember(language) { AnnouncementConfig.imageAssetPath(language) },
                markdown = announcementMarkdown,
                onDismiss = { dontShowAgain ->
                    forceShowAnnouncement = false
                    if (dontShowAgain) {
                        coroutineScope.launch {
                            appSettings.setAnnouncementReadVersion(AnnouncementConfig.CURRENT_VERSION)
                        }
                    } else {
                        announcementDismissedOnce = true
                    }
                },
            )
        }
    }
}