package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.data.model.DepotMaintainConfig
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.DepotPlanOutcome
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.repository.DepotSnapshot
import com.aliothmoon.maameow.data.resource.ItemInfo
import com.aliothmoon.maameow.utils.i18n.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖库存页真正参与渲染与写回的那几个纯函数：[toUi] / [unmetIn] / [depotPlans] /
 * [firstDepotNode] / [depotMaintainConfigUi] / [depotProgressText] / [toPlanContext] /
 * [buildInventoryCells] / [groupForDisplay] / [farmingOrderSections] / [applyPlanOrder]。
 *
 * 它们原来是 private，测试只能自己重算一遍 need/outcome，等于在测测试；
 * 现在抽成 internal 的顶层函数，测的就是线上跑的那份。
 */
class DepotInventoryUiStateTest {

    // ========== 网格内容 ==========

    /** 库里一条记录都没有的材料也要出格子，否则「有没有」只能靠数格子猜 */
    @Test
    fun buildInventoryCells_addsDepotItemsMissingFromSnapshot() {
        val cells = buildInventoryCells(
            snapshot = DepotSnapshot(items = mapOf("30011" to 7)),
            itemMap = mapOf("30011" to info("30011", "源岩"), "30061" to info("30061", "破损装置")),
            depotItemIds = listOf("30011", "30061"),
            plans = emptyList(),
        )

        assertEquals(listOf("30011", "30061"), cells.map { it.id })
        assertEquals(7, cells.first { it.id == "30011" }.count)
        assertEquals(0, cells.first { it.id == "30061" }.count)
    }

    /** 快照里的物品即便超出识别集合也照样出格：Core 除了 MATERIAL 也会报合成玉、寻访凭证 */
    @Test
    fun buildInventoryCells_keepsSnapshotItemsOutsideDepotSet() {
        val cells = buildInventoryCells(
            snapshot = DepotSnapshot(items = mapOf("4003" to 72_000, "30011" to 7)),
            itemMap = mapOf("4003" to info("4003", "合成玉"), "30011" to info("30011", "源岩")),
            depotItemIds = listOf("30011"),
            plans = emptyList(),
        )

        // 合成玉 sortId 4003 < 源岩 30011，按 sortId 排在前面
        assertEquals(listOf("4003", "30011"), cells.map { it.id })
        assertEquals(72_000, cells.first { it.id == "4003" }.count)
    }

    /**
     * 声望这类杂项不进库存页：Core 每场战斗都会报 5001，
     * 但它既不能配计划也不是库存，出格子只会得到一个永远点不开的数字。
     */
    @Test
    fun buildInventoryCells_dropsJunkItemsWithoutPlan() {
        val cells = buildInventoryCells(
            snapshot = DepotSnapshot(items = mapOf("5001" to 98_765, "30011" to 7)),
            itemMap = mapOf("5001" to info("5001", "声望"), "30011" to info("30011", "源岩")),
            depotItemIds = listOf("30011"),
            plans = emptyList(),
            excludedItemIds = setOf("5001"),
        )

        assertEquals(listOf("30011"), cells.map { it.id })
    }

    /** 老计划里真存着杂项 id 时仍要能看见：否则那条计划在界面上删不掉 */
    @Test
    fun buildInventoryCells_keepsJunkItemWhenPlanned() {
        val snap = DepotSnapshot(items = mapOf("5001" to 98_765))
        val plans = listOf(plan(dropId = "5001").toUi(node(), 0, snap, emptyMap()) { true })

        val cells = buildInventoryCells(
            snapshot = snap,
            itemMap = mapOf("5001" to info("5001", "声望")),
            depotItemIds = emptyList(),
            plans = plans,
            excludedItemIds = setOf("5001"),
        )

        assertEquals(listOf("5001"), cells.map { it.id })
    }

    /** 龙门币这类货币能被选成刷取目标，数量按战斗累计的掉落算 */
    @Test
    fun buildInventoryCells_includesCurrencyTargets() {
        val cells = buildInventoryCells(
            snapshot = DepotSnapshot(items = mapOf("4001" to 1_200_000)),
            itemMap = mapOf("4001" to info("4001", "龙门币"), "4006" to info("4006", "采购凭证")),
            depotItemIds = listOf("4001", "4006"),
            plans = emptyList(),
        )

        assertEquals(listOf("4001", "4006"), cells.map { it.id })
        assertEquals(1_200_000, cells.first { it.id == "4001" }.count)
        assertEquals(0, cells.first { it.id == "4006" }.count)
    }

