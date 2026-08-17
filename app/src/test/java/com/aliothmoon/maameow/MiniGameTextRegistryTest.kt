package com.aliothmoon.maameow

import com.aliothmoon.maameow.data.resource.MiniGameTextRegistry
import com.aliothmoon.maameow.utils.i18n.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniGameTextRegistryTest {

    @Test
    fun resolveDisplay_prefersLocalizedKey_overInlineApiText() {
        // 线上 PF 条目同时带内联中文 Display 和 DisplayKey，必须命中本地化资源
        val result = MiniGameTextRegistry.resolveDisplay(
            display = "阵地足球锦标赛",
            displayKey = "MiniGame@PF",
            value = "MiniGame@PF@Begin"
        )
        assertEquals(UiText.Resource(R.string.mini_game_name_pf), result)
    }

    @Test
    fun resolveDisplay_unknownKey_fallsBackToInlineApiText() {
        val result = MiniGameTextRegistry.resolveDisplay(
            display = "未来的新活动",
            displayKey = "MiniGame@FutureUnknown",
            value = "MiniGame@FutureUnknown@Begin"
        )
        assertEquals(UiText.Dynamic("未来的新活动"), result)
    }

    @Test
    fun resolveDisplay_valueOnly_usesValueMapping() {
        val result = MiniGameTextRegistry.resolveDisplay(
            display = null,
            displayKey = null,
            value = "SS@Store@Begin"
        )
        assertEquals(UiText.Resource(R.string.mini_game_name_ss_store), result)
    }

    @Test
    fun resolveTip_prefersLocalizedKey_overInlineApiText() {
        val result = MiniGameTextRegistry.resolveTip(
            tip = "API 内联中文提示",
            tipKey = "MiniGame@PFTip",
            display = UiText.Dynamic("阵地足球锦标赛"),
            displayKey = "MiniGame@PF",
            value = "MiniGame@PF@Begin"
        )
        assertEquals(UiText.Resource(R.string.mini_game_tip_pf), result)
    }

    @Test
    fun resolveTip_unknownKey_fallsBackToInlineApiText() {
        val result = MiniGameTextRegistry.resolveTip(
            tip = "API 内联中文提示",
            tipKey = "MiniGame@FutureUnknownTip",
            display = UiText.Dynamic("未来的新活动"),
            displayKey = "MiniGame@FutureUnknown",
            value = "MiniGame@FutureUnknown@Begin"
        )
        assertEquals(UiText.Dynamic("API 内联中文提示"), result)
    }

    @Test
    fun resolveTip_noTipSources_fallsBackToDisplay() {
        val display = UiText.Dynamic("未来的新活动")
        val result = MiniGameTextRegistry.resolveTip(
            tip = null,
            tipKey = null,
            display = display,
            displayKey = null,
            value = "MiniGame@FutureUnknown@Begin"
        )
        assertEquals(display, result)
    }

    @Test
    fun resolveDisplay_interactiveExhibition_usesLocalizedName() {
        // 上游 v6.17.0-beta.2 新增的奇象巡展，没登记会直接把 Key 显示到界面上
        assertEquals(
            UiText.Resource(R.string.mini_game_name_interactive_exhibition),
            MiniGameTextRegistry.resolveDisplay(
                display = "奇象巡展-找未收录生物",
                displayKey = "MiniGame@InteractiveExhibition",
                value = "MiniGame@InteractiveExhibition@Begin"
            )
        )
    }

    @Test
    fun resolveTip_interactiveExhibition_usesLocalizedTip() {
        assertEquals(
            UiText.Resource(R.string.mini_game_tip_interactive_exhibition),
            MiniGameTextRegistry.resolveTip(
                tip = "API 内联中文提示",
                tipKey = "MiniGame@InteractiveExhibitionTip",
                display = UiText.Dynamic("奇象巡展-找未收录生物"),
                displayKey = "MiniGame@InteractiveExhibition",
                value = "MiniGame@InteractiveExhibition@Begin"
            )
        )
    }

    @Test
    fun resolveTip_allEmpty_returnsEmptyTip() {
        val result = MiniGameTextRegistry.resolveTip(
            tip = null,
            tipKey = null,
            display = UiText.Empty,
            displayKey = null,
            value = null
        )
        assertEquals(MiniGameTextRegistry.EMPTY_TIP, result)
    }
}
