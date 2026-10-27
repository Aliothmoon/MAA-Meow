package com.aliothmoon.maameow.data.resource

import androidx.annotation.StringRes
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf

/**
 * 小游戏文案注册表：API 本地化键 / 任务 value → 字符串资源
 * 键名对齐 MaaWpfGui Res/Localizations 的 Toolbox.MiniGame.*（v6.21.0-beta.2 起），只登记 API 会下发的条目
 */
object MiniGameTextRegistry {
    val EMPTY_TIP: UiText = uiTextOf(R.string.mini_game_empty_tip)

    @StringRes
    private fun displayResByKey(key: String): Int? = when (key) {
        "Toolbox.MiniGame.Name.SsStore" -> R.string.mini_game_name_ss_store
        "Toolbox.MiniGame.Name.GreenTicketStore" -> R.string.mini_game_name_green_ticket_store
        "Toolbox.MiniGame.Name.YellowTicketStore" -> R.string.mini_game_name_yellow_ticket_store
        "Toolbox.MiniGame.Name.RAStore" -> R.string.mini_game_name_ra_store
        "Toolbox.MiniGame.SecretFront" -> R.string.mini_game_name_secret_front
        "Toolbox.MiniGame.AutoRaisePotential" -> R.string.mini_game_name_auto_raise_potential
        "Toolbox.MiniGame.MaterialSynthesis" -> R.string.mini_game_name_material_synthesis
        "Toolbox.MiniGame.CursedRelic" -> R.string.mini_game_name_cursed_relic
        "Toolbox.MiniGame.PV" -> R.string.mini_game_name_pv
        "Toolbox.MiniGame.SPA" -> R.string.mini_game_name_spa
        "Toolbox.MiniGame.OS" -> R.string.mini_game_name_os
        "Toolbox.MiniGame.RMTR1" -> R.string.mini_game_name_rm_tr_1
        "Toolbox.MiniGame.RM1" -> R.string.mini_game_name_rm_1
        "Toolbox.MiniGame.AT.ConversationRoom" -> R.string.mini_game_name_at_conversation_room
        "Toolbox.MiniGame.All.GreenGrass" -> R.string.mini_game_name_all_green_grass
        "Toolbox.MiniGame.All.HoneyFruit" -> R.string.mini_game_name_all_honey_fruit
        "Toolbox.MiniGame.All.IvyVine" -> R.string.mini_game_name_all_ivy_vine
        "Toolbox.MiniGame.PF" -> R.string.mini_game_name_pf
        "Toolbox.MiniGame.InteractiveExhibition" -> R.string.mini_game_name_interactive_exhibition
        else -> null
    }

    @StringRes
    private fun tipResByKey(key: String): Int? = when (key) {
        "Toolbox.MiniGame.Name.SsStore.Tip" -> R.string.mini_game_tip_ss_store
        "Toolbox.MiniGame.Name.GreenTicketStore.Tip" -> R.string.mini_game_tip_green_ticket_store
        "Toolbox.MiniGame.Name.YellowTicketStore.Tip" -> R.string.mini_game_tip_yellow_ticket_store
        "Toolbox.MiniGame.Name.RAStore.Tip" -> R.string.mini_game_tip_ra_store
        "Toolbox.MiniGame.SecretFront.Tip" -> R.string.mini_game_tip_secret_front
        "Toolbox.MiniGame.AutoRaisePotential.Tip" -> R.string.mini_game_tip_auto_raise_potential
        "Toolbox.MiniGame.MaterialSynthesis.Tip" -> R.string.mini_game_tip_material_synthesis
        "Toolbox.MiniGame.CursedRelic.Tip" -> R.string.mini_game_tip_cursed_relic
        "Toolbox.MiniGame.PixelPaint.Tip" -> R.string.mini_game_tip_pixel_paint
        "Toolbox.MiniGame.PV.Tip" -> R.string.mini_game_tip_pv
        "Toolbox.MiniGame.SPA.Tip" -> R.string.mini_game_tip_spa
        "Toolbox.MiniGame.OS.Tip" -> R.string.mini_game_tip_os
        "Toolbox.MiniGame.RMTR1.Tip" -> R.string.mini_game_tip_rm_tr_1
        "Toolbox.MiniGame.RM1.Tip" -> R.string.mini_game_tip_rm_1
        "Toolbox.MiniGame.AT.ConversationRoom.Tip" -> R.string.mini_game_tip_at_conversation_room
        "Toolbox.MiniGame.All.GreenGrass.Tip" -> R.string.mini_game_tip_all_duel_channel
        "Toolbox.MiniGame.All.HoneyFruit.Tip" -> R.string.mini_game_tip_all_duel_channel
        "Toolbox.MiniGame.All.IvyVine.Tip" -> R.string.mini_game_tip_all_duel_channel
        "Toolbox.MiniGame.PF.Tip" -> R.string.mini_game_tip_pf
        "Toolbox.MiniGame.InteractiveExhibition.Tip" -> R.string.mini_game_tip_interactive_exhibition
        else -> null
    }

