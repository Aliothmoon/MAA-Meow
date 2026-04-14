package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.preferences.AppSettingsManager

import com.aliothmoon.maameow.data.model.TaskParamProvider

enum class TaskTypeInfo(
    private val defaultNameZh: String,
    private val defaultNameEn: String,
    val defaultConfig: () -> TaskParamProvider
) {
    WAKE_UP("开始唤醒", "StartUp", { WakeUpConfig() }),
    RECRUITING("自动公招", "Recruit", { RecruitConfig() }),
    BASE("基建换班", "Base", { InfrastConfig() }),
    COMBAT("理智作战", "Combat", { FightConfig() }),
    MALL("信用收支", "Credit", { MallConfig() }),
    MISSION("领取奖励", "Rewards", { AwardConfig() }),
    AUTO_ROGUELIKE("自动肉鸽", "Auto I.S.", { RoguelikeConfig() }),
    RECLAMATION("生息演算", "Reclamation", { ReclamationConfig() });

    fun defaultName(language: AppSettingsManager.AppLanguage): String {
        return when (language) {
            AppSettingsManager.AppLanguage.EN -> defaultNameEn
            else -> defaultNameZh
        }
    }
}
