package com.aliothmoon.maameow.data.model
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.maa.task.TaskParamProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 领取奖励配置
 */
@Serializable
data class AwardConfig(
    val award: Boolean = true,  // 领取每日/每周任务奖励
    val mail: Boolean = false,  // 领取所有邮件奖励
    val freeGacha: Boolean = false,  // 进行每日免费单抽
    val orundum: Boolean = false,  // 领取幸运墙合成玉
    val mining: Boolean = false,  // 领取挖矿合成玉
    val specialAccess: Boolean = false  // 领取周年特殊月卡
) : TaskParamProvider {
    override fun toTaskParams(): MaaTaskParams {
        val paramsJson = buildJsonObject {
            put("award", award)
            put("mail", mail)
            put("recruit", freeGacha)
            put("orundum", orundum)
            put("mining", mining)
            put("specialaccess", specialAccess)
        }
        return MaaTaskParams(MaaTaskType.AWARD, paramsJson.toString())
    }
}