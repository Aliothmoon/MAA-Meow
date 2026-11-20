package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 牛杂「卫戍协议」极速模拟设置，对齐 WPF AsstStrongholdProtocolTask
 * 赛季固定规程：盟约未开放，core 收到只会空跑
 */
@Serializable
data class StrongholdProtocolConfig(
    val mode: String = MODE_BADGE,
    val target: Int = TARGET_GOLD,
    val runs: Int = DEFAULT_RUNS,
) {
    companion object {
        const val MODE_BADGE = "badge"
        const val MODE_SCORE = "score"

        const val TARGET_GOLD = 3
        const val TARGET_COLOR = 6
        val TARGETS = listOf(TARGET_GOLD, TARGET_COLOR)

        const val DEFAULT_RUNS = 60
        const val MAX_RUNS = 999

        private const val THEME_REGULATION = "Regulation"
    }

    val isBadge: Boolean get() = mode != MODE_SCORE

    // 越界参数会让 AsstAppendTask 直接返回 0，下发前收敛
    fun toTaskParams(): MaaTaskParams {
        val params = buildJsonObject {
            if (isBadge) {
                put("mode", MODE_BADGE)
                put("theme", THEME_REGULATION)
                put("target", target.takeIf { it in TARGETS } ?: TARGET_GOLD)
                put("runs", runs.coerceIn(1, MAX_RUNS))
                put("attempts", 0)
            } else {
                put("mode", MODE_SCORE)
            }
        }
        return MaaTaskParams(MaaTaskType.STRONGHOLD_PROTOCOL, params.toString())
    }
}
