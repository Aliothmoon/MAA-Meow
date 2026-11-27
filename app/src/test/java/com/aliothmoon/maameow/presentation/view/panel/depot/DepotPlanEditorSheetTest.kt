package com.aliothmoon.maameow.presentation.view.panel.depot

import org.junit.Assert.assertEquals
import org.junit.Test

/** 覆盖抽屉二段键盘避让的纯几何计算：[computeDepotSheetImePlan] */
class DepotPlanEditorSheetTest {

    @Test
    fun shortEditSheet_usesPanOnlyWithZeroScrollClearance() {
        val plan = computeDepotSheetImePlan(
            rootHeightPx = 2712f,
            statusBarTopPx = 138f,
            imeTargetBottomPx = 1140f,
            imeMarginPx = 78f,
            sheetHeightPx = 1210f,
            viewportBottomInSheetPx = 930f,
            focusedBottomInSheetPx = 765f,
        )

        // distanceFromSheetBottom = 445 -> 需上移 1140 + 78 - 445 = 773，顶部余量 1364 足够
        assertEquals(773f, plan.sheetPanPx, 0.1f)
        assertEquals(0f, plan.scrollBottomClearancePx, 0.1f)
        assertEquals(0f, plan.scrollDeltaPx, 0.1f)
    }

    @Test
    fun expandedStageEditSheet_capsPanAtStatusBarAndScrollsRemainder() {
        val plan = computeDepotSheetImePlan(
            rootHeightPx = 2712f,
            statusBarTopPx = 138f,
            imeTargetBottomPx = 1140f,
            imeMarginPx = 78f,
            sheetHeightPx = 2400f,
            viewportBottomInSheetPx = 2120f,
            focusedBottomInSheetPx = 2050f,
        )

        // 顶部状态栏余量 174，footer 280 -> 末尾留白 1140 + 78 - 280 - 174 = 764
        // 目标输入框距底 350 -> 需上移 868，抽屉移 174 后滚动补齐 694
        assertEquals(174f, plan.sheetPanPx, 0.1f)
        assertEquals(764f, plan.scrollBottomClearancePx, 0.1f)
        assertEquals(694f, plan.scrollDeltaPx, 0.1f)
    }

    @Test
    fun addFlowFixedSheet_collapsedFieldStaysStillWhileExpandedFieldScrolls() {
        // 折叠态：输入框在上方，无需平移也无需滚动
        val collapsed = computeDepotSheetImePlan(
            rootHeightPx = 2712f,
            statusBarTopPx = 138f,
            imeTargetBottomPx = 1140f,
            imeMarginPx = 78f,
            sheetHeightPx = 2010f,
            viewportBottomInSheetPx = 1730f,
            focusedBottomInSheetPx = 780f,
        )
        assertEquals(0f, collapsed.sheetPanPx, 0.1f)
        assertEquals(374f, collapsed.scrollBottomClearancePx, 0.1f)
        assertEquals(0f, collapsed.scrollDeltaPx, 0.1f)

        // 展开关卡态：输入框顶到视口底部，抽屉平移 564 后滚动补齐 374
        val expanded = computeDepotSheetImePlan(
            rootHeightPx = 2712f,
            statusBarTopPx = 138f,
            imeTargetBottomPx = 1140f,
            imeMarginPx = 78f,
            sheetHeightPx = 2010f,
            viewportBottomInSheetPx = 1730f,
            focusedBottomInSheetPx = 1730f,
        )
        assertEquals(564f, expanded.sheetPanPx, 0.1f)
        assertEquals(374f, expanded.scrollBottomClearancePx, 0.1f)
        assertEquals(374f, expanded.scrollDeltaPx, 0.1f)
    }

    @Test
    fun switchingFocusWhilePanned_preservesPanAndOnlyScrollsAdditionalShortfall() {
        // 已经平移 174 且滚过一次后，焦点切到下方刚露出的输入框（距底 280）
        val next = computeDepotSheetImePlan(
            rootHeightPx = 2712f,
            statusBarTopPx = 138f,
            imeTargetBottomPx = 1140f,
            imeMarginPx = 78f,
            sheetHeightPx = 2400f,
            viewportBottomInSheetPx = 2120f,
            focusedBottomInSheetPx = 2120f,
            currentSheetPanPx = 174f,
        )
        assertEquals(174f, next.sheetPanPx, 0.1f)
        assertEquals(764f, next.scrollDeltaPx, 0.1f)

        // 焦点切到已在键盘上方的输入框（距底 1300），保持现平移量不回跳
        val alreadyVisible = computeDepotSheetImePlan(
            rootHeightPx = 2712f,
            statusBarTopPx = 138f,
            imeTargetBottomPx = 1140f,
            imeMarginPx = 78f,
            sheetHeightPx = 2400f,
            viewportBottomInSheetPx = 2120f,
            focusedBottomInSheetPx = 1100f,
            currentSheetPanPx = 174f,
        )
        assertEquals(174f, alreadyVisible.sheetPanPx, 0.1f)
        assertEquals(0f, alreadyVisible.scrollDeltaPx, 0.1f)
    }

    @Test
    fun keyboardHidden_resetsEverythingToZero() {
        val hidden = computeDepotSheetImePlan(
            rootHeightPx = 2712f,
            statusBarTopPx = 138f,
            imeTargetBottomPx = 0f,
            imeMarginPx = 78f,
            sheetHeightPx = 2400f,
            viewportBottomInSheetPx = 2120f,
            focusedBottomInSheetPx = 2050f,
            currentSheetPanPx = 174f,
        )
        assertEquals(DepotSheetImePlan(0f, 0f, 0f), hidden)
    }
}
