package com.aliothmoon.maameow.presentation.view.panel.depot

import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.DepotPlanOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 覆盖后台任务那侧计划概览用到的纯函数：[depotPlanOrderEntries]。
 *
 * 行怎么画、拖动手势本身在 Compose 里，测不了；这一块是拖动前后真正会算错的地方 ——
 * 序号错一位就和下面的计划格子对不上。
 */
class DepotPlanOrderListTest {

    // ========== 计划 → 列表行 ==========

    /** 序号 1 起、按计划原本的顺序，进度与缺口照算 */
    @Test
    fun depotPlanOrderEntries_numbersFromOneAndKeepsPlanOrder() {
        val entries = depotPlanOrderEntries(
            plans = listOf(
                DepotMaintainPlan(stage = "1-7", dropId = "30012", dropCount = 50),
                DepotMaintainPlan(stage = "CE-6", dropId = "30011", dropCount = 800),
            ),
            outcomes = listOf(DepotPlanOutcome.Runnable, DepotPlanOutcome.Runnable),
            inventory = mapOf("30012" to 60, "30011" to 8),
            itemNameMap = mapOf("30012" to "源岩", "30011" to "龙门币"),
            notSelectedLabel = "未选择",
        )

        assertEquals(listOf(1, 2), entries.map { it.no })
        assertEquals(listOf("plan-order-0", "plan-order-1"), entries.map { it.key })
        assertEquals(listOf("30012", "30011"), entries.map { it.itemId })
        assertEquals(listOf("源岩", "龙门币"), entries.map { it.itemName })
        assertEquals(listOf(60, 8), entries.map { it.current })
        assertEquals(listOf(50, 800), entries.map { it.target })
    }

    /** 库存已经超过目标的，缺口按 0 算，不能是负数 */
    @Test
    fun depotPlanOrderEntries_clampsNeedAtZero() {
        val entries = depotPlanOrderEntries(
            plans = listOf(
                DepotMaintainPlan(stage = "1-7", dropId = "30012", dropCount = 50),
                DepotMaintainPlan(stage = "CE-6", dropId = "30011", dropCount = 800),
            ),
            outcomes = listOf(DepotPlanOutcome.Enough, DepotPlanOutcome.Runnable),
            inventory = mapOf("30012" to 60, "30011" to 8),
            itemNameMap = emptyMap(),
            notSelectedLabel = "未选择",
        )

        assertEquals(listOf(0, 792), entries.map { it.need })
    }

    /**
     * 没选物品的计划也照样占一行。
     *
     * 执行侧会跳过它，但列表少一行的话，下面的序号就和计划卡对不上了 ——
     * 库存数据页的刷取顺序可以先滤掉，这里不行。
     */
    @Test
    fun depotPlanOrderEntries_keepsUnselectedPlan() {
        val entries = depotPlanOrderEntries(
            plans = listOf(
                DepotMaintainPlan(stage = "1-7", dropId = "30012", dropCount = 50),
                DepotMaintainPlan(stage = "", dropId = "", dropCount = 0),
            ),
            outcomes = listOf(DepotPlanOutcome.Runnable, DepotPlanOutcome.NoItem),
            inventory = mapOf("30012" to 60),
            itemNameMap = mapOf("30012" to "源岩"),
            notSelectedLabel = "未选择",
        )

        assertEquals(2, entries.size)
        assertEquals(listOf(1, 2), entries.map { it.no })
        assertEquals("未选择", entries[1].itemName)
        assertEquals(DepotPlanOutcome.NoItem, entries[1].outcome)
    }

    /**
     * 认不出来的物品按 0 计，缺口按满额算。
     *
     * 名字映射用的是全表（老计划里可能存着已经不在识别集合里的 id），真查不到时按「未选择」
     * 兜底 —— 宁可说不清是什么，也不能把内部 ID 摆到用户面前。
     */
    @Test
    fun depotPlanOrderEntries_readsUnknownItemAsZero() {
        val entries = depotPlanOrderEntries(
            plans = listOf(DepotMaintainPlan(stage = "1-7", dropId = "99999", dropCount = 30)),
            outcomes = listOf(DepotPlanOutcome.Runnable),
            inventory = emptyMap(),
            itemNameMap = emptyMap(),
            notSelectedLabel = "未选择",
        )

        assertEquals("未选择", entries.single().itemName)
        assertEquals(0, entries.single().current)
        assertEquals(30, entries.single().need)
    }
}
