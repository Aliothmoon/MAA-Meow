package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.resource.ServerTimezone
import com.aliothmoon.maameow.domain.models.UserDataUpdateTriggerInterval
import com.aliothmoon.maameow.domain.models.isUserDataUpdateDue
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.serialization.Serializable

/** 更新数据：按间隔展开为 0~2 个识别任务。 */
@Serializable
data class UserDataUpdateConfig(
    val updateOperBox: Boolean = true,
    val updateDepot: Boolean = true,
    val triggerInterval: UserDataUpdateTriggerInterval = UserDataUpdateTriggerInterval.EVERY_TIME,
) : TaskParamProvider {

    override fun toTaskParams(ctx: TaskParamContext): List<MaaTaskParams> {
        if (!updateOperBox && !updateDepot) {
            return emptyList()
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
            return emptyList()
        }

        // 对齐上游：先干员后仓库（串行）。
        return buildList {
            if (operDue) add(MaaTaskParams(MaaTaskType.OPER_BOX, "{}"))
            if (depotDue) add(MaaTaskParams(MaaTaskType.DEPOT, "{}"))
        }
    }
}
