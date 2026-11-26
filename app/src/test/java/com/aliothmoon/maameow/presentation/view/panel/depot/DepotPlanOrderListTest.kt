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

        assertEquals(listOf("plan-30012-0", "plan-30011-0"), entries.map { it.key })
        assertEquals(listOf(1, 2), entries.map { it.no })
        assertEquals(listOf("1-7", "CE-6"), entries.map { it.stage })
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

    @Test
    fun depotPlanOrderEntries_keysStayStableOnReorderAndDisambiguateDuplicates() {
        val a = DepotMaintainPlan(stage = "1-7", dropId = "30012", dropCount = 50)
        val b = DepotMaintainPlan(stage = "CE-6", dropId = "4001", dropCount = 80000)
        val aDup = DepotMaintainPlan(stage = "1-7", dropId = "30012", dropCount = 80)

        val before = depotPlanOrderEntries(
            plans = listOf(a, b, aDup),
            outcomes = listOf(DepotPlanOutcome.Runnable, DepotPlanOutcome.Runnable, DepotPlanOutcome.Runnable),
            inventory = emptyMap(),
            itemNameMap = emptyMap(),
            notSelectedLabel = "未选择",
        )
        val after = depotPlanOrderEntries(
            plans = listOf(b, a, aDup),
            outcomes = listOf(DepotPlanOutcome.Runnable, DepotPlanOutcome.Runnable, DepotPlanOutcome.Runnable),
            inventory = emptyMap(),
            itemNameMap = emptyMap(),
            notSelectedLabel = "未选择",
        )

        assertEquals(listOf("plan-30012-0", "plan-4001-0", "plan-30012-1"), before.map { it.key })
        assertEquals(listOf("plan-4001-0", "plan-30012-0", "plan-30012-1"), after.map { it.key })
    }

    @Test
    fun depotPlanOrderEntries_computesDualChipExtraAndFormatsProgress() {
        // 3212 = 先锋芯片组，3213 = 先锋双芯片（每片折算 2 个芯片组）
        val entries = depotPlanOrderEntries(
            plans = listOf(DepotMaintainPlan(stage = "PR-C-2", dropId = "3212", dropCount = 12)),
            outcomes = listOf(DepotPlanOutcome.Runnable),
            inventory = mapOf("3212" to 4, "3213" to 3),
            itemNameMap = mapOf("3212" to "先锋芯片组"),
            notSelectedLabel = "未选择",
            countDualChips = true,
        )

        val entry = entries.single()
        assertEquals(10, entry.current)
        assertEquals(6, entry.dualChipExtra)
        assertEquals(2, entry.need)
        assertEquals("4 (+6) / 12", depotProgressText(entry.current, entry.target, synced = true, entry.dualChipExtra))
        assertEquals("-- / 12", depotProgressText(entry.current, entry.target, synced = false, entry.dualChipExtra))
    }

    @Test
    fun reconcileOrderedPlans_preservesUidsAcrossEditAndDuplicates() {
        var nextId = 10L
        val rock1 = DepotMaintainPlan(stage = "1-7", dropId = "30012", dropCount = 50)
        val rock2 = DepotMaintainPlan(stage = "1-7", dropId = "30012", dropCount = 50)
        val lmd = DepotMaintainPlan(stage = "CE-6", dropId = "4001", dropCount = 50000)
        val prev = listOf(
            IndexedDepotPlan(1L, rock1),
            IndexedDepotPlan(2L, rock2),
            IndexedDepotPlan(3L, lmd),
        )

        // 原地改一条计划的目标数量：沿用原 uid，重复项也互不冲突
        val edited = reconcileOrderedPlans(prev, listOf(rock1, rock2.copy(dropCount = 80), lmd)) { nextId++ }
        assertEquals(listOf(1L, 2L, 3L), edited.map { it.uid })

        // 删除第一条并追加一条新计划：保留剩余项的 uid，仅新项分配新 uid
        val added = DepotMaintainPlan(stage = "AP-5", dropId = "4006", dropCount = 200)
        val afterDeleteAndAdd = reconcileOrderedPlans(prev, listOf(rock2, lmd, added)) { nextId++ }
        assertEquals(listOf(1L, 3L, 10L), afterDeleteAndAdd.map { it.uid })
    }

    @Test
    fun uidAllocator_handlesDeleteToEmptyAndPreservesSeededDragOrder() {
        val allocator = DepotPlanUidAllocator()
        val rock = DepotMaintainPlan(stage = "1-7", dropId = "30012", dropCount = 50)
        val sugar = DepotMaintainPlan(stage = "S3-1", dropId = "30022", dropCount = 40)

        val initial = allocator.reconcile(listOf(rock, sugar))
        assertEquals(listOf(0L, 1L), initial.map { it.uid })

        // 拖拽互换顺序后提前 seed，等落盘回流应沿用原 uid
        val dragged = listOf(initial[1], initial[0])
        allocator.seed(dragged)
        val afterPersist = allocator.reconcile(listOf(sugar, rock))
        assertEquals(listOf(1L, 0L), afterPersist.map { it.uid })

        // 删空后再撤销回来不抛越界且分配新 uid
        assertEquals(emptyList<IndexedDepotPlan>(), allocator.reconcile(emptyList()))
        val restored = allocator.reconcile(listOf(rock))
        assertEquals(listOf(2L), restored.map { it.uid })
    }
}
