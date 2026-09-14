package com.aliothmoon.maameow.domain.launch

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartDecision
import com.aliothmoon.maameow.domain.usecase.TaskStartDecisionReason
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StartTaskChainUseCaseTest {
    private val prepare = mockk<PrepareTaskStartUseCase>()
    private val useCase = StartTaskChainUseCase(
        prepare = prepare,
        composition = mockk(relaxed = true),
        muteCoordinator = mockk(relaxed = true),
        achievements = mockk(relaxed = true),
        sessionLogger = mockk(relaxed = true),
        appSettingsManager = mockk(relaxed = true),
        appContext = mockk(relaxed = true),
    )

    @Test
    fun blockedDecision_logsLocalizedReasonInsteadOfEnumName() = runBlocking {
        val decision = TaskStartDecision.Blocked(TaskStartDecisionReason.GAME_NOT_ON_BACKGROUND_DISPLAY)
        coEvery { prepare(any(), any()) } returns decision

        val result = useCase(emptyList(), TaskStartContext(mode = TaskStartMode.SCHEDULED))

        assertEquals(
            StartTaskChainUseCase.Result.Failed(
                executionResult = ExecutionResult.FAILED_VALIDATION,
                message = uiTextOf(R.string.schedule_log_task_blocked, decision.message),
            ),
            result,
        )
    }
}
