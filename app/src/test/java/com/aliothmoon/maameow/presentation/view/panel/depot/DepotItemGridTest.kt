package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖后台任务库存保持面板的两组格子怎么分：[depotMaintainItemCells]。
 *
 * 分错一组就会出现「明明配了计划却出现在暂无计划里」这种错位，或者加计划时把已有的那条
 * 又加一遍 —— 这两处都是纯函数算的，格子本身怎么画在 Compose 里，测不了。
 */
class DepotItemGridTest {

    private val itemIds = listOf("30012", "30011", "30013")
    private val itemNameMap = mapOf("30012" to "源岩", "30011" to "龙门币", "30013" to "聚酸酯")
    private val inventory = mapOf("30012" to 60, "30011" to 8)

    /** 有计划那组：一条计划一格，顺序就是计划顺序，进度按物品当前库存算 */
    @Test
    fun plannedCellsFollowPlanOrder() {
        val groups = depotMaintainItemCells(
            plans = listOf(
                DepotMaintainPlan(stage = "1-7", dropId = "30012", dropCount = 50),
                DepotMaintainPlan(stage = "CE-6", dropId = "30011", dropCount = 800),
            ),
            itemIds = itemIds,
            itemNameMap = itemNameMap,
            inventory = inventory,
            notSelectedLabel = "未选择",
        )

        assertEquals(listOf("plan-0", "plan-1"), groups.planned.map { it.key })
        assertEquals(listOf(0, 1), groups.planned.map { it.planIndex })
        assertEquals(listOf("源岩", "龙门币"), groups.planned.map { it.name })
        assertEquals(listOf(60, 8), groups.planned.map { it.count })
        assertEquals(listOf(50, 800), groups.planned.map { it.target })
    }

    /** 暂无计划那组：物品表里剩下的一切，按物品表顺序，只显示当前数量 */
    @Test
    fun unplannedIsTheRestOfTheItemTable() {
        val groups = depotMaintainItemCells(
            plans = listOf(DepotMaintainPlan(stage = "1-7", dropId = "30011", dropCount = 800)),
            itemIds = itemIds,
            itemNameMap = itemNameMap,
            inventory = inventory,
            notSelectedLabel = "未选择",
        )

        assertEquals(listOf("item-30012", "item-30013"), groups.unplanned.map { it.key })
        assertEquals(listOf("源岩", "聚酸酯"), groups.unplanned.map { it.name })
        assertEquals(listOf(60, 0), groups.unplanned.map { it.count })
        assertEquals(listOf(null, null), groups.unplanned.map { it.target })
        assertEquals(listOf(null, null), groups.unplanned.map { it.planIndex })
    }

    /**
     * 未选物品的计划留在「已有计划」那组。
     *
     * 老配置里「添加」留下的空行执行侧会跳过，但用户得能看见它、点开补上物品；
     * 它也不能把物品表里的任何一格挤掉。
     */
    @Test
    fun keepsUnselectedPlanInPlannedGroup() {
        val groups = depotMaintainItemCells(
            plans = listOf(DepotMaintainPlan(stage = "", dropId = "", dropCount = 0)),
            itemIds = itemIds,
            itemNameMap = itemNameMap,
            inventory = inventory,
            notSelectedLabel = "未选择",
        )

        assertEquals(1, groups.planned.size)
        assertEquals("", groups.planned.single().itemId)
        assertEquals("未选择", groups.planned.single().name)
        assertEquals(0, groups.planned.single().target)
        assertEquals(3, groups.unplanned.size)
    }

    /** 同一物品配了两条计划就是两格，和库存数据页一个口径；它也不会再出现在暂无计划里 */
    @Test
    fun oneCellPerPlanForTheSameItem() {
        val groups = depotMaintainItemCells(
            plans = listOf(
                DepotMaintainPlan(stage = "1-7", dropId = "30012", dropCount = 50),
                DepotMaintainPlan(stage = "PR-A-1", dropId = "30012", dropCount = 80),
            ),
            itemIds = itemIds,
            itemNameMap = itemNameMap,
            inventory = inventory,
            notSelectedLabel = "未选择",
        )

        assertEquals(listOf(0, 1), groups.planned.map { it.planIndex })
        assertEquals(listOf(50, 80), groups.planned.map { it.target })
        assertEquals(listOf("30011", "30013"), groups.unplanned.map { it.itemId })
    }

    /** 物品表里认不出的 id 直接用 id 兜底：这一组是「能点开配计划」的入口，不能写「未选择」 */
    @Test
    fun unplannedFallsBackToIdWhenNameIsUnknown() {
        val groups = depotMaintainItemCells(
            plans = emptyList(),
            itemIds = listOf("99999"),
            itemNameMap = emptyMap(),
            inventory = emptyMap(),
            notSelectedLabel = "未选择",
        )

        assertEquals("99999", groups.unplanned.single().name)
    }

