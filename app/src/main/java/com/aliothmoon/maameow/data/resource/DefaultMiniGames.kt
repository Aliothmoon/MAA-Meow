package com.aliothmoon.maameow.data.resource

import androidx.annotation.StringRes
import com.aliothmoon.maameow.R

/**
 * 默认小游戏列表
 * 分组对齐上游 StageManager.BuildDefaultMiniGameEntries：隐秘战线归「常驻活动」，其余归「常驻功能」
 */
object DefaultMiniGames {
    data class DefaultMiniGameEntry(
        @param:StringRes val displayRes: Int,
        val value: String,
        @param:StringRes val tipRes: Int,
        @param:StringRes val categoryRes: Int = R.string.mini_game_category_permanent_feature,
    )

    val ENTRIES = listOf(
        DefaultMiniGameEntry(
            R.string.mini_game_name_secret_front,
            "MiniGame@SecretFront",
            R.string.mini_game_tip_secret_front,
            R.string.mini_game_category_permanent
        ),
        DefaultMiniGameEntry(
            R.string.mini_game_name_ss_store,
            "SS@Store@Begin",
            R.string.mini_game_tip_ss_store
        ),
        DefaultMiniGameEntry(
            R.string.mini_game_name_green_ticket_store,
            "GreenTicket@Store@Begin",
            R.string.mini_game_tip_green_ticket_store
        ),
        DefaultMiniGameEntry(
            R.string.mini_game_name_yellow_ticket_store,
            "YellowTicket@Store@Begin",
            R.string.mini_game_tip_yellow_ticket_store
        ),
        DefaultMiniGameEntry(
            R.string.mini_game_name_ra_store,
            "RA@Store@Begin",
            R.string.mini_game_tip_ra_store
        ),
        DefaultMiniGameEntry(
            R.string.mini_game_name_auto_raise_potential,
            "MiniGame@AutoRaisePotential@Begin",
            R.string.mini_game_tip_auto_raise_potential
        ),
        DefaultMiniGameEntry(
            R.string.mini_game_name_material_synthesis,
            "MiniGame@MaterialSynthesis@Begin",
            R.string.mini_game_tip_material_synthesis
        ),
        DefaultMiniGameEntry(
            R.string.mini_game_name_cursed_relic,
            "MiniGame@CursedRelic@Begin",
            R.string.mini_game_tip_cursed_relic
        )
    )
}
