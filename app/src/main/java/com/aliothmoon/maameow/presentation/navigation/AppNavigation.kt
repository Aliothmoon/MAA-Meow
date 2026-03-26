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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.service.ExternalNotificationService
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.presentation.components.ResourceLoadingOverlay
import com.aliothmoon.maameow.presentation.view.background.BackgroundTaskView
import com.aliothmoon.maameow.presentation.view.home.HomeView
import com.aliothmoon.maameow.presentation.view.notification.NotificationSettingsView
import com.aliothmoon.maameow.presentation.view.settings.ErrorLogView
import com.aliothmoon.maameow.presentation.view.settings.LogHistoryView
import com.aliothmoon.maameow.presentation.view.settings.SettingsView
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.schedule.ui.CountdownDialog
import com.aliothmoon.maameow.schedule.ui.ScheduleEditView
import com.aliothmoon.maameow.schedule.ui.ScheduleListView
import org.koin.compose.koinInject

@Composable
fun AppNavigation(
    backgroundTaskViewModel: BackgroundTaskViewModel,
    appSettings: AppSettingsManager = koinInject(),
    notificationService: ExternalNotificationService = koinInject(),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentNavRoute = navBackStackEntry?.destination?.route

    val context = LocalContext.current

    var isFullscreen by remember { mutableStateOf(false) }

    // 执行模式状态 - 用于底部导航拦截
    val runMode by appSettings.runMode.collectAsStateWithLifecycle()
    val pendingScheduledExecution by backgroundTaskViewModel.coordinator.pendingExecution.collectAsStateWithLifecycle()
    val scheduledCountdownState by backgroundTaskViewModel.coordinator.countdownState.collectAsStateWithLifecycle()

    // 定义哪些页面属于主 Tab
    val mainTabs = listOf(Routes.HOME, Routes.BACKGROUND_TASK, Routes.SCHEDULE, Routes.NOTIFICATION)
    
    // 判断是否处于主 Tab 页面
    val isOnMainTab = currentNavRoute in mainTabs || currentNavRoute == null

    // 判断是否显示底部导航
    val showBottomBar = !isFullscreen && isOnMainTab

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

    LaunchedEffect(notificationService) {
        notificationService.feedbackMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 主 Tab 切换动画定义 - 使用极短的渐变色来平滑过渡，防止重叠感
    val tabEnterTransition = fadeIn(animationSpec = tween(150))
    val tabExitTransition = fadeOut(animationSpec = tween(150))

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    AppBottomNavigation(
                        currentRoute = currentNavRoute ?: Routes.HOME,
                        onTabSelected = { tab ->
                            if (tab.route == currentNavRoute) return@AppBottomNavigation

                            if (tab.route == Routes.BACKGROUND_TASK && runMode == RunMode.FOREGROUND) {
                                Toast.makeText(
                                    context,
                                    "当前是前台模式，请先切换到后台模式",
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
                        SettingsView(navController = navController)
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
                }
            }
        }

        ResourceLoadingOverlay()

        // 全局定时任务倒计时弹窗
        val countdown = scheduledCountdownState
        if (countdown is CountdownState.Counting) {
            CountdownDialog(
                state = countdown,
                onCancel = { backgroundTaskViewModel.onScheduledCountdownCancel() },
                onStartNow = { backgroundTaskViewModel.onScheduledStartNow() },
            )
        }
    }
}
