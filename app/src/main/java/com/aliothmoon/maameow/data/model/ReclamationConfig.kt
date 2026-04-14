package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.data.model.TaskParamProvider
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
        val THEME_KEYS = listOf("Tales", "Fire")
        val MODE_VALUES = listOf(0, 1)
        val INCREMENT_MODE_VALUES = listOf(0, 1)
        const val DEFAULT_TOOL_TO_CRAFT = "荧光棒"
    }

    override fun toTaskParams(): MaaTaskParams {
        val paramsJson = buildJsonObject {
            put("theme", theme)
            put("mode", mode)
            put("increment_mode", incrementMode)
            put("num_craft_batches", maxCraftCountPerRound)
            put("clear_store", clearStore)
            putJsonArray("tools_to_craft") {
                val toolName = toolToCraft.ifBlank { DEFAULT_TOOL_TO_CRAFT }
                toolName.split(";", "；").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                    add(JsonPrimitive(it))
                }
            }
        }
        return MaaTaskParams(MaaTaskType.RECLAMATION, paramsJson.toString())
    }
}
