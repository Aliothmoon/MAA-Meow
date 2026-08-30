package com.aliothmoon.maameow.data.resource

import androidx.annotation.StringRes
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf

/**
 * 小游戏文案注册表：API 本地化键 / 任务 value → 字符串资源
 * 键集合与 MaaWpfGui Res/Localizations 中的 MiniGame* 键保持一致
 */
object MiniGameTextRegistry {
    val EMPTY_TIP: UiText = uiTextOf(R.string.mini_game_empty_tip)

    @StringRes
    private fun displayResByKey(key: String): Int? = when (key) {
        "MiniGameNameSsStore" -> R.string.mini_game_name_ss_store
        "MiniGameNameGreenTicketStore" -> R.string.mini_game_name_green_ticket_store
        "MiniGameNameYellowTicketStore" -> R.string.mini_game_name_yellow_ticket_store
        "MiniGameNameRAStore" -> R.string.mini_game_name_ra_store
        "MiniGame@SecretFront" -> R.string.mini_game_name_secret_front
        "MiniGame@PV" -> R.string.mini_game_name_pv
        "MiniGame@SPA" -> R.string.mini_game_name_spa
        "MiniGame@OS" -> R.string.mini_game_name_os
        "MiniGame@RM-TR-1" -> R.string.mini_game_name_rm_tr_1
        "MiniGame@RM-1" -> R.string.mini_game_name_rm_1
        "MiniGame@AT@ConversationRoom" -> R.string.mini_game_name_at_conversation_room
        "MiniGame@ALL@GreenGrass" -> R.string.mini_game_name_all_green_grass
        "MiniGame@ALL@HoneyFruit" -> R.string.mini_game_name_all_honey_fruit
        "MiniGame@ALL@IvyVine" -> R.string.mini_game_name_all_ivy_vine
        "MiniGame@PF" -> R.string.mini_game_name_pf
        "MiniGame@InteractiveExhibition" -> R.string.mini_game_name_interactive_exhibition
        else -> null
    }

    @StringRes
    private fun tipResByKey(key: String): Int? = when (key) {
        "MiniGameNameSsStoreTip" -> R.string.mini_game_tip_ss_store
        "MiniGameNameGreenTicketStoreTip" -> R.string.mini_game_tip_green_ticket_store
        "MiniGameNameYellowTicketStoreTip" -> R.string.mini_game_tip_yellow_ticket_store
        "MiniGameNameRAStoreTip" -> R.string.mini_game_tip_ra_store
        "MiniGame@SecretFrontTip" -> R.string.mini_game_tip_secret_front
        "MiniGame@PixelPaintTip" -> R.string.mini_game_tip_pixel_paint
        "MiniGame@PVTip" -> R.string.mini_game_tip_pv
        "MiniGame@SPATip" -> R.string.mini_game_tip_spa
        "MiniGame@OSTip" -> R.string.mini_game_tip_os
        "MiniGame@RM-TR-1Tip" -> R.string.mini_game_tip_rm_tr_1
        "MiniGame@RM-1Tip" -> R.string.mini_game_tip_rm_1
        "MiniGame@AT@ConversationRoomTip" -> R.string.mini_game_tip_at_conversation_room
        "MiniGame@ALL@GreenGrassTip" -> R.string.mini_game_tip_all_duel_channel
        "MiniGame@ALL@HoneyFruitTip" -> R.string.mini_game_tip_all_duel_channel
        "MiniGame@ALL@IvyVineTip" -> R.string.mini_game_tip_all_duel_channel
        "MiniGame@PFTip" -> R.string.mini_game_tip_pf
        "MiniGame@InteractiveExhibitionTip" -> R.string.mini_game_tip_interactive_exhibition
        else -> null
    }

    @StringRes
    private fun displayResByValue(value: String): Int? = when (value) {
        "SS@Store@Begin" -> R.string.mini_game_name_ss_store
        "GreenTicket@Store@Begin" -> R.string.mini_game_name_green_ticket_store
        "YellowTicket@Store@Begin" -> R.string.mini_game_name_yellow_ticket_store
        "RA@Store@Begin" -> R.string.mini_game_name_ra_store
        "MiniGame@SecretFront" -> R.string.mini_game_name_secret_front
        "MiniGame@PV" -> R.string.mini_game_name_pv
        "MiniGame@SPA" -> R.string.mini_game_name_spa
        "MiniGame@OS" -> R.string.mini_game_name_os
        "MiniGame@RM-TR-1" -> R.string.mini_game_name_rm_tr_1
        "MiniGame@RM-1" -> R.string.mini_game_name_rm_1
        "MiniGame@AT@ConversationRoom" -> R.string.mini_game_name_at_conversation_room
        "MiniGame@ALL@GreenGrass" -> R.string.mini_game_name_all_green_grass
        "MiniGame@ALL@HoneyFruit" -> R.string.mini_game_name_all_honey_fruit
        "MiniGame@ALL@IvyVine" -> R.string.mini_game_name_all_ivy_vine
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
        "MiniGame@PV" -> R.string.mini_game_tip_pv
        "MiniGame@SPA" -> R.string.mini_game_tip_spa
        "MiniGame@OS" -> R.string.mini_game_tip_os
        "MiniGame@RM-TR-1" -> R.string.mini_game_tip_rm_tr_1
        "MiniGame@RM-1" -> R.string.mini_game_tip_rm_1
        "MiniGame@AT@ConversationRoom" -> R.string.mini_game_tip_at_conversation_room
        "MiniGame@ALL@GreenGrass" -> R.string.mini_game_tip_all_duel_channel
        "MiniGame@ALL@HoneyFruit" -> R.string.mini_game_tip_all_duel_channel
        "MiniGame@ALL@IvyVine" -> R.string.mini_game_tip_all_duel_channel
        "MiniGame@PF", "MiniGame@PF@Begin" -> R.string.mini_game_tip_pf
        "MiniGame@InteractiveExhibition", "MiniGame@InteractiveExhibition@Begin" ->
            R.string.mini_game_tip_interactive_exhibition

        else -> null
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
     * 解析优先级（同 resolveDisplay，已知 Key 优先，偏离 WPF v6.12.0-beta.2 的内联优先取向）：
     * TipKey 本地化 > API 内联 Tip > DisplayKey+"Tip" 约定键 > Value 映射 > Display 回退
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
        val localizedTip: UiText? = when {
            !tip.isNullOrBlank() -> UiText.Dynamic(tip)
            !displayKey.isNullOrBlank() -> tipResByKey("${displayKey}Tip")?.let { uiTextOf(it) }
            !value.isNullOrBlank() -> tipResByValue(value)?.let { uiTextOf(it) }
            else -> null
        }

        return when {
            localizedTip != null -> localizedTip
            display != UiText.Empty -> display
            else -> EMPTY_TIP
        }
    }
}
