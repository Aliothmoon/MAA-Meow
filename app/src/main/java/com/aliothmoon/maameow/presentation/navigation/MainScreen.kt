package com.aliothmoon.maameow.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.presentation.view.background.BackgroundTaskView
import com.aliothmoon.maameow.presentation.view.home.HomeView
import com.aliothmoon.maameow.presentation.view.settings.SettingsView
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.schedule.ui.ScheduleListView
import com.aliothmoon.maameow.theme.MaaAnimations
import kotlinx.coroutines.launch
import kotlin.math.abs


@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    backgroundTaskViewModel: BackgroundTaskViewModel,
    onViewAnnouncement: () -> Unit = {},
    visible: Boolean = true,
    fullscreen: Boolean = false,
) {
    val pagerState = rememberPagerState(pageCount = { BottomNavTab.all.size })
    val scope = rememberCoroutineScope()

    // targetPage：点击/滑动一旦确定目标即生效，停稳后等于 currentPage。
    // animateScrollToPage 内部走 MutatorMutex，连续调用时后者自动接管，无需手动取消。
    fun goToPage(index: Int) {
        if (index !in BottomNavTab.all.indices || index == pagerState.targetPage) return
        scope.launch {
            val distance = abs(index - pagerState.currentPage).coerceAtLeast(1)
            pagerState.animateScrollToPage(
                page = index,
                animationSpec = tween(
                    durationMillis = 100 * distance + 100,
                    easing = MaaAnimations.springEasing,
                ),
            )
        }
    }

    // 非首页 Tab 按返回键先回到首页；全屏由 BackgroundTaskView 自行处理。
    BackHandler(enabled = visible && !fullscreen && pagerState.targetPage != 0) {
        goToPage(0)
    }

    // 定时任务触发时：若正处于子页面，先弹回主 Tab 浮出主界面，再滑到后台任务页
    // （恢复旧导航 navigate(BACKGROUND){popUpTo(HOME)} 的“自动浮出后台页”语义）。
    val pendingScheduledExecution by backgroundTaskViewModel.coordinator.pendingExecution.collectAsStateWithLifecycle()
    LaunchedEffect(pendingScheduledExecution?.requestId) {
        if (pendingScheduledExecution != null) {
            navController.popBackStack(Routes.HOME, false)
            goToPage(BottomNavTab.all.indexOf(BottomNavTab.BACKGROUND))
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (visible && !fullscreen) {
                AppBottomNavigation(
                    currentRoute = BottomNavTab.all[pagerState.targetPage].route,
                    onTabSelected = { tab -> goToPage(BottomNavTab.all.indexOf(tab)) },
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
                .graphicsLayer {
                    // 隐藏时跳过绘制但保留组合树，避免 HorizontalPager 状态丢失
                    alpha = if (visible) 1f else 0f
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { BottomNavTab.all[it].route },
                userScrollEnabled = visible && !fullscreen,
            ) { page ->
                when (BottomNavTab.all[page]) {
                    BottomNavTab.HOME -> HomeView(navController = navController)
                    BottomNavTab.BACKGROUND -> BackgroundTaskView(
                        viewModel = backgroundTaskViewModel,
                    )

                    BottomNavTab.SCHEDULE -> ScheduleListView(navController = navController)
                    BottomNavTab.SETTINGS -> SettingsView(
                        navController = navController,
                        onViewAnnouncement = onViewAnnouncement,
                    )
                }
            }

            // 隐藏（子页面叠加其上）时吞掉所有指针，防止横滑切走主 Tab / 点击穿透到底层页。
            if (!visible) {
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        })
            }
        }
    }
}
