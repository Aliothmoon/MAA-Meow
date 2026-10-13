package com.aliothmoon.maameow.domain.launch

import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.utils.i18n.UiText
import java.util.UUID

enum class LaunchSource {
    Schedule,
    External,
}

data class LaunchRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val source: LaunchSource,
    val profileId: String,
    val displayName: String,
    val scheduledTimeMs: Long,
    val forceStart: Boolean = false,
    /** 运行期间屏保；仅后台且待机接管时生效 */
    val autoScreenSaver: Boolean = false,
    /** 熄屏挂机改为倒计时后关物理屏，结束不恢复 */
    val hardwareScreenOff: Boolean = false,
    /** 在用手机时不拉界面，改通知倒计时；仅后台定时 */
    val silentStartWhenInUse: Boolean = false,
    val autoSleepAfterTask: Boolean = false,
    /** 启动时已亮屏未锁屏则不熄屏 */
    val skipAutoSleepIfAwake: Boolean = false,
    val closeGameAfterTask: Boolean = false,
    val strategyId: String = "",
    val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,
) {
    companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 30
    }
}

/** 提权后端拦下启动：[reason] 进通知与终局文案，[detail] 是技术原因，只进触发日志 */
data class BackendBlock(val reason: UiText, val detail: String? = null)

sealed interface LaunchSession {
    data object Idle : LaunchSession

    data class InFlight(
        val request: LaunchRequest,
        val phase: Phase,
        val presentation: LaunchPresentation = LaunchPresentation.DIALOG,
    ) : LaunchSession

    sealed interface Phase {
        data object DevicePrep : Phase
        data class Counting(val remainingSeconds: Int) : Phase
        data object Preparing : Phase
        data object Starting : Phase
    }
}

enum class LaunchPresentation {
    /** 前台模式，无倒计时 */
    NONE,

    /** 后台，界面里弹 Dialog 倒计时 */
    DIALOG,

    /** 后台静默启动，不拉界面，定时服务的通知里倒计时 */
    NOTIFICATION,
}

sealed interface LaunchUserEvent {
    data object Cancel : LaunchUserEvent
    data object StartNow : LaunchUserEvent
}

fun LaunchSession.toCountdownState(): CountdownState {
    return when (this) {
        is LaunchSession.InFlight -> when (val phase = phase) {
            is LaunchSession.Phase.Counting -> CountdownState.Counting(
                strategyName = request.displayName,
                remainingSeconds = phase.remainingSeconds,
            )

            LaunchSession.Phase.Preparing,
            LaunchSession.Phase.Starting -> CountdownState.Executing

            else -> CountdownState.Idle
        }

        LaunchSession.Idle -> CountdownState.Idle
    }
}
