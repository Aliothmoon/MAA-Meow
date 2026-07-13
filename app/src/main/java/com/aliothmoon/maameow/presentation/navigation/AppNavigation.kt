package com.aliothmoon.maameow.presentation.navigation

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
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
import com.aliothmoon.maameow.theme.rememberWallpaperDisplayBitmap
import com.aliothmoon.maameow.theme.wallpaperBlurModifier
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.ExternalNotificationService
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.presentation.LocalToaster
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
import com.aliothmoon.maameow.theme.WallpaperColorScheme
import com.aliothmoon.maameow.theme.withPureDarkSurfaces
import com.aliothmoon.maameow.utils.BitmapUtils
import com.aliothmoon.maameow.utils.WallpaperFileStore
import com.aliothmoon.maameow.utils.i18n.resolve
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val wallpaperTextContrast by appSettings.wallpaperTextContrast.collectAsStateWithLifecycle()

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

        // Load full-quality bitmap for wallpaper display on a background thread.
        // wallpaperUpdateVersion forces reload when the same file path is overwritten.
        val wallpaperUpdateVersion by appSettings.wallpaperUpdateVersion.collectAsStateWithLifecycle()
        var nativeBitmap by remember { mutableStateOf<Bitmap?>(null) }
        // Load whenever a custom wallpaper URI is set (not only when the full-screen image is
        // drawn). Seed extraction must keep working on the wallpaper settings page and while
        // the image layer is hidden, otherwise components fall back to the outer blue theme.
        val needWallpaperBitmap = wallpaperUri.isNotEmpty() &&
            themeMode != AppSettingsManager.ThemeMode.PURE_DARK
        LaunchedEffect(wallpaperUri, needWallpaperBitmap, wallpaperUpdateVersion) {
            // nativeBitmap?.recycle() removed - GC handles it
            if (!needWallpaperBitmap || wallpaperUri.isEmpty()) {
                nativeBitmap = null
                return@LaunchedEffect
            }
            val requestUri = wallpaperUri
            val requestVersion = wallpaperUpdateVersion
            // Migrate first if needed. Returning early after setWallpaperUri avoids a double
            // decode: the URI/version change will re-enter this effect once.
            val store = WallpaperFileStore(context)
            val migratedUri = withContext(Dispatchers.IO) {
                // Heal mid-edit crash before reading/migrating wallpaper files.
                store.reconcileOrphanEditBackup()
                store.migrateLegacyUriIfNeeded(requestUri)
            }
            if (requestUri != wallpaperUri || requestVersion != wallpaperUpdateVersion) return@LaunchedEffect
            if (migratedUri != null && migratedUri != requestUri) {
                appSettings.setWallpaperUri(migratedUri)
                return@LaunchedEffect
            }
            // Stale preference path with missing file: clear so the UI does not stay on a
            // transparent empty background forever.
            val exists = withContext(Dispatchers.IO) { store.fileExistsForUri(requestUri) }
            if (requestUri != wallpaperUri || requestVersion != wallpaperUpdateVersion) return@LaunchedEffect
            if (!exists) {
                nativeBitmap = null
                appSettings.setWallpaperUri("")
                return@LaunchedEffect
            }
            // Drop the previous frame immediately so same-path recrop cannot flash old pixels.
            nativeBitmap = null
            val loaded = withContext(Dispatchers.IO) {
                BitmapUtils.loadDownsampledBitmap(context, requestUri)
            }
            if (requestUri != wallpaperUri || requestVersion != wallpaperUpdateVersion) return@LaunchedEffect
            if (loaded == null) {
                // File exists but decode failed (transient IO/OOM/corrupt frame).
                // Keep wallpaperUri so a flaky decode does not wipe the user setting;
                // missing paths are already cleared above via !exists.
                nativeBitmap = null
            } else {
                nativeBitmap = loaded
            }
        }
        // DisposableEffect removed - avoid race condition with recycled bitmap

        if (showWallpaper) {
            // API 31+: sharp bitmap + Modifier.blur; API 28–30: software Stack Blur bitmap.
            val bitmap = rememberWallpaperDisplayBitmap(nativeBitmap, wallpaperBlur)
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
                        .then(wallpaperBlurModifier(wallpaperBlur)),
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

        // Dynamic color: quantize seed once per wallpaper identity; rebuild scheme when theme flips.
        // Do not gate on nativeBitmap alone — seed must still resolve when the full-screen image
        // layer is hidden (settings page) or still loading, so UI components keep wallpaper colors.
        var wallpaperSeedArgb by remember { mutableStateOf<Int?>(null) }
        // Seed is used for (1) dynamic ColorScheme and (2) content/text contrast on wallpaper.
        // Load whenever a wallpaper is set; PURE_DARK still skips bitmap load above so no seed there.
        // Include the two color switches in keys so toggling them re-runs seed load/clear
        // (otherwise needSeed is only read once and late ON may leave wallpaperSeedArgb null).
        LaunchedEffect(
            nativeBitmap,
            wallpaperUri,
            wallpaperUpdateVersion,
            needWallpaperBitmap,
            useWallpaperColor,
            wallpaperTextContrast,
        ) {
            val needSeed = wallpaperTextContrast || useWallpaperColor
            if (wallpaperUri.isEmpty() || !needWallpaperBitmap) {
                wallpaperSeedArgb = null
                return@LaunchedEffect
            }
            if (!needSeed) {
                wallpaperSeedArgb = null
                return@LaunchedEffect
            }
            val requestUri = wallpaperUri
            val requestVersion = wallpaperUpdateVersion
            val displayBitmap = nativeBitmap
            val seed = withContext(Dispatchers.Default) {
                val store = WallpaperFileStore(context)
                val identity = store.wallpaperIdentity(requestUri)
                store.loadCachedSeed(identity)?.let { return@withContext it }
                val sourceBitmap = displayBitmap
                    ?: BitmapUtils.loadDownsampledBitmap(context, requestUri, maxDimension = 512)
                if (sourceBitmap == null) {
                    null
                } else {
                    try {
                        WallpaperColorScheme.extractSeedColor(sourceBitmap).also { extracted ->
                            store.saveCachedSeed(identity, extracted)
                        }
                    } finally {
                        // Only recycle the temporary sample we loaded ourselves.
                        if (sourceBitmap !== displayBitmap && !sourceBitmap.isRecycled) {
                            sourceBitmap.recycle()
                        }
                    }
                }
            }
            // Drop superseded seed results from a faster previous recrop/switch.
            if (requestUri == wallpaperUri && requestVersion == wallpaperUpdateVersion) {
                wallpaperSeedArgb = seed
            }
        }
        val dynamicColorScheme = remember(wallpaperSeedArgb, isDarkTheme) {
            wallpaperSeedArgb?.let { WallpaperColorScheme.generateColorScheme(it, isDarkTheme) }
        }
        // Keep the last wallpaper-derived scheme to avoid flashing the outer system/monet
        // theme while a new seed is being quantized.
        var lastWallpaperScheme by remember { mutableStateOf<ColorScheme?>(null) }
        SideEffect {
            if (dynamicColorScheme != null) {
                lastWallpaperScheme = dynamicColorScheme
            } else if (!useWallpaperColor || wallpaperUri.isEmpty()) {
                lastWallpaperScheme = null
            }
        }

        // Adaptive color scheme: transparent Scaffold background when wallpaper is shown.
        // Only background is transparent so wallpaper shows through the scaffold.
        // When frosted glass is enabled, dialog/card surfaces become semi-transparent.
        val isPureDark = themeMode == AppSettingsManager.ThemeMode.PURE_DARK
        // PURE_DARK: never let wallpaper MCU / residual scheme tint surface containers gray.
        // Still allow primary accents from outer theme; force pure-black surface family.
        val baseScheme = when {
            isPureDark -> MaterialTheme.colorScheme.withPureDarkSurfaces()
            useWallpaperColor && dynamicColorScheme != null -> dynamicColorScheme
            useWallpaperColor && wallpaperUri.isNotEmpty() && lastWallpaperScheme != null -> lastWallpaperScheme!!
            else -> MaterialTheme.colorScheme
        }.let { scheme ->
            // Content/text only: pick readable ink from wallpaper seed.
            // Independent of useWallpaperColor. Gate on wallpaper *existing* (and not
            // PURE_DARK), not showWallpaper — settings page hides the full-screen layer
            // but should still preview dynamic text when the toggle is on.
            val seed = wallpaperSeedArgb
            val wallpaperActive =
                wallpaperUri.isNotEmpty() && !isPureDark
            if (wallpaperActive && wallpaperTextContrast && seed != null) {
                with(WallpaperColorScheme) { scheme.adaptContentForWallpaper(seed, isDarkTheme) }
            } else {
                scheme
            }
        }
        val transparentColorScheme = if (showWallpaper) {
            if (wallpaperFrostedGlass) {
                baseScheme.copy(
                    background = Color.Transparent,
                    surface = baseScheme.surface.copy(alpha = 0.55f),
                    surfaceVariant = baseScheme.surfaceVariant.copy(alpha = 0.55f),
                    surfaceBright = baseScheme.surfaceBright.copy(alpha = 0.70f),
                    surfaceDim = baseScheme.surfaceDim.copy(alpha = 0.50f),
                    surfaceContainerLowest = baseScheme.surfaceContainerLowest.copy(alpha = 0.35f),
                    surfaceContainerLow = baseScheme.surfaceContainerLow.copy(alpha = 0.40f),
                    surfaceContainer = baseScheme.surfaceContainer.copy(alpha = 0.65f),
                    surfaceContainerHigh = baseScheme.surfaceContainerHigh.copy(alpha = 0.80f),
                    surfaceContainerHighest = baseScheme.surfaceContainerHighest.copy(alpha = 0.90f),
                    outline = baseScheme.outline.copy(alpha = 0.35f),
                    outlineVariant = baseScheme.outlineVariant.copy(alpha = 0.30f),
                )
            } else {
                baseScheme.copy(background = Color.Transparent)
            }
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
        // Outside main MaterialTheme: re-apply wallpaper baseScheme so toast chrome follows seed colors.
        MaterialTheme(colorScheme = baseScheme) {
            Toaster(
                state = toaster,
                alignment = Alignment.TopCenter,
                richColors = true,
                showCloseButton = true,
                darkTheme = isDarkTheme,
                containerPadding = PaddingValues(top = 8.dp),
                modifier = Modifier.statusBarsPadding(),
            )
        }
        // 全局定时任务倒计时弹窗（前台所有控制模式均不弹出对话框，静默处理）
        // Outside the main MaterialTheme block (same as announcement): must re-apply
        // wallpaper-derived baseScheme so AlertDialog follows dynamic colors.
        val countdown = scheduledCountdownState
        val hideCountdownDialog = runMode == RunMode.FOREGROUND
        if (countdown is CountdownState.Counting && !hideCountdownDialog) {
            MaterialTheme(colorScheme = baseScheme) {
                CountdownDialog(
                    state = countdown,
                    onCancel = { backgroundTaskViewModel.onScheduledCountdownCancel() },
                    onStartNow = { backgroundTaskViewModel.onScheduledStartNow() },
                )
            }
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
