package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.data.model.AwardConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.AppAliveChecker
import com.aliothmoon.maameow.remote.AppAliveStatus
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PrepareTaskStartUseCaseTest {

    private val taskChainState = mockk<TaskChainState> {
        every { getClientType() } returns "Official"
    }
    private val analyzeTaskChainUseCase = AnalyzeTaskChainUseCase(taskChainState)
    private val appSettings = mockk<AppSettingsManager> {
        every { runMode } returns MutableStateFlow(RunMode.BACKGROUND)
    }

    @Test
    fun manualStart_requiresConfirmation_whenGameIsDeadAndNoWakeUpLaunchConfigured() = runBlocking {
        val useCase = PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = analyzeTaskChainUseCase,
            appAliveChecker = FakeAppAliveChecker(AppAliveStatus.DEAD),
            appSettings = appSettings,
        )

        val result = useCase(
            chain = listOf(TaskChainNode(name = "领取奖励", enabled = true, config = AwardConfig())),
            context = TaskStartContext(mode = TaskStartMode.MANUAL),
        )

        assertEquals(
            TaskStartDecision.RequiresConfirmation(
                reason = TaskStartDecisionReason.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
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
            appSettings = appSettings,
        )

        val result = useCase(
            chain = listOf(TaskChainNode(name = "领取奖励", enabled = true, config = AwardConfig())),
            context = TaskStartContext(mode = TaskStartMode.SCHEDULED),
        )

        assertEquals(
            TaskStartDecision.Blocked(
                reason = TaskStartDecisionReason.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
            ),
            result
        )
    }

    @Test
    fun acknowledgedManualStart_returnsReady_withoutRecheckingWarning() = runBlocking {
        val useCase = PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = analyzeTaskChainUseCase,
            appAliveChecker = FakeAppAliveChecker(AppAliveStatus.DEAD),
            appSettings = appSettings,
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
            appSettings = appSettings,
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
            appSettings = appSettings,
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
            appSettings = appSettings,
        )

        val result = useCase(
            chain = emptyList(),
            context = TaskStartContext(mode = TaskStartMode.MANUAL),
        )

        assertEquals(
            TaskStartDecision.Blocked(
                reason = TaskStartDecisionReason.NO_TASK_SELECTED,
            ),
            result
        )
    }

    @Test
    fun manualStart_requiresConfirmation_whenGameNotInstalled() = runBlocking {
        val useCase = PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = analyzeTaskChainUseCase,
            appAliveChecker = FakeAppAliveChecker(AppAliveStatus.ALIVE),
            appSettings = appSettings,
            isPackageInstalled = { false },
        )

        val result = useCase(
            chain = listOf(TaskChainNode(name = "领取奖励", enabled = true, config = AwardConfig())),
            context = TaskStartContext(mode = TaskStartMode.MANUAL),
        )

        assertEquals(
            TaskStartDecision.RequiresConfirmation(
                reason = TaskStartDecisionReason.GAME_NOT_INSTALLED,
                acknowledgement = TaskStartAcknowledgement.GAME_NOT_INSTALLED,
            ),
            result
        )
    }

    @Test
    fun scheduledStart_blocked_whenGameNotInstalled() = runBlocking {
        val useCase = PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = analyzeTaskChainUseCase,
            appAliveChecker = FakeAppAliveChecker(AppAliveStatus.ALIVE),
            appSettings = appSettings,
            isPackageInstalled = { false },
        )

        val result = useCase(
            chain = listOf(TaskChainNode(name = "领取奖励", enabled = true, config = AwardConfig())),
            context = TaskStartContext(mode = TaskStartMode.SCHEDULED),
        )

        assertEquals(
            TaskStartDecision.Blocked(
                reason = TaskStartDecisionReason.GAME_NOT_INSTALLED,
            ),
            result
        )
    }

    @Test
    fun acknowledgedGameNotInstalled_skipsInstallCheck() = runBlocking {
        val useCase = PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = analyzeTaskChainUseCase,
            appAliveChecker = FakeAppAliveChecker(AppAliveStatus.ALIVE),
            appSettings = appSettings,
            isPackageInstalled = { false },
        )

        val result = useCase(
            chain = listOf(TaskChainNode(name = "领取奖励", enabled = true, config = AwardConfig())),
            context = TaskStartContext(
                mode = TaskStartMode.MANUAL,
                acknowledgements = setOf(TaskStartAcknowledgement.GAME_NOT_INSTALLED),
            ),
        )

        assertTrue(result is TaskStartDecision.Ready)
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