    /** 老配置里留着识别集合之外的物品时，它照样在「已有计划」那组里，能点开改也能删 */
    @Test
    fun plannedItemOutsideItemTableStillHasACell() {
        val groups = depotMaintainItemCells(
            plans = listOf(DepotMaintainPlan(stage = "1-7", dropId = "99999", dropCount = 30)),
            itemIds = itemIds,
            itemNameMap = itemNameMap,
            inventory = emptyMap(),
            notSelectedLabel = "未选择",
        )

        assertEquals(1, groups.planned.size)
        assertEquals("99999", groups.planned.single().itemId)
        assertEquals("未选择", groups.planned.single().name)
        assertEquals(0, groups.planned.single().count)
        assertEquals(30, groups.planned.single().target)
        assertEquals(3, groups.unplanned.size)
        assertNull(groups.unplanned.firstOrNull { it.itemId == "99999" })
    }

    /** 什么都没配时，上面那组是空的（界面上不画分段标题），下面那组是整张物品表 */
    @Test
    fun emptyConfigLeavesEverythingUnplanned() {
        val groups = depotMaintainItemCells(
            plans = emptyList(),
            itemIds = itemIds,
            itemNameMap = itemNameMap,
            inventory = inventory,
            notSelectedLabel = "未选择",
        )

        assertEquals(emptyList<DepotItemCellUi>(), groups.planned)
        assertEquals(itemIds, groups.unplanned.map { it.itemId })
    }

    /**
     * 一行几格：跟着可用宽度走，但夹在 2~4 之间。
     *
     * 这两头都是有代价的 —— 少了图标铺不开、多了图标小到认不出是什么材料，
     * 所以宽屏也不能让它一路涨上去。括号里是设备上的实际可用宽度。
     */
    @Test
    fun columnsFollowAvailableWidthWithinBounds() {
        // 后台任务面板：屏幕宽减去侧栏与内边距
        assertEquals(3, depotItemColumns(280.dp))
        // 库存数据页：整宽减两侧内边距
        assertEquals(4, depotItemColumns(387.dp))
        // 平板那种宽度也就 4 格
        assertEquals(4, depotItemColumns(900.dp))
        // 窄到放不下也保底 2 格，不会退化成一条竖列
        assertEquals(2, depotItemColumns(120.dp))
        assertEquals(2, depotItemColumns(0.dp))
    }

    /**
     * 一行几格的像素宽度：加缝严格等于可用宽度，一格都不会换到下一行。
     *
     * 括号里那两组都是设备上量到的真实数字（面板内容宽 750px 是页面缩放 80% 时、
     * 738px 是 83% 时，缝都是 13px、四列）：按 dp 除再各自四舍五入是 178px 一格，
     * 四格加三条缝 751px，比可用宽度多 1px —— 界面上是「只摆三格、右边空出大半格」。
     */
    @Test
    fun cellWidthsFillRowExactly() {
        val widths = depotCellWidthsPx(availablePx = 750, gapPx = 13, columns = 4)
        assertEquals(listOf(178, 178, 178, 177), widths)
        // 四格加三条缝正好铺满，不留空档也不溢出
        assertEquals(750, widths.sum() + 13 * 3)
        // 宽度差不超过 1px，肉眼看不出一格宽一格窄
        assertEquals(1, widths.max() - widths.min())

        // 83% 缩放下那组：四格 175/175/175/174 加三条缝正好 738，一格都不该换行
        val at83 = depotCellWidthsPx(availablePx = 738, gapPx = 13, columns = 4)
        assertEquals(listOf(175, 175, 175, 174), at83)
        assertEquals(738, at83.sum() + 13 * 3)

        // 除得尽时就是整齐的等分
        assertEquals(listOf(180, 180, 180, 180), depotCellWidthsPx(759, 13, 4))
        // 三列、两列同样铺满
        assertEquals(750, depotCellWidthsPx(750, 13, 3).sum() + 13 * 2)
        assertEquals(750, depotCellWidthsPx(750, 13, 2).sum() + 13)
    }

    /** 宽度不够分（窄到负数）也不能给出负宽度，更不能崩 */
    @Test
    fun cellWidthsNeverNegative() {
        assertEquals(listOf(0, 0, 0, 0), depotCellWidthsPx(availablePx = 20, gapPx = 13, columns = 4))
        assertEquals(listOf(0, 0), depotCellWidthsPx(availablePx = 0, gapPx = 0, columns = 2))
        assertEquals(emptyList<Int>(), depotCellWidthsPx(availablePx = 750, gapPx = 13, columns = 0))
    }

    /**
     * 随便什么宽度、缝、列数，一行几格的宽度加缝都不许超过可用宽度。
     *
     * 超出去就是「第四格被挤到下一行」，界面上看着像少了一列 —— 这正是页面缩放到 83% 时的样子，
     * 所以这里把一段宽度区间整个扫一遍，别只盯着一两个数。
     */
    @Test
    fun cellWidthsNeverOverflowAvailable() {
        for (available in 200..1200) {
            for (gap in 0..20) {
                for (columns in 1..5) {
                    val widths = depotCellWidthsPx(available, gap, columns)
                    val where = "available=$available gap=$gap columns=$columns"
                    assertEquals(where, columns, widths.size)
                    assertTrue(where, widths.all { it >= 0 })
                    assertTrue(where, widths.sum() + gap * (columns - 1) <= available)
                    assertTrue(where, widths.max() - widths.min() <= 1)
                }
            }
        }
    }
}
