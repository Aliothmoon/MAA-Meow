package com.aliothmoon.maameow.presentation.view.panel.roguelike

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.enums.RoguelikeMode
import com.aliothmoon.maameow.domain.enums.UiUsageConstants.Roguelike as RoguelikeUi

@Composable
fun localizedRoguelikeThemeOptions(): List<Pair<String, String>> {
    return RoguelikeUi.THEMES.map { it to localizedRoguelikeThemeLabel(it) }
}

@Composable
fun localizedRoguelikeModeOptionsForTheme(theme: String): List<Pair<String, String>> {
    return RoguelikeUi.getModeKeysForTheme(theme).map { it to localizedRoguelikeModeLabel(it) }
}

@Composable
fun localizedRoguelikeModeDescription(mode: RoguelikeMode): String {
    return when (mode) {
        RoguelikeMode.Exp -> stringResource(R.string.panel_roguelike_mode_desc_exp)
        RoguelikeMode.Investment -> stringResource(R.string.panel_roguelike_mode_desc_investment)
        RoguelikeMode.Collectible -> stringResource(R.string.panel_roguelike_mode_desc_collectible)
        RoguelikeMode.Squad -> stringResource(R.string.panel_roguelike_mode_desc_squad)
        RoguelikeMode.Exploration -> stringResource(R.string.panel_roguelike_mode_desc_exploration)
        RoguelikeMode.CLP_PDS -> stringResource(R.string.panel_roguelike_mode_desc_clp_pds)
        RoguelikeMode.FindPlaytime -> stringResource(R.string.panel_roguelike_mode_desc_find_playtime)
    }
}

@Composable
fun localizedRoguelikeRoleOptionsForTheme(theme: String): List<Pair<String, String>> {
    return RoguelikeUi.getRoleKeysForTheme(theme).map { it to localizedRoguelikeRoleLabel(it) }
}

@Composable
fun localizedRoguelikePlaytimeTargetOptions(): List<Pair<String, String>> {
    return RoguelikeUi.PLAYTIME_TARGETS.map { it to localizedRoguelikePlaytimeTargetLabel(it) }
}

@Composable
fun localizedRoguelikeCollectibleAwardOptions(theme: String): List<Pair<String, String>> {
    return RoguelikeUi.getCollectibleAwardKeys(theme).map { it to localizedRoguelikeCollectibleAwardLabel(it) }
}

@Composable
fun localizedRoguelikeDifficultyOptions(theme: String): List<Pair<Int, String>> {
    val maxDiff = RoguelikeUi.getMaxDifficultyForTheme(theme)
    return RoguelikeUi.getDifficultyValues(theme).map { value ->
        value to when (value) {
            -1 -> stringResource(R.string.common_not_switch)
            Int.MAX_VALUE -> stringResource(R.string.panel_roguelike_difficulty_max, maxDiff)
            0 -> stringResource(R.string.panel_roguelike_difficulty_min)
            else -> value.toString()
        }
    }
}

@Composable
fun localizedRoguelikeSquadOptions(theme: String, mode: RoguelikeMode): List<Pair<String, String>> {
    return RoguelikeUi.getSquadOptionsForTheme(theme, mode).map { it to localizedRoguelikeSquadLabel(it) }
}

@Composable
private fun localizedRoguelikeThemeLabel(theme: String): String {
    return when (theme) {
        "Phantom" -> stringResource(R.string.panel_roguelike_theme_phantom)
        "Mizuki" -> stringResource(R.string.panel_roguelike_theme_mizuki)
        "Sami" -> stringResource(R.string.panel_roguelike_theme_sami)
        "Sarkaz" -> stringResource(R.string.panel_roguelike_theme_sarkaz)
        "JieGarden" -> stringResource(R.string.panel_roguelike_theme_jiegarden)
        else -> theme
    }
}

@Composable
private fun localizedRoguelikeModeLabel(modeKey: String): String {
    return when (modeKey) {
        "Exp" -> stringResource(R.string.panel_roguelike_mode_exp)
        "Investment" -> stringResource(R.string.panel_roguelike_mode_investment)
        "Collectible" -> stringResource(R.string.panel_roguelike_mode_collectible)
        "Squad" -> stringResource(R.string.panel_roguelike_mode_squad)
        "Exploration" -> stringResource(R.string.panel_roguelike_mode_exploration)
        "CLP_PDS" -> stringResource(R.string.panel_roguelike_mode_clp_pds)
        "FindPlaytime" -> stringResource(R.string.panel_roguelike_mode_find_playtime)
        else -> modeKey
    }
}

@Composable
private fun localizedRoguelikeRoleLabel(role: String): String {
    return when (role) {
        RoguelikeUi.ROLE_FIRST_MOVE_ADVANTAGE -> stringResource(R.string.panel_roguelike_role_first_move_advantage)
        RoguelikeUi.DEFAULT_ROLE -> stringResource(R.string.panel_roguelike_role_slow_and_steady)
        RoguelikeUi.ROLE_OVERCOMING_WEAKNESSES -> stringResource(R.string.panel_roguelike_role_overcoming_weaknesses)
        RoguelikeUi.ROLE_AS_YOUR_HEART_DESIRES -> stringResource(R.string.panel_roguelike_role_as_your_heart_desires)
        RoguelikeUi.ROLE_FLEXIBLE_DEPLOYMENT -> stringResource(R.string.panel_roguelike_role_flexible_deployment)
        RoguelikeUi.ROLE_UNBREAKABLE -> stringResource(R.string.panel_roguelike_role_unbreakable)
        else -> role
    }
}

