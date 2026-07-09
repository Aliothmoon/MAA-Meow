package com.aliothmoon.maameow.presentation.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aliothmoon.maameow.announcement.AnnouncementConfig
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.ExternalNotificationService
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.presentation.LocalToaster
import com.aliothmoon.maameow.theme.WallpaperColorScheme
import com.aliothmoon.maameow.utils.BitmapUtils
import com.aliothmoon.maameow.presentation.components.AnnouncementDialog
import com.aliothmoon.maameow.presentation.components.ResourceLoadingOverlay
import com.aliothmoon.maameow.presentation.state.UiEffect
import com.aliothmoon.maameow.presentation.view.notification.NotificationSettingsView
import com.aliothmoon.maameow.presentation.view.settings.AchievementDebugView
import com.aliothmoon.maameow.presentation.view.settings.AchievementView
import com.aliothmoon.maameow.presentation.view.settings.ErrorLogView
import com.aliothmoon.maameow.presentation.view.settings.LogHistoryView
import com.aliothmoon.maameow.presentation.view.settings.TaskOverrideEditorView
import com.aliothmoon.maameow.presentation.view.settings.WallpaperSettingsView
import com.aliothmoon.maameow.presentation.viewmodel.AppEventsViewModel
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.schedule.ui.CountdownDialog
import com.aliothmoon.maameow.schedule.ui.ScheduleEditView
import com.aliothmoon.maameow.schedule.ui.ScheduleTriggerLogView
import com.aliothmoon.maameow.theme.MaaAnimations
import com.aliothmoon.maameow.utils.i18n.resolve
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** 主 Tab 路由集合（与 [BottomNavTab.all] 单一真源），用于判断是否处于主界面。 */
private val MAIN_TAB_ROUTES: Set<String> = BottomNavTab.all.mapTo(HashSet()) { it.route }

