package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.model.TaskParamProvider

enum class TaskTypeInfo(
    val displayName: String,
    val defaultConfig: () -> TaskParamProvider
) {
    WAKE_UP("开始唤醒", { WakeUpConfig() }),
    RECRUITING("自动公招", { RecruitConfig() }),
    BASE("基建换班", { InfrastConfig() }),
    COMBAT("理智作战", { FightConfig() }),
    MALL("信用收支", { MallConfig() }),
    MISSION("领取奖励", { AwardConfig() }),
    AUTO_ROGUELIKE("自动肉鸽", { RoguelikeConfig() }),
    RECLAMATION("生息演算", { ReclamationConfig() });
}
