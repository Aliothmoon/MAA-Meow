package com.aliothmoon.maameow.presentation.navigation

import android.widget.Toast
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
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.presentation.components.ResourceLoadingOverlay
import com.aliothmoon.maameow.presentation.view.background.BackgroundTaskView
import com.aliothmoon.maameow.presentation.view.home.HomeView
import com.aliothmoon.maameow.presentation.view.settings.ErrorLogView
import com.aliothmoon.maameow.presentation.view.settings.LogHistoryView
import com.aliothmoon.maameow.presentation.view.settings.SettingsView
import org.koin.compose.koinInject

@Composable
fun AppNavigation(
    appSettings: AppSettingsManager = koinInject(),
    resourceLoader: MaaResourceLoader = koinInject()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val context = LocalContext.current

    var isFullscreen by remember { mutableStateOf(false) }

    // 执行模式状态 - 用于底部导航拦截
    val runMode by appSettings.runMode.collectAsStateWithLifecycle()
    val resourceLoadState by resourceLoader.state.collectAsStateWithLifecycle()

    // 判断是否显示底部导航（只在主 Tab 页面显示）
    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.BACKGROUND_TASK) && !isFullscreen

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    AppBottomNavigation(
                        currentRoute = currentRoute ?: Routes.HOME,
                        onTabSelected = { tab ->
                            // 前台模式下，禁止切换到后台任务
                            if (tab.route == Routes.BACKGROUND_TASK && runMode == RunMode.FOREGROUND) {
                                Toast.makeText(
                                    context,
                                    "当前是前台模式，请先切换到后台模式",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@AppBottomNavigation
                            }
                            // 正常导航
                            navController.navigate(tab.route) {
                                // 避免重复添加到返回栈
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
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())
            ) {
                composable(
                    route = Routes.HOME,
                    enterTransition = {
                        when (initialState.destination.route) {
                            Routes.SETTINGS -> slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(350)
                            )

                            else -> fadeIn(animationSpec = tween(200))
                        }
                    },
                    exitTransition = {
                        when (targetState.destination.route) {
                            Routes.SETTINGS -> slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = tween(350)
                            )

                            else -> fadeOut(animationSpec = tween(200))
                        }
                    },
                    popEnterTransition = {
                        when (initialState.destination.route) {
                            Routes.SETTINGS -> slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = tween(350)
                            )

                            else -> fadeIn(animationSpec = tween(200))
                        }
                    },
                    popExitTransition = {
                        when (targetState.destination.route) {
                            Routes.SETTINGS -> slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(350)
                            )

                            else -> fadeOut(animationSpec = tween(200))
                        }
                    }
                ) {
                    HomeView(navController = navController)
                }

                composable(
                    route = Routes.BACKGROUND_TASK,
                    enterTransition = { fadeIn(animationSpec = tween(200)) },
                    exitTransition = { fadeOut(animationSpec = tween(200)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                    popExitTransition = { fadeOut(animationSpec = tween(200)) }
                ) {
                    BackgroundTaskView(onFullscreenChanged = { isFullscreen = it })
                }

                composable(
                    route = Routes.SETTINGS,
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(350)
                        )
                    },
                    exitTransition = {
                        when (targetState.destination.route) {
                            Routes.HOME -> slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(350)
                            )

                            Routes.LOG_HISTORY -> slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = tween(350)
                            )

                            Routes.ERROR_LOG -> slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = tween(350)
                            )

                            else -> null
                        }
                    },
                    popEnterTransition = {
                        // 从 LogHistory 返回时，Settings 从左侧滑入
                        slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = tween(350)
                        )
                    },
                    popExitTransition = {
                        // 返回 Home 时，Settings 向右滑出
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(350)
                        )
                    }
                ) {
                    SettingsView(navController = navController)
                }

                composable(
                    route = Routes.LOG_HISTORY,
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(350)
                        )
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(350)
                        )
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(350)
                        )
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(350)
                        )
                    }
                ) {
                    LogHistoryView(navController = navController)
                }

                composable(
                    route = Routes.ERROR_LOG,
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(350)
                        )
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(350)
                        )
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(350)
                        )
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(350)
                        )
                    }
                ) {
                    ErrorLogView(navController = navController)
                }
            }
        }

        ResourceLoadingOverlay(state = resourceLoadState)
    }
}

