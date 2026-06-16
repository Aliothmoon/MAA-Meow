package com.aliothmoon.maameow.presentation.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.announcement.AnnouncementConfig
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.achievement.AchievementTextFormatter
import com.aliothmoon.maameow.data.achievement.getAchievementPlaceholder
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.ExternalNotificationService
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.presentation.components.AnnouncementDialog
import com.aliothmoon.maameow.presentation.components.ResourceLoadingOverlay
import com.aliothmoon.maameow.presentation.view.background.BackgroundTaskView
import com.aliothmoon.maameow.presentation.view.home.HomeView
import com.aliothmoon.maameow.presentation.view.notification.NotificationSettingsView
import com.aliothmoon.maameow.presentation.view.settings.AchievementDebugView
import com.aliothmoon.maameow.presentation.view.settings.AchievementView
import com.aliothmoon.maameow.presentation.view.settings.ErrorLogView
import com.aliothmoon.maameow.presentation.view.settings.LogHistoryView
import com.aliothmoon.maameow.presentation.view.settings.SettingsView
import com.aliothmoon.maameow.presentation.view.settings.TaskOverrideEditorView
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.schedule.ui.CountdownDialog
import com.aliothmoon.maameow.schedule.ui.ScheduleEditView
import com.aliothmoon.maameow.schedule.ui.ScheduleListView
import com.aliothmoon.maameow.schedule.ui.ScheduleTriggerLogView
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

    // 执行模式状态 - 用于底部导航拦截
    val runMode by appSettings.runMode.collectAsStateWithLifecycle()
    val announcementReadVersion by appSettings.announcementReadVersion.collectAsStateWithLifecycle()
    val language by appSettings.language.collectAsStateWithLifecycle()
    val overlayControlMode by appSettings.overlayControlMode.collectAsStateWithLifecycle()
    val pendingScheduledExecution by backgroundTaskViewModel.coordinator.pendingExecution.collectAsStateWithLifecycle()
    val scheduledCountdownState by backgroundTaskViewModel.coordinator.countdownState.collectAsStateWithLifecycle()

    // 定义哪些页面属于主 Tab
    val mainTabs = listOf(Routes.HOME, Routes.BACKGROUND_TASK, Routes.SCHEDULE, Routes.NOTIFICATION)
    
    // 判断是否处于主 Tab 页面
    val isOnMainTab = currentNavRoute in mainTabs || currentNavRoute == null

    // 判断是否显示底部导航
    val showBottomBar = !isFullscreen && isOnMainTab
    val switchBackgroundModeMessage = stringResource(R.string.navigation_toast_switch_background_mode)

    LaunchedEffect(pendingScheduledExecution?.requestId) {
        if (pendingScheduledExecution != null && currentNavRoute != Routes.BACKGROUND_TASK) {
            navController.navigate(Routes.BACKGROUND_TASK) {
                popUpTo(Routes.HOME) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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

    LaunchedEffect(achievementRepository, language) {
        achievementRepository.unlockEvents.collect { achievement ->
            val title = AchievementTextFormatter.formatPlaceholders(
                achievement.definition.title.resolve(language.tag),
            ) { key -> context.getAchievementPlaceholder(key) }
            snackbarHostState.showSnackbar(
                message = context.getString(
                    R.string.achievement_unlocked_message,
                    title,
                ),
                duration = SnackbarDuration.Short,
            )
        }
    }

    // 主 Tab 切换动画定义 - 使用极短的渐变色来平滑过渡，防止重叠感
    val tabEnterTransition = fadeIn(animationSpec = tween(150))
    val tabExitTransition = fadeOut(animationSpec = tween(150))

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomBar) {
                    AppBottomNavigation(
                        currentRoute = currentNavRoute ?: Routes.HOME,
                        onTabSelected = { tab ->
                            if (tab.route == currentNavRoute) return@AppBottomNavigation

                            if (tab.route == Routes.BACKGROUND_TASK && runMode == RunMode.FOREGROUND) {
                                Toast.makeText(
                                    context,
                                    switchBackgroundModeMessage,
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@AppBottomNavigation
                            }

                            navController.navigate(tab.route) {
                                popUpTo(Routes.HOME) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding())
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                ) {
                    composable(
                        route = Routes.HOME,
                        enterTransition = { tabEnterTransition },
                        exitTransition = { tabExitTransition },
                        popEnterTransition = { tabEnterTransition },
                        popExitTransition = { tabExitTransition }
                    ) {
                        HomeView(navController = navController)
                    }

                    composable(
                        route = Routes.BACKGROUND_TASK,
                        enterTransition = { tabEnterTransition },
                        exitTransition = { tabExitTransition },
                        popEnterTransition = { tabEnterTransition },
                        popExitTransition = { tabExitTransition }
                    ) {
                        BackHandler { navController.popBackStack() }
                        BackgroundTaskView(
                            onFullscreenChanged = { isFullscreen = it },
                            viewModel = backgroundTaskViewModel,
                        )
                    }

                    composable(
                        route = Routes.SCHEDULE,
                        enterTransition = { tabEnterTransition },
                        exitTransition = { tabExitTransition },
                        popEnterTransition = { tabEnterTransition },
                        popExitTransition = { tabExitTransition }
                    ) {
                        BackHandler { navController.popBackStack() }
                        ScheduleListView(navController = navController)
                    }

                    composable(
                        route = Routes.NOTIFICATION,
                        enterTransition = { tabEnterTransition },
                        exitTransition = { tabExitTransition },
                        popEnterTransition = { tabEnterTransition },
                        popExitTransition = { tabExitTransition }
                    ) {
                        BackHandler { navController.popBackStack() }
                        NotificationSettingsView()
                    }

                    composable(
                        route = Routes.SETTINGS,
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(350))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(350))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        }
                    ) {
                        SettingsView(
                            navController = navController,
                            onViewAnnouncement = { forceShowAnnouncement = true },
                        )
                    }

                    composable(
                        route = Routes.ACHIEVEMENT,
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(350))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(350))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        }
                    ) {
                        AchievementView(navController = navController)
                    }

                    composable(
                        route = Routes.ACHIEVEMENT_DEBUG,
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(350))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(350))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        }
                    ) {
                        AchievementDebugView(navController = navController)
                    }

                    composable(
                        route = Routes.LOG_HISTORY,
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(350))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(350))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        }
                    ) {
                        LogHistoryView(navController = navController)
                    }

                    composable(
                        route = Routes.ERROR_LOG,
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(350))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(350))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        }
                    ) {
                        ErrorLogView(navController = navController)
                    }

                    composable(
                        route = Routes.SCHEDULE_EDIT,
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(350))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(350))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        }
                    ) { backStackEntry ->
                        val strategyId = backStackEntry.arguments?.getString("strategyId")
                            .let { if (it == "new") null else it }
                        ScheduleEditView(navController = navController, strategyId = strategyId)
                    }

                    composable(
                        route = Routes.SCHEDULE_TRIGGER_LOG,
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(350))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(350))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        }
                    ) {
                        ScheduleTriggerLogView(navController = navController)
                    }

                    composable(
                        route = Routes.TASK_OVERRIDE_EDITOR,
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(350))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(350))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350))
                        }
                    ) {
                        TaskOverrideEditorView(navController = navController)
                    }
                }
            }
        }

        ResourceLoadingOverlay()

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
