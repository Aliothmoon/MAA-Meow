package com.aliothmoon.maameow.presentation.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aliothmoon.maameow.announcement.AnnouncementConfig
import com.aliothmoon.maameow.announcement.AnnouncementContent
import com.aliothmoon.maameow.announcement.AnnouncementManager
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.launch.LaunchEffect
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.ExternalNotificationService
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.presentation.LocalToaster
import com.aliothmoon.maameow.presentation.components.AnnouncementDialog
import com.aliothmoon.maameow.presentation.components.ResourceLoadingOverlay
import com.aliothmoon.maameow.presentation.components.clearFocusOnBlankTap
import com.aliothmoon.maameow.presentation.onboarding.LocalOnboardingState
import com.aliothmoon.maameow.presentation.onboarding.OnboardingOverlay
import com.aliothmoon.maameow.presentation.onboarding.OnboardingState
import com.aliothmoon.maameow.presentation.pip.LocalIsInPip
import com.aliothmoon.maameow.presentation.state.UiEffect
import com.aliothmoon.maameow.presentation.view.notification.NotificationSettingsView
import com.aliothmoon.maameow.presentation.view.settings.AchievementDebugView
import com.aliothmoon.maameow.presentation.view.settings.AchievementView
import com.aliothmoon.maameow.presentation.view.settings.ErrorLogView
import com.aliothmoon.maameow.presentation.view.settings.LogHistoryView
import com.aliothmoon.maameow.presentation.view.settings.TaskOverrideEditorView
import com.aliothmoon.maameow.presentation.viewmodel.AppEventsViewModel
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.schedule.ui.CountdownDialog
import com.aliothmoon.maameow.schedule.ui.ScheduleEditView
import com.aliothmoon.maameow.schedule.ui.ScheduleTriggerLogView
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaMotion
import com.aliothmoon.maameow.utils.i18n.resolve
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
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
    announcementManager: AnnouncementManager = koinInject(),
    achievementReporter: AchievementReporter = koinInject(),
    resourceInitService: ResourceInitService = koinInject(),
    mainTabNavigator: MainTabNavigator = koinInject(),
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
    val announcementReadHash by appSettings.announcementReadHash.collectAsStateWithLifecycle()
    val language by appSettings.language.collectAsStateWithLifecycle()
    val announcementContent by announcementManager.content.collectAsStateWithLifecycle()

    // 远端公告：ETag 条件请求，304（内容未变）不会触发弹窗；语言切换时重拉
    LaunchedEffect(language) {
        announcementManager.refresh(language)
    }

    // 首启引导：资源就绪后自动开始
    val needsOnboarding by appSettings.needsOnboarding.collectAsStateWithLifecycle()
    val onboardingState = remember { OnboardingState(pending = needsOnboarding) }
    LaunchedEffect(needsOnboarding) { onboardingState.pending = needsOnboarding }
    val resourceInitState by resourceInitService.state.collectAsStateWithLifecycle()
    val resourceReady = resourceInitState is ResourceInitState.Ready
    LaunchedEffect(resourceReady, needsOnboarding) {
        if (resourceReady && needsOnboarding) onboardingState.start()
    }
    // 跳过或看完都算看过；重看再写一次是幂等的
    LaunchedEffect(onboardingState) {
        snapshotFlow { onboardingState.active }
            .drop(1)
            .filter { !it }
            .collect { appSettings.markOnboardingSeen() }
    }
    val scheduledCountdownState by backgroundTaskViewModel.countdownState.collectAsStateWithLifecycle()

    // 判断是否处于主 Tab 页面
    val isOnMainTab = currentNavRoute == null || currentNavRoute in MAIN_TAB_ROUTES

    LaunchedEffect(backgroundTaskViewModel) {
        backgroundTaskViewModel.launchEffects.collect { effect ->
            when (effect) {
                is LaunchEffect.Feedback -> {
                    Toast.makeText(
                        context,
                        effect.message.resolve(context),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
    // 后台倒计时 Overlay 由 CountdownUIImpl 写入；前台无倒计时不触碰
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

    // LocalToaster 未提供即抛错，须同时覆盖 pager 与 NavHost 子页
    val reduceMotion = LocalReduceMotion.current
    CompositionLocalProvider(
        LocalToaster provides toaster,
        LocalOnboardingState provides onboardingState,
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .clearFocusOnBlankTap()) {
            MainScreen(
                navController = navController,
                backgroundTaskViewModel = backgroundTaskViewModel,
                onViewAnnouncement = { forceShowAnnouncement = true },
                onViewOnboarding = { onboardingState.start() },
                visible = isOnMainTab,
                fullscreen = isFullscreen,
            )

            // NavHost 只承载子页面；主 Tab 切换完全由 MainScreen 的 HorizontalPager 处理
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                enterTransition = { MaaMotion.pageEnter(forward = true, reduceMotion) },
                exitTransition = { MaaMotion.pageExit(forward = true, reduceMotion) },
                popEnterTransition = { MaaMotion.pageEnter(forward = false, reduceMotion) },
                popExitTransition = { MaaMotion.pageExit(forward = false, reduceMotion) },
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
            }
            // 画中画只留预览画面
            if (!LocalIsInPip.current) {
                ResourceLoadingOverlay()
                // 聚光灯引导：压在主界面之上、轻提示之下，toast 仍可读
                OnboardingOverlay(
                    state = onboardingState,
                    onRequestTab = { mainTabNavigator.navigateTo(it, animate = false) },
                )
                // 顶部轻提示（sonner）：替代旧的 Material3 Snackbar，按类型上色（成功=绿、错误=红）
                Toaster(
                    state = toaster,
                    alignment = Alignment.TopCenter,
                    richColors = true,
                    showCloseButton = true,
                    darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f,
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
                // 长期公告弹窗：远端内容变化（哈希与已读标记不符）后首次启动自动弹出，或从设置中手动打开
                val current = announcementContent
                val needsToShow = current != null && current.hash != announcementReadHash
                val showAnnouncement = forceShowAnnouncement ||
                        (needsToShow && !announcementDismissedOnce && !onboardingState.blocksStartupDialogs)
                val shownAnnouncement = remember(showAnnouncement, language, current) {
                    if (!showAnnouncement) {
                        null
                    } else {
                        // 手动打开时拉取可能尚未完成，回退内置 assets
                        current ?: AnnouncementConfig.loadContent(context, language)
                            .takeIf { it.isNotBlank() }
                            ?.let { AnnouncementContent.of(it) }
                    }
                }
                if (shownAnnouncement != null) {
                    AnnouncementDialog(
                        imageAssetPath = remember(language) {
                            AnnouncementConfig.imageAssetPath(language)
                        },
                        markdown = shownAnnouncement.markdown,
                        onDismiss = { dontShowAgain ->
                            forceShowAnnouncement = false
                            if (dontShowAgain) {
                                coroutineScope.launch {
                                    appSettings.setAnnouncementReadHash(shownAnnouncement.hash)
                                }
                            } else {
                                announcementDismissedOnce = true
                            }
                        },
                        onStubbornUnlock = { achievementReporter.reportAnnouncementStubbornClick() },
                    )
                }
            }
        }
    }
}
