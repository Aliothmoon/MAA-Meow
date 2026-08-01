package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.model.AwardConfig
import com.aliothmoon.maameow.data.model.DepotMaintainConfig
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.RoguelikeConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.UserDataUpdateConfig
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.DepotSnapshot
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.repository.OperBoxSnapshot
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.models.SeriesLock
import com.aliothmoon.maameow.maa.task.MaaTaskType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeTaskChainUseCaseTest {

    private val taskChainState = mockk<TaskChainState> {
        every { clientType } returns "Official"
    }
    private val resourceDataManager = mockk<ResourceDataManager>(relaxed = true)
    private val operBoxRepository = mockk<OperBoxRepository>(relaxed = true) {
        every { snapshot } returns MutableStateFlow(OperBoxSnapshot())
        every { isLoaded } returns MutableStateFlow(true)
    }
    private val depotRepository = mockk<DepotRepository>(relaxed = true) {
        every { snapshot } returns MutableStateFlow(DepotSnapshot())
        // isLoaded 必须显式给 —— relaxed mock 的 StateFlow 不会 emit，
        // 分析阶段开头的 `isLoaded.first { it }` 会挂死
        every { isLoaded } returns MutableStateFlow(true)
    }
    private val useCase = AnalyzeTaskChainUseCase(
        taskChainState = taskChainState,
        resourceDataManager = resourceDataManager,
        activityManager = mockk(relaxed = true),
        depotRepository = depotRepository,
        operBoxRepository = operBoxRepository,
        itemHelper = mockk(relaxed = true),
        dropsRefresher = mockk(relaxed = true),
    )

    @Test
    fun returnsBlocked_whenNoTaskIsEnabled() = runBlocking {
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
    fun returnsBlocked_whenWakeUpClientTypesConflict() = runBlocking {
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
    fun returnsBlocked_whenWeeklyScheduleFiltersOutAllTasks() = runBlocking {
        // SeriesLock 按日期生效（2026-08-01 起对官服/B服返回 true），会向预检日志追加警告；
        // mock 掉避免断言结果随墙钟日期变化
        mockkObject(SeriesLock)
        try {
            every { SeriesLock.isLocked(any(), any()) } returns false
            every { SeriesLock.isLocked(any()) } returns false

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
        } finally {
            unmockkObject(SeriesLock)
        }
    }

    @Test
    fun returnsReadyPlan_withClientTypePackageAndLaunchFlag() = runBlocking {
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
        assertEquals(2, ready.plan.nodes.size)
        assertEquals(2, ready.plan.params.size)
    }

    @Test
    fun returnsReadyPlan_withDefaultClientType_whenNoWakeUpTaskExists() = runBlocking {
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

    @Test
    fun roguelikeCoreChar_normalizedToSimplifiedChinese_beforeDispatch() = runBlocking {
        // 繁中服选了繁中名,下发前须反查归一化为简中名(MaaCore core_char 仅认简中名)
        every { resourceDataManager.getCharacterByNameOrAlias("維什戴爾") } returns
            CharacterInfo(name = "维什戴尔")

        val result = useCase(
            listOf(
                TaskChainNode(
                    name = "自动肉鸽",
                    enabled = true,
                    config = RoguelikeConfig(coreChar = "維什戴爾"),
                )
            )
        )

        val ready = result as AnalyzeTaskChainResult.Ready
        val roguelikeParams = ready.plan.params.first { it.type == MaaTaskType.ROGUELIKE }
        val coreChar = Json.parseToJsonElement(roguelikeParams.params)
            .jsonObject["core_char"]?.jsonPrimitive?.content

        assertEquals("维什戴尔", coreChar)
    }

    @Test
    fun userDataUpdate_neverSynced_expandsToBothRecognitions() = runBlocking {
        val result = useCase(
            listOf(
                TaskChainNode(
                    name = "更新数据",
                    enabled = true,
                    config = UserDataUpdateConfig(),
                )
            )
        )

        val ready = result as AnalyzeTaskChainResult.Ready
        assertEquals(
            listOf(MaaTaskType.OPER_BOX, MaaTaskType.DEPOT),
            ready.plan.params.map { it.type },
        )
    }

    /**
     * 「更新数据」与「库存保持（任务开始前更新库存）」相邻时只保留一次仓库识别；
     * 中间隔着别的任务则不合并 —— 那段时间库存确实可能变化。
     */
    @Test
    fun adjacentDepotRecognitions_areDeduplicated() = runBlocking {
        val result = useCase(
            listOf(
                TaskChainNode(
                    name = "更新数据",
                    enabled = true,
                    order = 0,
                    config = UserDataUpdateConfig(updateOperBox = false, updateDepot = true),
                ),
                TaskChainNode(
                    name = "库存保持",
                    enabled = true,
                    order = 1,
                    config = DepotMaintainConfig(updateDepot = true, plans = emptyList()),
                ),
            )
        )

        val ready = result as AnalyzeTaskChainResult.Ready
        assertEquals(listOf(MaaTaskType.DEPOT), ready.plan.params.map { it.type })
    }

    @Test
    fun nonAdjacentDepotRecognitions_areKept() = runBlocking {
        val result = useCase(
            listOf(
                TaskChainNode(
                    name = "更新数据",
                    enabled = true,
                    order = 0,
                    config = UserDataUpdateConfig(updateOperBox = false, updateDepot = true),
                ),
                TaskChainNode(name = "领取奖励", enabled = true, order = 1, config = AwardConfig()),
                TaskChainNode(
                    name = "库存保持",
                    enabled = true,
                    order = 2,
                    config = DepotMaintainConfig(updateDepot = true, plans = emptyList()),
                ),
            )
        )

        val ready = result as AnalyzeTaskChainResult.Ready
        assertEquals(
            listOf(MaaTaskType.DEPOT, MaaTaskType.AWARD, MaaTaskType.DEPOT),
            ready.plan.params.map { it.type },
        )
    }
}