    @StringRes
    private fun displayResByValue(value: String): Int? = when (value) {
        "SS@Store@Begin" -> R.string.mini_game_name_ss_store
        "GreenTicket@Store@Begin" -> R.string.mini_game_name_green_ticket_store
        "YellowTicket@Store@Begin" -> R.string.mini_game_name_yellow_ticket_store
        "RA@Store@Begin" -> R.string.mini_game_name_ra_store
        "MiniGame@SecretFront" -> R.string.mini_game_name_secret_front
        "MiniGame@AutoRaisePotential", "MiniGame@AutoRaisePotential@Begin" ->
            R.string.mini_game_name_auto_raise_potential

        "MiniGame@MaterialSynthesis", "MiniGame@MaterialSynthesis@Begin" ->
            R.string.mini_game_name_material_synthesis

        "MiniGame@CursedRelic", "MiniGame@CursedRelic@Begin" ->
            R.string.mini_game_name_cursed_relic

        "MiniGame@PV", "MiniGame@PV@Begin" -> R.string.mini_game_name_pv
        "MiniGame@SPA", "MiniGame@SPA@Begin" -> R.string.mini_game_name_spa
        "MiniGame@OS", "MiniGame@OS@Begin" -> R.string.mini_game_name_os
        "MiniGame@RM-TR-1", "MiniGame@RM-TR-1@Begin" -> R.string.mini_game_name_rm_tr_1
        "MiniGame@RM-1", "MiniGame@RM-1@Begin" -> R.string.mini_game_name_rm_1
        "MiniGame@AT@ConversationRoom" -> R.string.mini_game_name_at_conversation_room
        "MiniGame@ALL@GreenGrass@DuelChannel@Begin" -> R.string.mini_game_name_all_green_grass
        "MiniGame@ALL@GreenGrass@HoneyFruit@Begin" -> R.string.mini_game_name_all_honey_fruit
        "MiniGame@ALL@GreenGrass@IvyVine@Begin" -> R.string.mini_game_name_all_ivy_vine
        "MiniGame@PF", "MiniGame@PF@Begin" -> R.string.mini_game_name_pf
        "MiniGame@InteractiveExhibition", "MiniGame@InteractiveExhibition@Begin" ->
            R.string.mini_game_name_interactive_exhibition

        else -> null
    }

