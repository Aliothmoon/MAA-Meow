package com.aliothmoon.maameow.domain.launch

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.models.PlanSideTask
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartDecision
import com.aliothmoon.maameow.domain.usecase.TaskStartDecisionReason
import com.aliothmoon.maameow.domain.usecase.TaskChainPlan
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.utils.i18n.uiTextDynamic
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StartTaskChainUseCaseTest {
    private val prepare = mockk<PrepareTaskStartUseCase>()
    private val sideTaskRunner = mockk<PlanSideTaskRunner>(relaxed = true)
    private val composition = mockk<MaaCompositionService>(relaxed = true)
    private val useCase = StartTaskChainUseCase(
        prepare = prepare,
        composition = composition,
        muteCoordinator = mockk(relaxed = true),
        achievements = mockk(relaxed = true),
        sessionLogger = mockk(relaxed = true),
        appSettingsManager = mockk(relaxed = true),
        appContext = mockk(relaxed = true),
        sideTaskRunner = sideTaskRunner,
    )

    private fun planWithoutCoreTask() = TaskChainPlan(
        nodes = emptyList(),
        params = emptyList(),
        clientType = "Official",
        gamePackageName = null,
        launchesGame = false,
        sideTasks = listOf(PlanSideTask.OPER_BOX_YITULIU),
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

    @Test
    fun planWithoutCoreTask_waitsForSideTaskAndSkipsCore() = runBlocking {
        val message = uiTextDynamic("done")
        coEvery { prepare(any(), any()) } returns TaskStartDecision.Ready(planWithoutCoreTask())
        coEvery { sideTaskRunner.runWithoutCore(any(), any()) } returns
                PlanSideTaskRunner.Outcome(ok = true, message = message)

        val result = useCase(emptyList(), TaskStartContext(mode = TaskStartMode.SCHEDULED))

        assertEquals(StartTaskChainUseCase.Result.SuccessWithoutCore(message), result)
        // 等在这里才有前台服务与唤醒锁；Core 一次都不能起
        coVerify(exactly = 0) { composition.start(any(), any(), any(), any(), any()) }
    }

    @Test
    fun sideTaskFailure_isNotReportedAsStarted() = runBlocking {
        val message = uiTextDynamic("token empty")
        coEvery { prepare(any(), any()) } returns TaskStartDecision.Ready(planWithoutCoreTask())
        coEvery { sideTaskRunner.runWithoutCore(any(), any()) } returns
                PlanSideTaskRunner.Outcome(ok = false, message = message)

        val result = useCase(emptyList(), TaskStartContext(mode = TaskStartMode.SCHEDULED))

        assertEquals(
            StartTaskChainUseCase.Result.Failed(ExecutionResult.FAILED_START, message),
            result,
        )
    }
}
