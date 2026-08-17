package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ItemInfo
import com.aliothmoon.maameow.data.resource.StageApCostHelper
import com.aliothmoon.maameow.domain.models.DropTarget
import com.aliothmoon.maameow.maa.callback.SubTaskHandler
import com.aliothmoon.maameow.maa.task.TaskSlot
import com.aliothmoon.maameow.manager.RemoteServiceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 目标库存运行时重算契约。
 * 对齐上游 FightSettingsUserControlModel.RefreshFightTaskDrops。
 *
 * 登记分 stage(TaskSlot) + bind(TaskSlot, taskId)；单测用 [stageAndBind] 一步完成。
 * SetTaskParams 经 [RemoteServiceManager] 下发。
 */
class FightDropsRefresherTest {

    private val depotRepository: DepotRepository = mockk()
    private val itemHelper: ItemHelper = mockk()
    private val stageApCostHelper: StageApCostHelper = mockk()
    private val subTaskHandler: SubTaskHandler = mockk()
    private val remoteService: RemoteService = mockk()
    private val maaCore: MaaCoreService = mockk()
    private lateinit var refresher: FightDropsRefresher

    /** 最近一次 SetTaskParams 的 JSON；未调用则为 null */
    private var lastParamsJson: String? = null

    @Before
    fun setUp() {
        every { itemHelper.getItemInfo(ITEM) } returns ItemInfo(id = ITEM, name = "源岩")
        every { itemHelper.getItemInfo(match { it != ITEM }) } returns null

        mockkObject(RemoteServiceManager)
        every { RemoteServiceManager.getInstanceOrNull() } returns remoteService
        every { remoteService.maaCoreService } returns maaCore
        every { maaCore.SetTaskParams(any(), any()) } answers {
            lastParamsJson = secondArg()
            true
        }

        // 默认无理智快照 → 理智判定整体关闭，老用例行为不变
        every { subTaskHandler.lastSanitySnapshot } returns null
        every { stageApCostHelper.getApCost(any()) } returns null

        refresher = FightDropsRefresher(
            depotRepository,
            itemHelper,
            stageApCostHelper,
            subTaskHandler,
        )
        lastParamsJson = null
    }

    /** 无药剂/源石预算的目标，理智判定才会介入 */
    private fun budgetlessTarget(dropCount: Int = 100, expireDays: Int? = null) =
        target(dropCount = dropCount, medicine = 0, stone = 0)
            .copy(medicineExpireDays = expireDays)