    /** 同一物品配了多条计划就出多格：执行侧会挨个跑，格子少一条就和二级页的计数对不上 */
    @Test
    fun buildInventoryCells_oneCellPerPlanForDuplicates() {
        val snap = DepotSnapshot(items = mapOf("30011" to 12))
        val plans = listOf(
            plan(dropCount = 50).toUi(node(), 0, snap, emptyMap()) { true },
            plan(dropCount = 100).toUi(node(), 1, snap, emptyMap()) { true },
        )

        val cells = buildInventoryCells(snap, emptyMap(), emptyList(), plans)

        assertEquals(2, cells.size)
        assertEquals(listOf(50, 100), cells.map { it.plan?.target })
        assertEquals(2, cells.map { it.key }.distinct().size)
    }

    /** 计划里的物品即便仓库里没有也要出现，否则「缺多少」在最需要看的时候反而没格子 */
    @Test
    fun buildInventoryCells_keepsPlannedItemWithoutSnapshot() {
        val plans = listOf(
            plan(dropCount = 50).toUi(node(), 0, DepotSnapshot(), emptyMap()) { true },
        )

        val cells = buildInventoryCells(DepotSnapshot(), emptyMap(), emptyList(), plans)

        assertEquals(listOf("30011"), cells.map { it.id })
        assertEquals(0, cells.single().count)
        assertTrue(cells.single().unmet)
    }

    @Test
    fun groupForDisplay_splitsIntoStockedUnmetAndEmpty() {
        val snap = DepotSnapshot(items = mapOf("30011" to 60, "30012" to 12, "30061" to 0))
        val cells = buildInventoryCells(
            snapshot = snap,
            itemMap = emptyMap(),
            depotItemIds = listOf("30011", "30012", "30061", "30062"),
            plans = listOf(
                // 目标 50、现有 12 → 未集齐
                plan(dropId = "30012", dropCount = 50)
                    .toUi(node(), 0, snap, emptyMap()) { true },
                // 目标 50、现有 60 → 已够，算「有库存」
                plan(dropId = "30011", dropCount = 50)
                    .toUi(node(), 1, snap, emptyMap()) { true },
            ),
        )

        val groups = cells.groupForDisplay()

        assertEquals(listOf("30011"), groups.stocked.map { it.id })
        assertEquals(listOf("30012"), groups.unmet.map { it.id })
        assertEquals(listOf("30061", "30062"), groups.empty.map { it.id })
    }

    // ========== 计划与进度 ==========

    @Test
    fun toUi_readsCurrentFromSnapshotAndComputesNeed() {
        val ui = plan(dropCount = 50, stage = "1-7")
            .toUi(node(), 0, DepotSnapshot(items = mapOf("30011" to 12)), emptyMap()) { true }

        assertEquals(12, ui.current)
        assertEquals(50, ui.target)
        assertEquals(38, ui.need)
        assertEquals(DepotPlanOutcome.Runnable, ui.outcome)
        assertTrue(ui.unmet)
    }

    @Test
    fun toUi_missingItemCountsAsZero() {
        val ui = plan(dropCount = 50, stage = "1-7")
            .toUi(node(), 0, DepotSnapshot(), emptyMap()) { true }

        assertEquals(0, ui.current)
        assertEquals(50, ui.need)
        assertTrue(ui.unmet)
    }

    @Test
    fun toUi_enoughStockClampsNeedToZero() {
        val ui = plan(dropCount = 50, stage = "1-7")
            .toUi(node(), 0, DepotSnapshot(items = mapOf("30011" to 60)), emptyMap()) { true }

        assertEquals(0, ui.need)
        assertEquals(DepotPlanOutcome.Enough, ui.outcome)
        assertFalse(ui.unmet)
    }

    /** 目标为 0 的计划会被执行侧按「目标为 0」拒掉，不该算成缺货 */
    @Test
    fun toUi_zeroTargetIsNotUnmet() {
        val ui = plan(dropCount = 0, stage = "1-7")
            .toUi(node(), 0, DepotSnapshot(), emptyMap()) { true }

        assertEquals(DepotPlanOutcome.ZeroTarget, ui.outcome)
        assertFalse(ui.unmet)
        assertFalse(plan(dropCount = 0).unmetIn(DepotSnapshot()))
    }

