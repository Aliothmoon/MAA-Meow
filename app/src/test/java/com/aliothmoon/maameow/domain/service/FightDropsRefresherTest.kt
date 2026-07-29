package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ItemInfo
import com.aliothmoon.maameow.domain.models.DropTarget
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 目标库存运行时重算契约。
 * 对齐上游 FightSettingsUserControlModel.RefreshFightTaskDrops。
 */
class FightDropsRefresherTest {

    private val depotRepository: DepotRepository = mockk()
    private val itemHelper: ItemHelper = mockk()
    private lateinit var refresher: FightDropsRefresher

    @Before
    fun setUp() {
        every { itemHelper.getItemInfo(ITEM) } returns ItemInfo(id = ITEM, name = "源岩")
        every { itemHelper.getItemInfo(match { it != ITEM }) } returns null
        refresher = FightDropsRefresher(depotRepository, itemHelper)
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

    private fun captureParams(
        taskId: Int = 1,
        inventory: Map<String, Int> = emptyMap(),
    ): Pair<FightDropsRefresher.RefreshOutcome, String?> {
        every { depotRepository.countOf(any()) } answers { inventory[firstArg()] ?: 0 }
        var captured: String? = null
        val outcome = refresher.onTaskStarted(taskId) { id, params ->
            assertEquals(taskId, id)
            captured = params
            true
        }
        return outcome to captured
    }

    @Test
    fun unregisteredTaskId_isSkipped() {
        var called = false
        val outcome = refresher.onTaskStarted(99) { _, _ ->
            called = true
            true
        }
        assertEquals(FightDropsRefresher.RefreshOutcome.Skipped, outcome)
        assertFalse(called)
    }

    @Test
    fun needPositive_updatesDropsToDeficit() {
        refresher.register(1, target(dropCount = 100))
        val (outcome, params) = captureParams(inventory = mapOf(ITEM to 30))
        val json = Json.parseToJsonElement(params!!).jsonObject

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
    }

    @Test
    fun needZero_setsTimesToZero() {
        refresher.register(1, target(dropCount = 100))
        val (outcome, params) = captureParams(inventory = mapOf(ITEM to 100))
        val json = Json.parseToJsonElement(params!!).jsonObject

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
        refresher.register(1, target(dropCount = 50))
        val (outcome, params) = captureParams(inventory = mapOf(ITEM to 80))
        val json = Json.parseToJsonElement(params!!).jsonObject

        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Sufficient)
        assertEquals(0, json["times"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, json["drops"]!!.jsonObject[ITEM]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun missingInventory_treatedAsZero() {
        refresher.register(1, target(dropCount = 40))
        val (outcome, params) = captureParams(inventory = emptyMap())
        val json = Json.parseToJsonElement(params!!).jsonObject

        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Updated)
        assertEquals(40, (outcome as FightDropsRefresher.RefreshOutcome.Updated).need)
        assertEquals(40, json["drops"]!!.jsonObject[ITEM]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun blankDropId_isSkipped() {
        refresher.register(1, target(dropId = ""))
        var called = false
        val outcome = refresher.onTaskStarted(1) { _, _ ->
            called = true
            true
        }
        assertEquals(FightDropsRefresher.RefreshOutcome.Skipped, outcome)
        assertFalse(called)
    }

    @Test
    fun nonPositiveDropCount_isSkipped() {
        refresher.register(1, target(dropCount = 0))
        var called = false
        val outcome = refresher.onTaskStarted(1) { _, _ ->
            called = true
            true
        }
        assertEquals(FightDropsRefresher.RefreshOutcome.Skipped, outcome)
        assertFalse(called)
    }

    @Test
    fun clear_removesRegisteredTargets() {
        refresher.register(1, target())
        refresher.clear()
        var called = false
        val outcome = refresher.onTaskStarted(1) { _, _ ->
            called = true
            true
        }
        assertEquals(FightDropsRefresher.RefreshOutcome.Skipped, outcome)
        assertFalse(called)
    }

    @Test
    fun setTaskParamsFailure_stillReportsOutcome() {
        every { depotRepository.countOf(any()) } returns 0
        refresher.register(1, target(dropCount = 10))
        val outcome = refresher.onTaskStarted(1) { _, _ -> false }
        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Updated)
        assertFalse((outcome as FightDropsRefresher.RefreshOutcome.Updated).applied)
    }

    @Test
    fun setTaskParamsFailure_onSufficient_stillReportsAppliedFalse() {
        every { depotRepository.countOf(any()) } returns 100
        refresher.register(1, target(dropCount = 50))
        val outcome = refresher.onTaskStarted(1) { _, _ -> false }
        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Sufficient)
        assertFalse((outcome as FightDropsRefresher.RefreshOutcome.Sufficient).applied)
    }

    @Test
    fun refreshJson_preservesExpireDaysAndDrGrandet() {
        refresher.register(
            1,
            target(dropCount = 100).copy(medicineExpireDays = 3, drGrandet = true),
        )
        val (_, params) = captureParams(inventory = mapOf(ITEM to 10))
        val json = Json.parseToJsonElement(params!!).jsonObject
        assertEquals(3, json["medicine_expire_days"]!!.jsonPrimitive.content.toInt())
        assertTrue(json["DrGrandet"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun unknownItemName_fallsBackToId() {
        every { depotRepository.countOf(any()) } returns 5
        refresher.register(1, target(dropId = "99999", dropCount = 1))
        val outcome = refresher.onTaskStarted(1) { _, _ -> true }
        assertTrue(outcome is FightDropsRefresher.RefreshOutcome.Sufficient)
        assertEquals("99999", (outcome as FightDropsRefresher.RefreshOutcome.Sufficient).dropName)
    }

    private companion object {
        const val STAGE = "1-7"
        const val ITEM = "30011"
    }
}
