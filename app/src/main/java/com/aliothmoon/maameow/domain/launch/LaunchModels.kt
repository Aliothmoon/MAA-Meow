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
    val wakeUnlock: Boolean = false,
    val autoSleepAfterTask: Boolean = false,
    val strategyId: String = "",
    val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,
) {
    companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 30
    }
}

sealed interface LaunchSession {
    data object Idle : LaunchSession

    data class InFlight(
        val request: LaunchRequest,
        val phase: Phase,
        /**
         * 是否需要主界面导航到后台页（Dialog 路径）
         * FG 静默 Silent/Overlay 为 false，避免强行拉回主 Tab
         */
        val presentUi: Boolean = true,
    ) : LaunchSession

    sealed interface Phase {
        data object DevicePrep : Phase
        data class Counting(val remainingSeconds: Int) : Phase
        data object Preparing : Phase
        data object Starting : Phase
    }
}

sealed interface LaunchUserEvent {
    data object Cancel : LaunchUserEvent
    data object StartNow : LaunchUserEvent
}

/** 一次性 UI 反馈（导航由 [LaunchSession.InFlight.presentUi] 驱动，不再双轨） */
sealed interface LaunchEffect {
    data class Feedback(val message: UiText) : LaunchEffect
}

enum class CountdownMode {
    Silent,
    Overlay,
    DialogAndOverlay,
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
