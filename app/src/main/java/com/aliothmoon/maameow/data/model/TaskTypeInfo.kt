package com.aliothmoon.maameow.data.model

import android.content.Context
import androidx.annotation.StringRes
import com.aliothmoon.maameow.R

enum class TaskTypeInfo(
    @param:StringRes val nameRes: Int,
    val defaultConfig: () -> TaskParamProvider,
    /** 需要先配置内容的任务仅通过「添加任务」加入 */
    val inDefaultChain: Boolean = true,
    /** 新用户默认任务列表里是否勾选 */
    val enabledByDefault: Boolean = false,
) {
    WAKE_UP(R.string.task_type_wake_up, { WakeUpConfig() }, enabledByDefault = true),
    RECRUITING(R.string.task_type_recruiting, { RecruitConfig() }, enabledByDefault = true),
    BASE(R.string.task_type_base, { InfrastConfig() }, enabledByDefault = true),
    COMBAT(R.string.task_type_combat, { FightConfig() }),
    MALL(R.string.task_type_mall, { MallConfig() }),
    MISSION(R.string.task_type_mission, { AwardConfig() }),
    AUTO_ROGUELIKE(R.string.task_type_auto_roguelike, { RoguelikeConfig() }),
    USER_DATA_UPDATE(
        R.string.task_type_user_data_update,
        { UserDataUpdateConfig() },
    ),
    DEPOT_MAINTAIN(
        R.string.task_type_depot_maintain,
        { DepotMaintainConfig() },
        inDefaultChain = false,
    ),
    SWITCH_THEME(
        R.string.task_type_switch_theme,
        { SwitchThemeConfig() },
        inDefaultChain = false,
    ),
    OPER_PROGRESS(
        R.string.task_type_oper_progress,
        { OperProgressConfig() },
        inDefaultChain = false,
    );

    fun defaultName(context: Context): String = context.getString(nameRes)
}
