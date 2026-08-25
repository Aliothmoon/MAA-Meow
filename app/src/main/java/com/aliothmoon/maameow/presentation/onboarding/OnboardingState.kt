package com.aliothmoon.maameow.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** 引导可高亮的靶点，由各页面用 [onboardingTarget] 上报位置 */
enum class OnboardingTarget {
    // 首页
    SERVICE_STATUS,
    RUN_MODE,
    PERMISSIONS,
    SERVICE_BUTTON,

    // 后台任务页
    BG_PREVIEW,
    BG_PANEL_TABS,
    BG_TASK_LIST,
    BG_START,

    // 定时任务页
    SCHEDULE_ADD,
    SCHEDULE_LIST,
    SCHEDULE_TRIGGER_LOG,

    // 底栏
    TAB_SETTINGS,
}

/**
 * 聚光灯引导运行时状态：当前步骤 + 靶点 root 坐标（页面上报，dispose 即清，切页不残留）
 *
 * @param pending 持久化的待展示标记，之后由 AppNavigation 同步
 */
@Stable
class OnboardingState(pending: Boolean = false) {

    var pending by mutableStateOf(pending)

    var active by mutableStateOf(false)
        private set

    var stepIndex by mutableIntStateOf(0)
        private set

    /** 待展示或展示中：启动弹窗让路；看 [pending] 才能首帧压住不闪 */
    val blocksStartupDialogs: Boolean get() = pending || active

    val steps: List<OnboardingStep> get() = OnboardingSteps.all

    val currentStep: OnboardingStep get() = steps[stepIndex]

    val bounds = mutableStateMapOf<OnboardingTarget, Rect>()

    /** 跳过或看完都结束于 [active] 变 false，持久化由观察方处理 */
    fun start() {
        if (active) return
        stepIndex = 0
        active = true
    }

    fun next() {
        if (stepIndex < steps.lastIndex) stepIndex++ else finish()
    }

    fun previous() {
        if (stepIndex > 0) stepIndex--
    }

    fun finish() {
        active = false
    }

    fun updateBounds(target: OnboardingTarget, rect: Rect) {
        if (bounds[target] != rect) bounds[target] = rect
    }

    fun clearBounds(target: OnboardingTarget) {
        bounds.remove(target)
    }
}

/** 未提供（预览 / 悬浮窗组合树）时靶点上报静默无效 */
val LocalOnboardingState = staticCompositionLocalOf<OnboardingState?> { null }

/** 登记为引导靶点；仅引导中挂监听，平时滚动零开销 */
@Composable
fun Modifier.onboardingTarget(target: OnboardingTarget): Modifier {
    val state = LocalOnboardingState.current ?: return this
    if (!state.active) return this
    DisposableEffect(state, target) {
        onDispose { state.clearBounds(target) }
    }
    return onGloballyPositioned { state.updateBounds(target, it.boundsInRoot()) }
}

/** 同 [OnboardingState.blocksStartupDialogs]；未提供时视为不让路 */
@Composable
fun onboardingBlocksStartupDialogs(): Boolean =
    LocalOnboardingState.current?.blocksStartupDialogs == true