@Composable
private fun localizedRoguelikePlaytimeTargetLabel(target: String): String {
    return when (target) {
        "Ling" -> stringResource(R.string.panel_roguelike_playtime_target_ling)
        "Shu" -> stringResource(R.string.panel_roguelike_playtime_target_shu)
        "Nian" -> stringResource(R.string.panel_roguelike_playtime_target_nian)
        else -> target
    }
}

@Composable
private fun localizedRoguelikeCollectibleAwardLabel(key: String): String {
    return when (key) {
        "hot_water" -> stringResource(R.string.panel_roguelike_reward_hot_water)
        "shield" -> stringResource(R.string.panel_roguelike_reward_shield)
        "ingot" -> stringResource(R.string.panel_roguelike_reward_ingot)
        "hope" -> stringResource(R.string.panel_roguelike_reward_hope)
        "random" -> stringResource(R.string.panel_roguelike_reward_random)
        "key" -> stringResource(R.string.panel_roguelike_reward_key)
        "dice" -> stringResource(R.string.panel_roguelike_reward_dice)
        "ideas" -> stringResource(R.string.panel_roguelike_reward_ideas)
        "ticket" -> stringResource(R.string.panel_roguelike_reward_ticket)
        else -> key
    }
}

@Composable
private fun localizedRoguelikeSquadLabel(squad: String): String {
    return when (squad) {
        "指挥分队" -> stringResource(R.string.panel_roguelike_squad_leader)
        "后勤分队" -> stringResource(R.string.panel_roguelike_squad_support)
        "突击战术分队" -> stringResource(R.string.panel_roguelike_squad_tactical_assault)
        "堡垒战术分队" -> stringResource(R.string.panel_roguelike_squad_tactical_fortification)
        "远程战术分队" -> stringResource(R.string.panel_roguelike_squad_tactical_ranged)
        "破坏战术分队" -> stringResource(R.string.panel_roguelike_squad_tactical_destruction)
        "高规格分队" -> stringResource(R.string.panel_roguelike_squad_first_class)
        "集群分队" -> stringResource(R.string.panel_roguelike_squad_gathering)
        "矛头分队" -> stringResource(R.string.panel_roguelike_squad_spearhead)
        "研究分队" -> stringResource(R.string.panel_roguelike_squad_research)
        "心胜于物分队" -> stringResource(R.string.panel_roguelike_squad_mind_over_matter)
        "物尽其用分队" -> stringResource(R.string.panel_roguelike_squad_resourceful)
        "以人为本分队" -> stringResource(R.string.panel_roguelike_squad_people_oriented)
        "永恒狩猎分队" -> stringResource(R.string.panel_roguelike_squad_eternal_hunting)
        "生活至上分队" -> stringResource(R.string.panel_roguelike_squad_life_prioritizing)
        "科学主义分队" -> stringResource(R.string.panel_roguelike_squad_scientific_thinking)
        "特训分队" -> stringResource(R.string.panel_roguelike_squad_special_training)
        "魂灵护送分队" -> stringResource(R.string.panel_roguelike_squad_soul_escort)
        "博闻广记分队" -> stringResource(R.string.panel_roguelike_squad_erudite)
        "蓝图测绘分队" -> stringResource(R.string.panel_roguelike_squad_blueprint)
        "因地制宜分队" -> stringResource(R.string.panel_roguelike_squad_improvisation)
        "异想天开分队" -> stringResource(R.string.panel_roguelike_squad_mimic)
        "点刺成锭分队" -> stringResource(R.string.panel_roguelike_squad_ingots)
        "拟态学者分队" -> stringResource(R.string.panel_roguelike_squad_collection)
        "专业人士分队" -> stringResource(R.string.panel_roguelike_squad_top_gun)
        "特勤分队" -> stringResource(R.string.panel_roguelike_squad_special_service)
        "高台突破分队" -> stringResource(R.string.panel_roguelike_squad_high_ground_breakthrough)
        "地面突破分队" -> stringResource(R.string.panel_roguelike_squad_ground_breakthrough)
        "游客分队" -> stringResource(R.string.panel_roguelike_squad_tourist)
        "司岁台分队" -> stringResource(R.string.panel_roguelike_squad_sisuitai)
        "天师府分队" -> stringResource(R.string.panel_roguelike_squad_tianshifu)
        "花团锦簇分队" -> stringResource(R.string.panel_roguelike_squad_splendid_blossoms)
        "棋行险着分队" -> stringResource(R.string.panel_roguelike_squad_risky_gambit)
        "岁影回音分队" -> stringResource(R.string.panel_roguelike_squad_echo_of_ages)
        "代理人分队" -> stringResource(R.string.panel_roguelike_squad_agent)
        "知学分队" -> stringResource(R.string.panel_roguelike_squad_scholarly)
        "商贾分队" -> stringResource(R.string.panel_roguelike_squad_merchant)
        else -> squad
    }
}
