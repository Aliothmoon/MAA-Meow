package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.data.model.AwardConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.domain.service.AppAliveChecker
import com.aliothmoon.maameow.remote.AppAliveStatus
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PrepareTaskStartUseCaseTest {

    private val analyzeTaskChainUseCase = AnalyzeTaskChainUseCase()

    @Test
    fun manualStart_requiresConfirmation_whenGameIsDeadAndNoWakeUpLaunchConfigured() = runBlocking {
        val useCase = PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = analyzeTaskChainUseCase,
            appAliveChecker = FakeAppAliveChecker(AppAliveStatus.DEAD),
        )

        val result = useCase(
            chain = listOf(TaskChainNode(name = "领取奖励", enabled = true, config = AwardConfig())),
            context = TaskStartContext(mode = TaskStartMode.MANUAL),
        )

        assertEquals(
            TaskStartDecision.RequiresConfirmation(
                reason = TaskStartDecisionReason.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
                message = PrepareTaskStartUseCase.NO_WAKE_UP_WARNING_MESSAGE,
                acknowledgement = TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
            ),
            result
        )
    }

    @Test
    fun scheduledStart_blocksFast_whenGameIsDeadAndNoWakeUpLaunchConfigured() = runBlocking {
        val useCase = PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = analyzeTaskChainUseCase,
            appAliveChecker = FakeAppAliveChecker(AppAliveStatus.DEAD),
        )

        val result = useCase(
            chain = listOf(TaskChainNode(name = "领取奖励", enabled = true, config = AwardConfig())),
            context = TaskStartContext(mode = TaskStartMode.SCHEDULED),
        )

        assertEquals(
            TaskStartDecision.Blocked(
                reason = TaskStartDecisionReason.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
                message = PrepareTaskStartUseCase.SCHEDULED_NO_WAKE_UP_FAILURE_MESSAGE,
            ),
            result
        )
    }

    @Test
    fun acknowledgedManualStart_returnsReady_withoutRecheckingWarning() = runBlocking {
        val useCase = PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = analyzeTaskChainUseCase,
            appAliveChecker = FakeAppAliveChecker(AppAliveStatus.DEAD),
        )

        val result = useCase(
            chain = listOf(TaskChainNode(name = "领取奖励", enabled = true, config = AwardConfig())),
            context = TaskStartContext(
                mode = TaskStartMode.MANUAL,
                acknowledgements = setOf(TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP),
            ),
        )

        assertTrue(result is TaskStartDecision.Ready)
    }

    @Test
    fun launchesGame_skipsAliveCheck_andReturnsReady() = runBlocking {
        val checker = FakeAppAliveChecker(AppAliveStatus.DEAD)
        val useCase = PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = analyzeTaskChainUseCase,
            appAliveChecker = checker,
        )

        val result = useCase(
            chain = listOf(
                TaskChainNode(
                    name = "开始唤醒",
                    enabled = true,
                    config = WakeUpConfig(clientType = "Official", startGameEnabled = true),
                )
            ),
            context = TaskStartContext(mode = TaskStartMode.MANUAL),
        )

        assertTrue(result is TaskStartDecision.Ready)
        assertEquals(0, checker.callCount)
    }

    @Test
    fun unknownAliveStatus_returnsReady() = runBlocking {
        val useCase = PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = analyzeTaskChainUseCase,
            appAliveChecker = FakeAppAliveChecker(AppAliveStatus.UNKNOWN),
        )

        val result = useCase(
            chain = listOf(TaskChainNode(name = "领取奖励", enabled = true, config = AwardConfig())),
            context = TaskStartContext(mode = TaskStartMode.MANUAL),
        )

        assertTrue(result is TaskStartDecision.Ready)
    }

    @Test
    fun analysisFailure_isForwardedAsBlockedDecision() = runBlocking {
        val useCase = PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = analyzeTaskChainUseCase,
            appAliveChecker = FakeAppAliveChecker(AppAliveStatus.ALIVE),
        )

        val result = useCase(
            chain = emptyList(),
            context = TaskStartContext(mode = TaskStartMode.MANUAL),
        )

        assertEquals(
            TaskStartDecision.Blocked(
                reason = TaskStartDecisionReason.INVALID_CHAIN,
                message = "请先选择要执行的任务",
            ),
            result
        )
    }

    private class FakeAppAliveChecker(
        private val status: Int,
    ) : AppAliveChecker {
        var callCount: Int = 0
            private set

        override suspend fun isAppAlive(packageName: String): Int {
            callCount += 1
            return status
        }
    }
}
