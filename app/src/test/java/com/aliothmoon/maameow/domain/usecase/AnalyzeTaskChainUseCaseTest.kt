package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.model.AwardConfig
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.preferences.TaskChainState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeTaskChainUseCaseTest {

    private val taskChainState = mockk<TaskChainState> {
        every { getClientType() } returns "Official"
    }
    private val useCase = AnalyzeTaskChainUseCase(taskChainState)

    @Test
    fun returnsBlocked_whenNoTaskIsEnabled() {
        val result = useCase(
            listOf(TaskChainNode(name = "领取奖励", enabled = false, config = AwardConfig()))
        )

        assertEquals(
            AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.NO_TASK_SELECTED,
            ),
            result
        )
    }

    @Test
    fun returnsBlocked_whenWakeUpClientTypesConflict() {
        val result = useCase(
            listOf(
                TaskChainNode(
                    name = "开始唤醒1",
                    order = 1,
                    enabled = true,
                    config = WakeUpConfig(clientType = "Official"),
                ),
                TaskChainNode(
                    name = "开始唤醒2",
                    order = 2,
                    enabled = true,
                    config = WakeUpConfig(clientType = "Bilibili"),
                ),
            )
        )

        assertEquals(
            AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.CONFLICTING_CLIENT_TYPES,
                clientTypes = listOf("Official", "Bilibili"),
            ),
            result
        )
    }

    @Test
    fun returnsBlocked_whenWeeklyScheduleFiltersOutAllTasks() {
        val disabledEveryDay = mapOf(
            "MONDAY" to false,
            "TUESDAY" to false,
            "WEDNESDAY" to false,
            "THURSDAY" to false,
            "FRIDAY" to false,
            "SATURDAY" to false,
            "SUNDAY" to false,
        )

        val result = useCase(
            listOf(
                TaskChainNode(
                    name = "理智作战",
                    enabled = true,
                    config = FightConfig(
                        useWeeklySchedule = true,
                        weeklySchedule = disabledEveryDay,
                    ),
                )
            )
        )

        assertEquals(
            AnalyzeTaskChainResult.Blocked(
                reason = AnalyzeTaskChainFailureReason.NO_EXECUTABLE_TASKS,
            ),
            result
        )
    }

    @Test
    fun returnsReadyPlan_withClientTypePackageAndLaunchFlag() {
        val result = useCase(
            listOf(
                TaskChainNode(
                    name = "领取奖励",
                    order = 2,
                    enabled = true,
                    config = AwardConfig(),
                ),
                TaskChainNode(
                    name = "开始唤醒",
                    order = 1,
                    enabled = true,
                    config = WakeUpConfig(
                        clientType = "Official",
                        startGameEnabled = true,
                    ),
                ),
            )
        )

        val ready = result as AnalyzeTaskChainResult.Ready
        assertEquals("Official", ready.plan.clientType)
        assertEquals(Packages["Official"], ready.plan.gamePackageName)
        assertTrue(ready.plan.launchesGame)
        assertEquals(2, ready.plan.enabledNodes.size)
        assertEquals(2, ready.plan.params.size)
    }

    @Test
    fun returnsReadyPlan_withDefaultClientType_whenNoWakeUpTaskExists() {
        val result = useCase(
            listOf(
                TaskChainNode(
                    name = "领取奖励",
                    enabled = true,
                    config = AwardConfig(),
                )
            )
        )

        val ready = result as AnalyzeTaskChainResult.Ready
        assertEquals("Official", ready.plan.clientType)
        assertEquals(Packages["Official"], ready.plan.gamePackageName)
        assertFalse(ready.plan.launchesGame)
        assertEquals(1, ready.plan.params.size)
    }
}