    @Test
    fun toUi_stageClosedFollowsActivityManager() {
        val ui = plan(dropCount = 50, stage = "1-7")
            .toUi(node(), 0, DepotSnapshot(), emptyMap()) { false }

        assertEquals(DepotPlanOutcome.StageClosed, ui.outcome)
    }

    @Test
    fun toUi_carriesNodeStateAndPlanIndex() {
        val ui = plan(dropCount = 50, stage = "1-7")
            .toUi(node(id = "n1", enabled = false), 3, DepotSnapshot(), emptyMap()) { true }

        assertEquals("n1", ui.nodeId)
        assertEquals(UiText.Dynamic("库存保持"), ui.nodeName)
        assertFalse(ui.node.nodeEnabled)
        assertEquals(3, ui.planIndex)
        // 物品查不到时回退成 ID，别显示空白
        assertEquals("30011", ui.itemName)
    }

    /** 库存页只关心节点启没启用（没启用时提示「保存了也不会跑」），药石在配置页配 */
    @Test
    fun toPlanContext_readsNodeEnabled() {
        assertFalse(node(id = "n1", enabled = false).toPlanContext().nodeEnabled)
        assertTrue(node().toPlanContext().nodeEnabled)
    }

    @Test
    fun unmetIn_isFalseWhenStockReachesTarget() {
        assertFalse(plan(dropCount = 50).unmetIn(DepotSnapshot(items = mapOf("30011" to 50))))
        assertTrue(plan(dropCount = 50).unmetIn(DepotSnapshot(items = mapOf("30011" to 49))))
    }

    /** 没选物品的计划在配置页已经有提示，网格里不该再多一个空格子 */
    @Test
    fun depotPlans_skipsPlansWithoutItem() {
        val chain = listOf(
            node(id = "a", config = DepotMaintainConfig(plans = listOf(plan(dropId = "")))),
            node(id = "b", config = DepotMaintainConfig(plans = listOf(plan(), plan()))),
        )

        assertEquals(2, chain.depotPlans().size)
    }

    @Test
    fun depotPlans_ignoresNonDepotNodes() {
        val chain = listOf(
            TaskChainNode(id = "a", name = "信用收支", config = MallConfig()),
            node(id = "b", config = DepotMaintainConfig(plans = listOf(plan()))),
        )

        assertEquals(1, chain.depotPlans().size)
    }

    /** 写回落点与 TaskChainState.updateDepotMaintainPlans 的选取必须一致：启用优先 */
    @Test
    fun firstDepotNode_prefersEnabledNode() {
        val disabled = node(id = "disabled", enabled = false, config = DepotMaintainConfig())
        val enabled = node(id = "enabled", enabled = true, config = DepotMaintainConfig())

        assertEquals("enabled", listOf(disabled, enabled).firstDepotNode()?.id)
        assertEquals("disabled", listOf(disabled).firstDepotNode()?.id)
        assertNull(
            listOf(TaskChainNode(id = "other", name = "信用收支", config = MallConfig()))
                .firstDepotNode()
        )
    }

    /**
     * 计划面板里的设置改的就是这个节点的配置：显示哪个节点，改动就得落到哪个节点上 —
     * 落错了不报错，只是安静地不生效。
     */
    @Test
    fun depotMaintainConfigUi_carriesNodeIdAndItsConfig() {
        val disabled = node(
            id = "disabled",
            name = "旧节点",
            enabled = false,
            config = DepotMaintainConfig(),
        )
        val enabled = node(
            id = "enabled",
            name = "库存保持",
            enabled = true,
            config = DepotMaintainConfig(
                useAutoSeries = true,
                plans = listOf(plan()),
            ),
        )

        val ui = listOf(disabled, enabled).depotMaintainConfigUi()

        assertEquals("enabled", ui.nodeId)
        assertTrue(ui.config.useAutoSeries)
        assertEquals(1, ui.config.plans.size)
    }

    /** 一档只有停用的库存保持节点时也要能改：落到那个节点上，不去碰别的节点 */
    @Test
    fun depotMaintainConfigUi_fallsBackToDisabledNode() {
        val ui = listOf(node(id = "disabled", enabled = false, config = DepotMaintainConfig()))
            .depotMaintainConfigUi()

        assertEquals("disabled", ui.nodeId)
    }

