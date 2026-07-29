package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.data.model.AwardConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.AppAliveChecker
import com.aliothmoon.maameow.remote.AppAliveStatus
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 关注「链分析 + 就绪性闸门」的组合与决策映射；闸门自身的判定矩阵见 [CheckGameReadinessUseCaseTest]。
 */
class PrepareTaskStartUseCaseTest {

    private val taskChainState = mockk<TaskChainState> {
        every { getClientType() } returns "Official"
    }
    private val resourceDataManager = mockk<ResourceDataManager>(relaxed = true)
    private val analyzeTaskChainUseCase = AnalyzeTaskChainUseCase(
        taskChainState = taskChainState,
        resourceDataManager = resourceDataManager,
        activityManager = mockk(relaxed = true),
        depotRepository = mockk(relaxed = true),
        operBoxRepository = mockk(relaxed = true),
        itemHelper = mockk(relaxed = true),
    )

    private fun useCase(aliveStatus: Int) = PrepareTaskStartUseCase(
        analyzeTaskChainUseCase = analyzeTaskChainUseCase,
        checkGameReadiness = CheckGameReadinessUseCase(
            appAliveChecker = FakeAppAliveChecker(aliveStatus),
            appSettings = mockk<AppSettingsManager> {
                every { runMode } returns MutableStateFlow(RunMode.BACKGROUND)
            },
            achievementReporter = mockk<AchievementReporter>(relaxed = true),
        ),
    )

    @Test
    fun analysisFailure_isForwardedAsBlockedDecision() = runBlocking {
        val result = useCase(AppAliveStatus.ALIVE)(
            chain = emptyList(),
            context = TaskStartContext(mode = TaskStartMode.MANUAL),
        )

        assertEquals(
            TaskStartDecision.Blocked(reason = TaskStartDecisionReason.NO_TASK_SELECTED),
            result
        )
    }

    @Test
    fun readyDecision_carriesGameAliveStatusOntoPlan() = runBlocking {
        // 含「开始唤醒(拉起游戏)」⇒ launchesGame=true ⇒ 闸门跳过存活检查但记录存活状态
        val result = useCase(AppAliveStatus.DEAD)(
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
        assertEquals(false, (result as TaskStartDecision.Ready).plan.gameAliveBeforeStart)
    }

    @Test
    fun requiresConfirmation_isForwardedFromReadinessGate() = runBlocking {
        val result = useCase(AppAliveStatus.DEAD)(
            chain = listOf(TaskChainNode(name = "领取奖励", enabled = true, config = AwardConfig())),
            context = TaskStartContext(mode = TaskStartMode.MANUAL),
        )

        assertEquals(
            TaskStartDecision.RequiresConfirmation(
                acknowledgement = TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
            ),
            result
        )
    }

    private class FakeAppAliveChecker(private val status: Int) : AppAliveChecker {
        override suspend fun isAppAlive(packageName: String): Int = status
        override suspend fun isAppOnBackgroundDisplay(packageName: String): Boolean? = null
    }
}
