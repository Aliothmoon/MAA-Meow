package com.aliothmoon.maameow.data.model

import android.content.Context
import androidx.annotation.StringRes
import com.aliothmoon.maameow.R

enum class TaskTypeInfo(
    @param:StringRes val nameRes: Int,
    val defaultConfig: () -> TaskParamProvider,
    /**
     * 是否出现在新建配置的默认任务链中。
     * 库存保持需要用户先配好保持计划才有意义，空计划占位无价值，
     * 故与上游一致：仅通过「添加任务」手动加入。
     */
    val inDefaultChain: Boolean = true,
) {
    WAKE_UP(R.string.task_type_wake_up, { WakeUpConfig() }),
    RECRUITING(R.string.task_type_recruiting, { RecruitConfig() }),
    BASE(R.string.task_type_base, { InfrastConfig() }),
    COMBAT(R.string.task_type_combat, { FightConfig() }),
    MALL(R.string.task_type_mall, { MallConfig() }),
    MISSION(R.string.task_type_mission, { AwardConfig() }),
    AUTO_ROGUELIKE(R.string.task_type_auto_roguelike, { RoguelikeConfig() }),
    RECLAMATION(R.string.task_type_reclamation, { ReclamationConfig() }),
    USER_DATA_UPDATE(
        R.string.task_type_user_data_update,
        { UserDataUpdateConfig() },
    ),
    DEPOT_MAINTAIN(
        R.string.task_type_depot_maintain,
        { DepotMaintainConfig() },
        inDefaultChain = false,
    );

    fun defaultName(context: Context): String = context.getString(nameRes)
}
