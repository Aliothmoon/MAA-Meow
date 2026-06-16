package com.aliothmoon.maameow.data.achievement

import android.content.Context
import com.aliothmoon.maameow.R

object AchievementTextFormatter {
    private val placeholderRegex = Regex("\\{key=([^}]+)\\}")

    fun formatPlaceholders(text: String, resolveKey: (String) -> String): String {
        return text.replace(placeholderRegex) { match ->
            resolveKey(match.groupValues[1])
        }
    }
}

fun Context.getAchievementPlaceholder(key: String): String {
    val resId = when (key) {
        "AchievementList" -> R.string.achievement_key_achievement_list
        "AutoRoguelike" -> R.string.achievement_key_auto_roguelike
        "AutoSquad" -> R.string.achievement_key_auto_squad
        "Combat" -> R.string.achievement_key_combat
        "Copilot" -> R.string.achievement_key_copilot
        "CreditFight" -> R.string.achievement_key_credit_fight
        "InfrastModeCustom" -> R.string.achievement_key_infrast_mode_custom
        "IssueReport" -> R.string.achievement_key_issue_report
        "MirrorChyan" -> R.string.achievement_key_mirror_chyan
        "OpenDebugFolder" -> R.string.achievement_key_open_debug_folder
        "Operator" -> R.string.achievement_key_operator
        "Recruiting" -> R.string.achievement_key_recruiting
        "ScheduleSettings" -> R.string.achievement_key_schedule_settings
        "StartingCoreChar" -> R.string.achievement_key_starting_core_char
        "UpdateCheckBeta" -> R.string.achievement_key_update_check_beta
        "UpdateCheckNightly" -> R.string.achievement_key_update_check_nightly
        "UseExpiringMedicine" -> R.string.achievement_key_use_expiring_medicine
        "UseSanityPotion" -> R.string.achievement_key_use_sanity_potion
        "UserDataUpdate" -> R.string.achievement_key_user_data_update
        else -> return key
    }
    return getString(resId)
}
