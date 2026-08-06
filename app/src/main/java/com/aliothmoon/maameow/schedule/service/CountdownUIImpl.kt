package com.aliothmoon.maameow.schedule.service

import com.aliothmoon.maameow.domain.launch.CountdownUI
import com.aliothmoon.maameow.domain.launch.LaunchRequest
import com.aliothmoon.maameow.domain.launch.LaunchUserEvent
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.schedule.model.CountdownState
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 后台倒计时：写 Overlay 倒计时状态 + 支持立即执行
 * Dialog 由 presentation 订 LaunchPipeline.session
 */
class CountdownUIImpl(
    private val overlayController: OverlayController,
    private val onUserEvent: (LaunchUserEvent) -> Unit,
) : CountdownUI {

    override suspend fun await(
        request: LaunchRequest,
        onTick: (remainingSeconds: Int) -> Unit,
        shouldAbort: () -> Boolean,
    ): Boolean {
        val startNow = AtomicBoolean(false)
        try {
            overlayController.setTemporaryCountdownListener {
                startNow.set(true)
                onUserEvent(LaunchUserEvent.StartNow)
            }
            for (remaining in request.countdownSeconds downTo 1) {
                if (shouldAbort() || startNow.get()) break
                onTick(remaining)
                overlayController.updateCountdownState(
                    CountdownState.Counting(request.displayName, remaining),
                )
                delay(1000)
            }
        } finally {
            overlayController.updateCountdownState(CountdownState.Idle)
            overlayController.setTemporaryCountdownListener(null)
        }
        return startNow.get()
    }
}