    @StringRes
    private fun tipResByValue(value: String): Int? = when (value) {
        "SS@Store@Begin" -> R.string.mini_game_tip_ss_store
        "GreenTicket@Store@Begin" -> R.string.mini_game_tip_green_ticket_store
        "YellowTicket@Store@Begin" -> R.string.mini_game_tip_yellow_ticket_store
        "RA@Store@Begin" -> R.string.mini_game_tip_ra_store
        "MiniGame@SecretFront" -> R.string.mini_game_tip_secret_front
        "MiniGame@AutoRaisePotential", "MiniGame@AutoRaisePotential@Begin" ->
            R.string.mini_game_tip_auto_raise_potential

        "MiniGame@MaterialSynthesis", "MiniGame@MaterialSynthesis@Begin" ->
            R.string.mini_game_tip_material_synthesis

        "MiniGame@CursedRelic", "MiniGame@CursedRelic@Begin" ->
            R.string.mini_game_tip_cursed_relic

        "MiniGame@PV", "MiniGame@PV@Begin" -> R.string.mini_game_tip_pv
        "MiniGame@SPA", "MiniGame@SPA@Begin" -> R.string.mini_game_tip_spa
        "MiniGame@OS", "MiniGame@OS@Begin" -> R.string.mini_game_tip_os
        "MiniGame@RM-TR-1", "MiniGame@RM-TR-1@Begin" -> R.string.mini_game_tip_rm_tr_1
        "MiniGame@RM-1", "MiniGame@RM-1@Begin" -> R.string.mini_game_tip_rm_1
        "MiniGame@AT@ConversationRoom" -> R.string.mini_game_tip_at_conversation_room
        "MiniGame@ALL@GreenGrass@DuelChannel@Begin",
        "MiniGame@ALL@GreenGrass@HoneyFruit@Begin",
        "MiniGame@ALL@GreenGrass@IvyVine@Begin" -> R.string.mini_game_tip_all_duel_channel
        "MiniGame@PF", "MiniGame@PF@Begin" -> R.string.mini_game_tip_pf
        "MiniGame@InteractiveExhibition", "MiniGame@InteractiveExhibition@Begin" ->
            R.string.mini_game_tip_interactive_exhibition

        else -> null
    }

    @StringRes
    private fun categoryResByKey(key: String): Int? = when (key) {
        "Toolbox.MiniGame.Category.PermanentFeature" -> R.string.mini_game_category_permanent_feature
        "Toolbox.MiniGame.Category.Permanent" -> R.string.mini_game_category_permanent
        "Toolbox.MiniGame.Category.CurrentEvent" -> R.string.mini_game_category_current_event
        else -> null
    }

    /**
     * 分组文案：CategoryKey 本地化 > API 内联 Category > 「当前活动」
     * 仅用于热更下发的条目，它们都带起止时间，未指定分组即归当前活动（对齐 WPF isCurrentEvent）
     */
    fun resolveCategory(category: String?, categoryKey: String?): UiText {
        if (!categoryKey.isNullOrBlank()) {
            categoryResByKey(categoryKey)?.let { return uiTextOf(it) }
        }
        return category?.takeIf { it.isNotBlank() }?.let { UiText.Dynamic(it) }
            ?: uiTextOf(R.string.mini_game_category_current_event)
    }

    /**
     * 解析优先级（有意偏离 WPF：已知 Key 的本地化资源优先于 API 内联文案，
     * 否则英文等非简中语言会直接展示 API 下发的中文；未知 Key 回退内联文案保持可用）：
     * DisplayKey 本地化 > API 内联 Display > 未知 DisplayKey 原样回退 > Value 映射
     */
    fun resolveDisplay(display: String?, displayKey: String?, value: String?): UiText {
        if (!displayKey.isNullOrBlank()) {
            displayResByKey(displayKey)?.let { return uiTextOf(it) }
        }
        return when {
            !display.isNullOrBlank() -> UiText.Dynamic(display)
            !displayKey.isNullOrBlank() ->
                UiText.Dynamic(value?.takeIf { it.isNotBlank() } ?: displayKey)

            !value.isNullOrBlank() ->
                displayResByValue(value)?.let { uiTextOf(it) } ?: UiText.Dynamic(value)

            else -> UiText.Empty
        }
    }

    /**
     * 解析优先级（前三级与上游 GetMiniGameTip 一致，Value 映射为本地兜底）：
     * TipKey 本地化 > API 内联 Tip > DisplayKey+".Tip" 约定键 > Value 映射 > Display 回退
     */
    fun resolveTip(
        tip: String?,
        tipKey: String?,
        display: UiText,
        displayKey: String?,
        value: String?
    ): UiText {
        if (!tipKey.isNullOrBlank()) {
            tipResByKey(tipKey)?.let { return uiTextOf(it) }
        }
        if (!tip.isNullOrBlank()) return UiText.Dynamic(tip)

        val res = displayKey?.takeIf { it.isNotBlank() }?.let { tipResByKey("$it.Tip") }
            ?: value?.takeIf { it.isNotBlank() }?.let { tipResByValue(it) }
        return when {
            res != null -> uiTextOf(res)
            display != UiText.Empty -> display
            else -> EMPTY_TIP
        }
    }
}
