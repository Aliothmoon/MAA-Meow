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
 *
 * 参数严格对齐 MaaWpfGui v6.10.5:
 * - mode 字段含义由 theme 决定,采用 flags 风格编码以避免 Tales / RelaunchAnchor 值冲突:
 *   - Tales: 0 = ProsperityNoSave, 1 = ProsperityInSave
 *   - RelaunchAnchor: 16 (1<<4) = RA-1, 32 (2<<4) = RA-15, 48 (3<<4) = RA-4
 * - WPF: ReclamationTask.Mode 默认 ProsperityInSave (= 1)
 */
@Serializable
data class ReclamationConfig(
    val theme: String = "Tales",
    val mode: Int = MODE_PROSPERITY_IN_SAVE,
    val toolToCraft: String = "",
    val incrementMode: Int = 0,
    val maxCraftCountPerRound: Int = 16,
    val clearStore: Boolean = true
) : TaskParamProvider {
    companion object {
        val THEME_KEYS = listOf("Tales", "Fire", "RelaunchAnchor")

        // Tales 模式值(对应 WPF TalesMode / MaaCore TalesMode)
        const val MODE_PROSPERITY_NO_SAVE = 0
        const val MODE_PROSPERITY_IN_SAVE = 1

        // RelaunchAnchor 模式值(v6.10.3 起改为 flags 编码,对应 WPF/MaaCore RelaunchAnchorMode)
        const val MODE_RA1 = 1 shl 4   // 16
        const val MODE_RA15 = 2 shl 4  // 32
        const val MODE_RA4 = 3 shl 4   // 48

        val TALES_MODE_VALUES = listOf(MODE_PROSPERITY_NO_SAVE, MODE_PROSPERITY_IN_SAVE)
        val RELAUNCH_ANCHOR_MODE_VALUES = listOf(MODE_RA1, MODE_RA4, MODE_RA15)

        // 向后兼容旧引用(Tales 0/1 值列表)
        @Deprecated("Use TALES_MODE_VALUES", ReplaceWith("TALES_MODE_VALUES"))
        val MODE_VALUES = TALES_MODE_VALUES
        val INCREMENT_MODE_VALUES = listOf(0, 1)

        // 对应 WPF ReclamationToolToCraftPlaceholder 各语言版本,按 clientType 取
        private val DEFAULT_TOOL_BY_CLIENT = mapOf(
            "Official" to "荧光棒",
            "Bilibili" to "荧光棒",
            "txwy" to "螢光棒",
            "YoStarEN" to "Glow Stick",
            "YoStarJP" to "ケミカルライト",
            "YoStarKR" to "형광봉"
        )
        const val DEFAULT_TOOL_TO_CRAFT = "荧光棒"

        fun defaultToolToCraft(clientType: String): String =
            DEFAULT_TOOL_BY_CLIENT[clientType] ?: DEFAULT_TOOL_TO_CRAFT
    }

    /**
     * 校正 mode 与 theme 的匹配关系。RelaunchAnchor 主题下若 mode 不在 {RA1, RA15} 中
     * (例如旧版本残留的 0/1),回退到 RA-1。
     */
    fun sanitizedMode(): Int {
        return if (theme == "RelaunchAnchor" && mode !in RELAUNCH_ANCHOR_MODE_VALUES) {
            MODE_RA1
        } else {
            mode
        }
    }

    override fun toTaskParams(ctx: TaskParamContext): TaskParamResult {
        // 对齐 WPF: 全角分号→半角;空字符串→ReclamationToolToCraftPlaceholder;按 ; 切分;仅 trim,不过滤空 entry
        val source = toolToCraft.ifEmpty { defaultToolToCraft(ctx.clientType) }
        val tools = source.replace('；', ';').split(';').map { it.trim() }

        val paramsJson = buildJsonObject {
            put("theme", theme)
            put("mode", sanitizedMode())
            put("increment_mode", incrementMode)
            put("num_craft_batches", maxCraftCountPerRound)
            putJsonArray("tools_to_craft") {
                tools.forEach { add(JsonPrimitive(it)) }
            }
            put("clear_store", clearStore)
        }
        return TaskParamResult(listOf(MaaTaskParams(MaaTaskType.RECLAMATION, paramsJson.toString())))
    }
}
