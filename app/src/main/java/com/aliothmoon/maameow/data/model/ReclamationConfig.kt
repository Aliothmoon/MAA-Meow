package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.maa.task.TaskParamProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 生息演算配置
 */
@Serializable
data class ReclamationConfig(
    val theme: String = "Tales",
    val mode: Int = 1,
    val toolToCraft: String = "",
    val incrementMode: Int = 0,
    val maxCraftCountPerRound: Int = 16,
    val clearStore: Boolean = true
) : TaskParamProvider {
    companion object {
        // TODO 需要跟随更新
        val THEME_OPTIONS = listOf(
            "Tales" to "沙洲遗闻",
            "Fire" to "沙中之火 (已关闭)"
        )

        val MODE_OPTIONS = listOf(
            0 to "无存档 (进出关卡刷点数)",
            1 to "有存档 (组装道具刷点数)"
        )

        val INCREMENT_MODE_OPTIONS = listOf(
            0 to "连点",
            1 to "长按"
        )
    }

    override fun toTaskParams(): MaaTaskParams {
        val paramsJson = buildJsonObject {
            put("theme", theme)
            put("mode", mode)
            put("increment_mode", incrementMode)
            put("num_craft_batches", maxCraftCountPerRound)
            put("clear_store", clearStore)
            putJsonArray("tools_to_craft") {
                val toolName = toolToCraft.ifBlank { "荧光棒" }
                toolName.split(";", "；").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                    add(JsonPrimitive(it))
                }
            }
        }
        return MaaTaskParams(MaaTaskType.RECLAMATION, paramsJson.toString())
    }
}