    private fun sanity(current: Int, max: Int = 135, reportedMinutesAgo: Long = 0) {
        every { subTaskHandler.lastSanitySnapshot } returns SubTaskHandler.SanitySnapshot(
            current = current,
            max = max,
            reportTimeMillis = System.currentTimeMillis() - reportedMinutesAgo * 60_000,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(RemoteServiceManager)
    }

    private fun target(
        dropId: String = ITEM,
        dropCount: Int = 100,
        stage: String = STAGE,
        medicine: Int = 3,
        stone: Int = 1,
        series: Int = 1,
        logLabel: String = "1",
    ) = DropTarget(dropId, dropCount, stage, medicine, stone, series, logLabel)

    /** 模拟 Analyze stage + Composition bind */
    private fun stageAndBind(
        taskId: Int,
        target: DropTarget,
        nodeId: String = NODE,
        index: Int = 0,
    ) {
        val slot = TaskSlot(nodeId, index)
        refresher.stage(slot, target)
        refresher.bind(slot, taskId)
    }

    private fun withInventory(inventory: Map<String, Int>) {
        every { depotRepository.countOf(any()) } answers { inventory[firstArg()] ?: 0 }
    }

    @Test
    fun unregisteredTaskId_isSkipped() {
        val outcome = refresher.onTaskStarted(99)
        assertEquals(FightDropsRefresher.RefreshOutcome.Skipped, outcome)
        verify(exactly = 0) { maaCore.SetTaskParams(any(), any()) }
    }

    @Test
    fun stageWithoutBind_isSkipped() {
        refresher.stage(TaskSlot(NODE, 0), target())
        val outcome = refresher.onTaskStarted(1)
        assertEquals(FightDropsRefresher.RefreshOutcome.Skipped, outcome)
        verify(exactly = 0) { maaCore.SetTaskParams(any(), any()) }
    }

    @Test
    fun bindWithoutStage_isNoOp() {
        refresher.bind(TaskSlot(NODE, 0), 1)
        val outcome = refresher.onTaskStarted(1)
        assertEquals(FightDropsRefresher.RefreshOutcome.Skipped, outcome)
    }

    @Test
    fun sameNodeDifferentIndex_doNotCollide() {
        stageAndBind(1, target(dropCount = 100), index = 0)
        stageAndBind(2, target(dropCount = 50), index = 1)
        withInventory(mapOf(ITEM to 0))

        val o1 = refresher.onTaskStarted(1) as FightDropsRefresher.RefreshOutcome.Updated
        val o2 = refresher.onTaskStarted(2) as FightDropsRefresher.RefreshOutcome.Updated
        assertEquals(100, o1.need)
        assertEquals(50, o2.need)
    }

    @Test
    fun needPositive_updatesDropsToDeficit() {
        stageAndBind(1, target(dropCount = 100))
        withInventory(mapOf(ITEM to 30))
        val outcome = refresher.onTaskStarted(1)
        val json = Json.parseToJsonElement(lastParamsJson!!).jsonObject

        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Updated)
        val updated = outcome as FightDropsRefresher.RefreshOutcome.Updated
        assertEquals(70, updated.need)
        assertEquals(30, updated.current)
        assertEquals(100, updated.target)
        assertTrue(updated.applied)

        assertEquals(Int.MAX_VALUE, json["times"]!!.jsonPrimitive.content.toInt())
        assertEquals(70, json["drops"]!!.jsonObject[ITEM]!!.jsonPrimitive.content.toInt())
        assertEquals(STAGE, json["stage"]!!.jsonPrimitive.content)
        assertEquals(3, json["medicine"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, json["stone"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, json["series"]!!.jsonPrimitive.content.toInt())
        verify(exactly = 1) { maaCore.SetTaskParams(1, any()) }
    }

    @Test
    fun needZero_setsTimesToZero() {
        stageAndBind(1, target(dropCount = 100))
        withInventory(mapOf(ITEM to 100))
        val outcome = refresher.onTaskStarted(1)
        val json = Json.parseToJsonElement(lastParamsJson!!).jsonObject

        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Sufficient)
        val sufficient = outcome as FightDropsRefresher.RefreshOutcome.Sufficient
        assertEquals(100, sufficient.current)
        assertEquals(100, sufficient.target)
        assertEquals("源岩", sufficient.dropName)
        assertEquals("1", sufficient.logLabel)

        assertEquals(0, json["times"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, json["drops"]!!.jsonObject[ITEM]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun needNegative_alsoSetsTimesToZero() {
        stageAndBind(1, target(dropCount = 50))
        withInventory(mapOf(ITEM to 80))
        refresher.onTaskStarted(1)
        val json = Json.parseToJsonElement(lastParamsJson!!).jsonObject

        assertEquals(0, json["times"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, json["drops"]!!.jsonObject[ITEM]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun missingInventory_treatedAsZero() {
        stageAndBind(1, target(dropCount = 40))
        withInventory(emptyMap())
        val outcome = refresher.onTaskStarted(1)
        val json = Json.parseToJsonElement(lastParamsJson!!).jsonObject

        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Updated)
        assertEquals(40, (outcome as FightDropsRefresher.RefreshOutcome.Updated).need)
        assertEquals(40, json["drops"]!!.jsonObject[ITEM]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun blankDropId_isSkipped() {
        stageAndBind(1, target(dropId = ""))
        val outcome = refresher.onTaskStarted(1)
        assertEquals(FightDropsRefresher.RefreshOutcome.Skipped, outcome)
        verify(exactly = 0) { maaCore.SetTaskParams(any(), any()) }
    }

    @Test
    fun nonPositiveDropCount_isSkipped() {
        stageAndBind(1, target(dropCount = 0))
        val outcome = refresher.onTaskStarted(1)
        assertEquals(FightDropsRefresher.RefreshOutcome.Skipped, outcome)
        verify(exactly = 0) { maaCore.SetTaskParams(any(), any()) }
    }

    @Test
    fun clear_removesStagedAndBound() {
        stageAndBind(1, target())
        refresher.clear()
        val outcome = refresher.onTaskStarted(1)
        assertEquals(FightDropsRefresher.RefreshOutcome.Skipped, outcome)
        verify(exactly = 0) { maaCore.SetTaskParams(any(), any()) }
    }

    @Test
    fun setTaskParamsFailure_stillReportsOutcome() {
        every { depotRepository.countOf(any()) } returns 0
        every { maaCore.SetTaskParams(any(), any()) } returns false
        stageAndBind(1, target(dropCount = 10))
        val outcome = refresher.onTaskStarted(1)
        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Updated)
        assertFalse((outcome as FightDropsRefresher.RefreshOutcome.Updated).applied)
    }

    @Test
    fun setTaskParamsFailure_onSufficient_stillReportsAppliedFalse() {
        every { depotRepository.countOf(any()) } returns 100
        every { maaCore.SetTaskParams(any(), any()) } returns false
        stageAndBind(1, target(dropCount = 50))
        val outcome = refresher.onTaskStarted(1)
        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Sufficient)
        assertFalse((outcome as FightDropsRefresher.RefreshOutcome.Sufficient).applied)
    }

    @Test
    fun serviceUnavailable_reportsAppliedFalse() {
        every { depotRepository.countOf(any()) } returns 0
        every { RemoteServiceManager.getInstanceOrNull() } returns null
        stageAndBind(1, target(dropCount = 10))
        val outcome = refresher.onTaskStarted(1)
        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Updated)
        assertFalse((outcome as FightDropsRefresher.RefreshOutcome.Updated).applied)
        verify(exactly = 0) { maaCore.SetTaskParams(any(), any()) }
    }

    @Test
    fun refreshJson_preservesExpireDaysAndDrGrandet() {
        stageAndBind(
            1,
            target(dropCount = 100).copy(medicineExpireDays = 3, drGrandet = true),
        )
        withInventory(mapOf(ITEM to 10))
        refresher.onTaskStarted(1)
        val json = Json.parseToJsonElement(lastParamsJson!!).jsonObject
        assertEquals(3, json["medicine_expire_days"]!!.jsonPrimitive.content.toInt())
        assertTrue(json["DrGrandet"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun unknownItemName_fallsBackToId() {
        every { depotRepository.countOf(any()) } returns 5
        stageAndBind(1, target(dropId = "99999", dropCount = 1))
        val outcome = refresher.onTaskStarted(1)
        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Sufficient)
        assertEquals("99999", (outcome as FightDropsRefresher.RefreshOutcome.Sufficient).dropName)
    }

    // ==================== 理智不足跳过 ====================

    @Test
    fun sanityBelowApCost_skipsWholeTask() {
        every { stageApCostHelper.getApCost(STAGE) } returns 21
        sanity(current = 10)
        stageAndBind(1, budgetlessTarget())
        withInventory(mapOf(ITEM to 0))

        val outcome = refresher.onTaskStarted(1)
        val json = Json.parseToJsonElement(lastParamsJson!!).jsonObject

        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.SanityInsufficient)
        val skipped = outcome as FightDropsRefresher.RefreshOutcome.SanityInsufficient
        // 回复量向上取整，过了 1ms 也记 +1 点，故不钉死具体值
        assertTrue("${skipped.estimatedSanity}", skipped.estimatedSanity in 10..11)
        assertEquals(21, skipped.apCost)
        assertEquals(0, json["times"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun sanityAboveApCost_runsNormally() {
        every { stageApCostHelper.getApCost(STAGE) } returns 21
        sanity(current = 30)
        stageAndBind(1, budgetlessTarget())
        withInventory(mapOf(ITEM to 0))

        assertTrue(refresher.onTaskStarted(1) is FightDropsRefresher.RefreshOutcome.Updated)
    }

    @Test
    fun naturalRegenIsEstimated_sixMinutesPerPoint() {
        every { stageApCostHelper.getApCost(STAGE) } returns 21
        // 10 点 + 120 分钟 ≈ 30 点，够打
        sanity(current = 10, reportedMinutesAgo = 120)
        stageAndBind(1, budgetlessTarget())
        withInventory(mapOf(ITEM to 0))

        assertTrue(refresher.onTaskStarted(1) is FightDropsRefresher.RefreshOutcome.Updated)
    }

    @Test
    fun regenIsCappedAtMax() {
        every { stageApCostHelper.getApCost(STAGE) } returns 200
        sanity(current = 10, max = 135, reportedMinutesAgo = 100_000)
        stageAndBind(1, budgetlessTarget())
        withInventory(mapOf(ITEM to 0))

        val outcome = refresher.onTaskStarted(1)
        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.SanityInsufficient)
        assertEquals(
            135,
            (outcome as FightDropsRefresher.RefreshOutcome.SanityInsufficient).estimatedSanity,
        )
    }

    @Test
    fun medicineBudget_disablesSanitySkip() {
        every { stageApCostHelper.getApCost(STAGE) } returns 21
        sanity(current = 0)
        stageAndBind(1, target(dropCount = 100, medicine = 3, stone = 0))
        withInventory(mapOf(ITEM to 0))

        assertTrue(refresher.onTaskStarted(1) is FightDropsRefresher.RefreshOutcome.Updated)
    }

    @Test
    fun unknownApCost_disablesSanitySkip() {
        every { stageApCostHelper.getApCost(STAGE) } returns null
        sanity(current = 0)
        stageAndBind(1, budgetlessTarget())
        withInventory(mapOf(ITEM to 0))

        assertTrue(refresher.onTaskStarted(1) is FightDropsRefresher.RefreshOutcome.Updated)
    }

    @Test
    fun noSanitySnapshot_disablesSanitySkip() {
        every { stageApCostHelper.getApCost(STAGE) } returns 21
        stageAndBind(1, budgetlessTarget())
        withInventory(mapOf(ITEM to 0))

        assertTrue(refresher.onTaskStarted(1) is FightDropsRefresher.RefreshOutcome.Updated)
    }

    @Test
    fun expiringMedicineWindow_blocksSkipUntilProvenExhausted() {
        every { stageApCostHelper.getApCost(STAGE) } returns 21
        sanity(current = 0)
        withInventory(mapOf(ITEM to 0))

        // 还没有任何任务证明 2 天窗口内的临期药已用完 → 照常进图
        stageAndBind(1, budgetlessTarget(expireDays = 2))
        assertTrue(refresher.onTaskStarted(1) is FightDropsRefresher.RefreshOutcome.Updated)

        // 该任务未达标就正常结束 → 窗口被证明耗尽
        refresher.onTaskCompleted(1)

        stageAndBind(2, budgetlessTarget(expireDays = 2), index = 1)
        assertTrue(
            refresher.onTaskStarted(2) is FightDropsRefresher.RefreshOutcome.SanityInsufficient
        )
    }

    @Test
    fun completedTaskReachingTarget_doesNotProveExhaustion() {
        every { stageApCostHelper.getApCost(STAGE) } returns 21
        sanity(current = 0)
        stageAndBind(1, budgetlessTarget(expireDays = 2))

        withInventory(mapOf(ITEM to 100))
        refresher.onTaskCompleted(1)

        withInventory(mapOf(ITEM to 0))
        stageAndBind(2, budgetlessTarget(expireDays = 2), index = 1)
        assertTrue(refresher.onTaskStarted(2) is FightDropsRefresher.RefreshOutcome.Updated)
    }

    @Test
    fun clear_resetsProvenExhaustedWindow() {
        every { stageApCostHelper.getApCost(STAGE) } returns 21
        sanity(current = 0)
        withInventory(mapOf(ITEM to 0))
        stageAndBind(1, budgetlessTarget(expireDays = 2))
        refresher.onTaskCompleted(1)

        refresher.clear()

        stageAndBind(2, budgetlessTarget(expireDays = 2), index = 1)
        assertTrue(refresher.onTaskStarted(2) is FightDropsRefresher.RefreshOutcome.Updated)
    }

    private companion object {
        const val STAGE = "1-7"
        const val ITEM = "30011"
        const val NODE = "node-a"
    }
}