    /** 还没有库存保持节点：给默认配置、nodeId 留空，写回时才由 TaskChainState 建节点 */
    @Test
    fun depotMaintainConfigUi_isEmptyWithoutDepotNode() {
        val ui = listOf(TaskChainNode(id = "a", name = "信用收支", config = MallConfig()))
            .depotMaintainConfigUi()

        assertEquals("", ui.nodeId)
        assertEquals(DepotMaintainConfig(), ui.config)
    }

    // ========== 进度文案 ==========

    /** 与后台任务的计划概览同一份实现：当前在前、目标在后 */
    @Test
    fun depotProgressText_putsCurrentFirst() {
        assertEquals("60 / 50", depotProgressText(current = 60, target = 50, synced = true))
    }

    /**
     * 没识别过仓库时当前库存是未知，不是 0 —— 那时所有计划都会算成「一件都没有」，
     * 写 0 会让人以为真的一件都没有
     */
    @Test
    fun depotProgressText_writesDashWhenNotSynced() {
        assertEquals("-- / 50", depotProgressText(current = 0, target = 50, synced = false))
    }

    @Test
    fun cellUi_withoutPlanIsSettled() {
        val cell = cell(id = "30011", count = 7, plan = null)

        assertFalse(cell.unmet)
    }

    @Test
    fun cellUi_followsItsPlan() {
        val cell = cell(
            id = "30011",
            count = 7,
            plan = plan(dropCount = 50, stage = "1-7")
                .toUi(node(), 0, DepotSnapshot(items = mapOf("30011" to 7)), emptyMap()) { true },
        )

        assertTrue(cell.unmet)
        assertEquals(43, cell.plan?.need)
    }

    /** 导出列表按快照原样来：识别报了什么就画什么，不按物品种类裁剪 */
    @Test
    fun itemUiList_keepsSnapshotItemsOutsideDepotSet() {
        val snapshot = DepotSnapshot(
            items = mapOf(
                "4003" to 72_000,
                "7003" to 3,
                "7004" to 1,
                "30011" to 7,
            ),
        )
        val itemMap = mapOf(
            "4003" to info("4003", "合成玉"),
            "7003" to info("7003", "寻访凭证"),
            "7004" to info("7004", "十连寻访凭证"),
            "30011" to info("30011", "源岩"),
        )

        assertEquals(
            listOf("4003", "7003", "7004", "30011"),
            snapshot.toItemUiList(itemMap).map { it.id },
        )
    }

    /**
     * 刷取顺序页点一行要按 [depotCellKey] 找回它那一格的面板，
     * 这个 key 必须和 [buildInventoryCells] 造格子时用的是同一个 —— 两边各拼一份的话，
     * 改了一边就会变成「点上去没反应」，而且不报错。
     */
    @Test
    fun depotCellKey_matchesTheKeyBuildInventoryCellsMakes() {
        val snap = DepotSnapshot(items = mapOf("30011" to 12))
        val ui = plan(dropCount = 50).toUi(node(id = "n1"), 2, snap, emptyMap()) { true }

        val cells = buildInventoryCells(snap, emptyMap(), listOf("30011"), listOf(ui))

        assertEquals(depotCellKey(itemId = "30011", nodeId = "n1", planIndex = 2), cells.single().key)
    }

    // ========== 夹具 ==========

    private fun plan(
        dropId: String = "30011",
        dropCount: Int = 50,
        stage: String = "1-7",
    ) = DepotMaintainPlan(
        stage = stage,
        dropId = dropId,
        dropCount = dropCount,
    )

    private fun node(
        id: String = "node",
        name: String = "库存保持",
        enabled: Boolean = true,
        config: TaskParamProvider = DepotMaintainConfig(plans = listOf(plan())),
    ) = TaskChainNode(id = id, name = name, enabled = enabled, config = config)

    private fun info(id: String, name: String) =
        ItemInfo(id = id, name = name, icon = "", sortId = id.toIntOrNull() ?: 0)

    private fun cell(
        id: String,
        count: Int,
        plan: DepotMaintainPlanUi?,
    ) = DepotInventoryCellUi(
        key = id,
        id = id,
        name = id,
        count = count,
        sortId = id.toIntOrNull() ?: 0,
        plan = plan,
    )
}
