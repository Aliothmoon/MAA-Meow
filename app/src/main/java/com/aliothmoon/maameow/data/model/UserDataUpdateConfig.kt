package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.resource.ServerTimezone
import com.aliothmoon.maameow.domain.models.UserDataUpdateTriggerInterval
import com.aliothmoon.maameow.domain.models.isUserDataUpdateDue
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.serialization.Serializable

/**
 * 更新数据任务：按触发间隔在任务链中追加干员识别 / 仓库识别。
 *
 * 容器型任务，本身不对应单一 MaaCore 类型；展开为 0~2 个识别任务。
 * 迁移自 WPF UserDataUpdateTask + UserDataUpdateSettingsUserControlModel.Serialize。
 */
@Serializable
data class UserDataUpdateConfig(
    val updateOperBox: Boolean = true,
    val updateDepot: Boolean = true,
    val triggerInterval: UserDataUpdateTriggerInterval = UserDataUpdateTriggerInterval.EVERY_TIME,
) : TaskParamProvider {

    override fun toTaskParams(ctx: TaskParamContext): TaskParamResult {
        if (!updateOperBox && !updateDepot) {
            return TaskParamResult(emptyList())
        }

        val yjToday = ServerTimezone.getYjDate(ctx.clientType)
        val yjZone = ServerTimezone.getServerZone(ctx.clientType)
        val operDue = updateOperBox && isUserDataUpdateDue(
            lastSyncMillis = ctx.operBoxRepository.snapshot.value.syncTimeMillis,
            interval = triggerInterval,
            yjToday = yjToday,
            yjZone = yjZone,
        )
        val depotDue = updateDepot && isUserDataUpdateDue(
            lastSyncMillis = ctx.depotRepository.snapshot.value.syncTimeMillis,
            interval = triggerInterval,
            yjToday = yjToday,
            yjZone = yjZone,
        )
        if (!operDue && !depotDue) {
            return TaskParamResult(emptyList())
        }

        // 对齐上游：先干员后仓库。
        // 双识别到期不在这里预告 DoubleSync —— 该成就由 ToolboxResultCollector
        // 按「同一会话内两侧识别都真的成功」判定，排上队不等于同步成功。
        return TaskParamResult(
            buildList {
                if (operDue) add(MaaTaskParams(MaaTaskType.OPER_BOX, "{}"))
                if (depotDue) add(MaaTaskParams(MaaTaskType.DEPOT, "{}"))
            }
        )
    }
}