@Composable
fun AppNavigation(
    backgroundTaskViewModel: BackgroundTaskViewModel,
    appSettings: AppSettingsManager = koinInject(),
    notificationService: ExternalNotificationService = koinInject(),
    overlayController: OverlayController = koinInject(),
    appEventsViewModel: AppEventsViewModel = koinViewModel(),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentNavRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val toaster = rememberToasterState()
    val isFullscreen by remember(backgroundTaskViewModel) {
        backgroundTaskViewModel.state
            .map { it.isFullscreenMonitor }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    var forceShowAnnouncement by remember { mutableStateOf(false) }
    var announcementDismissedOnce by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val runMode by appSettings.runMode.collectAsStateWithLifecycle()
    val announcementReadVersion by appSettings.announcementReadVersion.collectAsStateWithLifecycle()
    val language by appSettings.language.collectAsStateWithLifecycle()
    val scheduledCountdownState by backgroundTaskViewModel.coordinator.countdownState.collectAsStateWithLifecycle()

    // 判断是否处于主 Tab 页面
    val isOnMainTab = currentNavRoute == null || currentNavRoute in MAIN_TAB_ROUTES

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
            Toast.makeText(context, message.resolve(context), Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(backgroundTaskViewModel) {
        backgroundTaskViewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.Toast -> toaster.show(
                    message = effect.message.resolve(context),
                    type = ToastType.Info,
                )
            }
        }
    }
    LaunchedEffect(appEventsViewModel) {
        appEventsViewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.Toast -> toaster.show(
                    message = effect.message.resolve(context),
                    type = ToastType.Success,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen wallpaper background — hide on wallpaper settings page & pure dark mode
        val wallpaperUri by appSettings.wallpaperUri.collectAsStateWithLifecycle()
        val wallpaperAlpha by appSettings.wallpaperAlpha.collectAsStateWithLifecycle()
        val wallpaperBlur by appSettings.wallpaperBlur.collectAsStateWithLifecycle()
        val wallpaperFrostedGlass by appSettings.wallpaperFrostedGlass.collectAsStateWithLifecycle()
        val useWallpaperColor by appSettings.useWallpaperColor.collectAsStateWithLifecycle()

        // Theme awareness for wallpaper behavior
        val themeMode by appSettings.themeMode.collectAsStateWithLifecycle()
        val isDarkTheme = when (themeMode) {
            AppSettingsManager.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            AppSettingsManager.ThemeMode.WHITE -> false
            AppSettingsManager.ThemeMode.DARK, AppSettingsManager.ThemeMode.PURE_DARK -> true
        }
        val showWallpaper = wallpaperUri.isNotEmpty() &&
            currentNavRoute != Routes.WALLPAPER_SETTINGS &&
            themeMode != AppSettingsManager.ThemeMode.PURE_DARK

        // Load full-quality bitmap for wallpaper display
        val nativeBitmap = remember(wallpaperUri, showWallpaper) {
            if (!showWallpaper || wallpaperUri.isEmpty()) null
            else BitmapUtils.loadDownsampledBitmap(context, wallpaperUri)
        }
        // Recycle bitmap when wallpaper changes or is hidden
        androidx.compose.runtime.DisposableEffect(nativeBitmap) {
            onDispose { nativeBitmap?.recycle() }
        }

        if (showWallpaper) {
            val bitmap = remember(nativeBitmap) { nativeBitmap?.asImageBitmap() }
            if (bitmap != null) {
                val contentScale = ContentScale.Crop
                // Wallpaper image
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = contentScale,
                    alpha = wallpaperAlpha / 100f,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (wallpaperBlur > 0) Modifier.blur(wallpaperBlur.dp) else Modifier
                        ),
                )
                // Dark mode: dim the wallpaper
                if (isDarkTheme) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                    )
                }
            }
        }

        // Dynamic color: when useWallpaperColor is ON:
        // - has wallpaper → extract seed color from custom wallpaper
        // - no wallpaper → use system dynamic color (from system wallpaper)
        val dynamicColorScheme = remember(nativeBitmap, wallpaperUri, useWallpaperColor, isDarkTheme) {
            if (useWallpaperColor) {
                if (wallpaperUri.isNotEmpty() && nativeBitmap != null) {
                    val seed = WallpaperColorScheme.extractSeedColor(nativeBitmap)
                    WallpaperColorScheme.generateColorScheme(seed, isDarkTheme)
                } else null // no custom wallpaper → fall through to system dynamic color in Theme.kt
            } else null
        }

        // Adaptive color scheme: transparent Scaffold background when wallpaper is shown.
        // Only background is transparent so wallpaper shows through the scaffold.
        // When frosted glass is enabled, dialog/card surfaces become semi-transparent.
        val baseScheme = dynamicColorScheme ?: MaterialTheme.colorScheme
        val transparentColorScheme = if (showWallpaper) {
            baseScheme.copy(
                background = Color.Transparent,
                surface = if (wallpaperFrostedGlass) baseScheme.surface.copy(alpha = 0.55f) else baseScheme.surface,
                surfaceVariant = if (wallpaperFrostedGlass) baseScheme.surfaceVariant.copy(alpha = 0.55f) else baseScheme.surfaceVariant,
                surfaceContainerLowest = if (wallpaperFrostedGlass) baseScheme.surfaceContainerLowest.copy(alpha = 0.35f) else baseScheme.surfaceContainerLowest,
                surfaceContainerLow = if (wallpaperFrostedGlass) baseScheme.surfaceContainerLow.copy(alpha = 0.40f) else baseScheme.surfaceContainerLow,
                surfaceContainer = if (wallpaperFrostedGlass) baseScheme.surfaceContainer.copy(alpha = 0.65f) else baseScheme.surfaceContainer,
                surfaceContainerHigh = if (wallpaperFrostedGlass) baseScheme.surfaceContainerHigh.copy(alpha = 0.80f) else baseScheme.surfaceContainerHigh,
                surfaceContainerHighest = if (wallpaperFrostedGlass) baseScheme.surfaceContainerHighest.copy(alpha = 0.90f) else baseScheme.surfaceContainerHighest,
                outline = if (wallpaperFrostedGlass) baseScheme.outline.copy(alpha = 0.35f) else baseScheme.outline,
                outlineVariant = if (wallpaperFrostedGlass) baseScheme.outlineVariant.copy(alpha = 0.30f) else baseScheme.outlineVariant,
            )
        } else if (dynamicColorScheme != null) {
            dynamicColorScheme
        } else baseScheme

        // MainScreen with HorizontalPager for smooth tab switching
        MaterialTheme(colorScheme = transparentColorScheme) {
            MainScreen(
                navController = navController,
                backgroundTaskViewModel = backgroundTaskViewModel,
                onViewAnnouncement = { forceShowAnnouncement = true },
                visible = isOnMainTab,
                fullscreen = isFullscreen,
            )

            // NavHost 只承载子页面；主 Tab 切换完全由 MainScreen 的 HorizontalPager 处理。
            // 在此统一下发 LocalToaster，使所有子页面都能弹出顶部提示。
            CompositionLocalProvider(LocalToaster provides toaster) {
                NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                enterTransition = { MaaAnimations.sharedAxisForwardEnter },
                exitTransition = { MaaAnimations.sharedAxisForwardExit },
                popEnterTransition = { MaaAnimations.sharedAxisPopEnter },
                popExitTransition = { MaaAnimations.sharedAxisPopExit },
            ) {
                // 主 Tab 路由仅作占位，真实内容由 MainScreen 的 HorizontalPager 渲染
                BottomNavTab.all.forEach { tab -> composable(tab.route) {} }

                composable(Routes.NOTIFICATION) {
                    NotificationSettingsView(navController = navController)
                }
                composable(Routes.ACHIEVEMENT) {
                    AchievementView(navController = navController)
                }
                composable(Routes.ACHIEVEMENT_DEBUG) {
                    AchievementDebugView(navController = navController)
                }
                composable(Routes.LOG_HISTORY) {
                    LogHistoryView(navController = navController)
                }
                composable(Routes.ERROR_LOG) {
                    ErrorLogView(navController = navController)
                }
                composable(Routes.SCHEDULE_EDIT) { backStackEntry ->
                    val strategyId = backStackEntry.arguments?.getString("strategyId")
                        .let { if (it == "new") null else it }
                    ScheduleEditView(navController = navController, strategyId = strategyId)
                }
                composable(Routes.SCHEDULE_TRIGGER_LOG) {
                    ScheduleTriggerLogView(navController = navController)
                }
                composable(Routes.TASK_OVERRIDE_EDITOR) {
                    TaskOverrideEditorView(navController = navController)
                }
                composable(Routes.WALLPAPER_SETTINGS) {
                    val settingsVm: com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel = koinViewModel()
                    WallpaperSettingsView(navController = navController, viewModel = settingsVm)
                }
            }  // NavHost
            }
                ResourceLoadingOverlay()
}  // MaterialTheme
        // 顶部轻提示（sonner）：替代旧的 Material3 Snackbar，按类型上色（成功=绿、错误=红）
        Toaster(
            state = toaster,
            alignment = Alignment.TopCenter,
            richColors = true,
            showCloseButton = true,
            darkTheme = isDarkTheme,
            containerPadding = PaddingValues(top = 8.dp),
            modifier = Modifier.statusBarsPadding(),
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
            androidx.compose.material3.MaterialTheme(colorScheme = baseScheme) {
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
}
