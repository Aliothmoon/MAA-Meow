package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.CopilotApiService
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.model.copilot.CopilotOperatorRequirements
import com.aliothmoon.maameow.data.model.copilot.CopilotTaskData
import com.aliothmoon.maameow.data.repository.CopilotRepository
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CopilotSetInfo(
    val id: Int,
    val name: String,
    val description: String,
    val copilotIds: List<Int>
)

/** 作业站神秘代码类型 */
enum class CopilotCodeType {
    /** 单个作业 */
    COPILOT,

    /** 作业集 */
    COPILOT_SET,
}

/**
 * 解析后的作业站神秘代码
 * @param ambiguous 旧格式（maa://、纯数字）无法区分作业/作业集，type 仅为默认值，实际类型由调用方上下文决定
 */
data class CopilotCode(
    val type: CopilotCodeType,
    val id: Int,
    val ambiguous: Boolean = false,
)

sealed class CopilotRequestException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class InvalidInput(val rawInput: String, val isSet: Boolean) :
        CopilotRequestException("invalid input: $rawInput")

    class Network(val isSet: Boolean, val detail: String?, cause: Throwable? = null) :
        CopilotRequestException(detail ?: "network error", cause)

    class NotFound(
        val id: Int,
        val isSet: Boolean,
        val statusCode: Int,
        val apiMessage: String?
    ) : CopilotRequestException("not found: id=$id status=$statusCode message=$apiMessage")

    class JsonError(val isSet: Boolean, detail: String?, cause: Throwable? = null) :
        CopilotRequestException(detail ?: "json error", cause)
}

data class OperatorDisplayItem(
    val name: String,
    val tags: List<String>,
)

data class OperatorSummaryData(
    val operators: List<OperatorDisplayItem>,
    val groups: List<Pair<String, List<OperatorDisplayItem>>>,
    val totalCount: Int,
) {
    val isEmpty get() = totalCount == 0
}

class CopilotManager(
    private val apiService: CopilotApiService,
    private val repository: CopilotRepository,
) {
    companion object {
        /** 旧格式前缀，长期保留解析兼容（见 parseCopilotCode） */
        private const val LEGACY_ID_PREFIX = "maa://"

        /** 新格式前缀，prts://12345 为作业 */
        private const val NEW_ID_PREFIX = "prts://"

        /** 新格式作业集前缀，prts://s12345 为作业集 */
        private const val NEW_SET_ID_PREFIX = "prts://s"
    }

    // ===== 作业解析 =====

    /**
     * 从 PRTS Plus ID 解析作业
     * 支持 parseCopilotCode 的全部格式
     * @return Triple(copilotId, taskData, originalJsonContent)
     */
    suspend fun parseFromId(idString: String): Result<Triple<Int, CopilotTaskData, String>> {
        val trimmed = idString.trim()
        val id = extractCopilotId(trimmed)
            ?: return Result.failure(CopilotRequestException.InvalidInput(trimmed, isSet = false))
        val response = apiService.getCopilot(id).getOrElse {
            return Result.failure(
                CopilotRequestException.Network(
                    isSet = false,
                    detail = it.message,
                    cause = it
                )
            )
        }
        if (response.statusCode != 200 || response.data == null) {
            return Result.failure(
                CopilotRequestException.NotFound(
                    id = id,
                    isSet = false,
                    statusCode = response.statusCode,
                    apiMessage = response.message
                )
            )
        }
        val content = response.data.content
        if (content.isBlank()) {
            return Result.failure(
                CopilotRequestException.JsonError(
                    isSet = false,
                    detail = "empty content"
                )
            )
        }
        val taskData = parseJson(content).getOrElse {
            return Result.failure(
                CopilotRequestException.JsonError(
                    isSet = false,
                    detail = it.message,
                    cause = it
                )
            )
        }
        // Save to local file
        repository.saveCopilotJson(id, content)
        return Result.success(Triple(id, taskData, content))
    }

    /**
     * 从 JSON 字符串解析作业数据
     */
    fun parseJson(json: String): Result<CopilotTaskData> {
        return runCatching {
            JsonUtils.common.decodeFromString<CopilotTaskData>(json)
        }
    }

    /**
     * 从本地文件解析作业
     */
    suspend fun parseFromFile(filePath: String): Result<Pair<CopilotTaskData, String>> {
        val json = repository.readCopilotJson(filePath)
            ?: return Result.failure(LocalizedException(uiTextOf(R.string.copilot_file_read_error)))
        val taskData = parseJson(json).getOrElse { return Result.failure(it) }
        return Result.success(Pair(taskData, json))
    }

    // ===== 作业集导入 =====

    /**
     * 获取作业集中的所有作业 ID 列表
     */
    suspend fun getCopilotSetInfo(idString: String): Result<CopilotSetInfo> {
        val trimmed = idString.trim()
        val id = extractCopilotId(trimmed)
            ?: return Result.failure(CopilotRequestException.InvalidInput(trimmed, isSet = true))
        val response = apiService.getCopilotSet(id).getOrElse {
            return Result.failure(
                CopilotRequestException.Network(
                    isSet = true,
                    detail = it.message,
                    cause = it
                )
            )
        }
        if (response.statusCode != 200 || response.data == null) {
            return Result.failure(
                CopilotRequestException.NotFound(
                    id = id,
                    isSet = true,
                    statusCode = response.statusCode,
                    apiMessage = response.message
                )
            )
        }
        val data = response.data
        return Result.success(
            CopilotSetInfo(
                id = data.id,
                name = data.name,
                description = data.description,
                copilotIds = data.copilotIds
            )
        )
    }

    suspend fun getCopilotSetIds(idString: String): Result<List<Int>> {
        return getCopilotSetInfo(idString).map { it.copilotIds }
    }

    // ===== PRTS Plus 评分 =====

    /**
     * 评分作业
     */
    suspend fun rateCopilot(id: Int, isLike: Boolean): Boolean {
        val rating = if (isLike) "Like" else "Dislike"
        val result = apiService.rateCopilot(id, rating)
        return result.isSuccess
    }

    // ===== 任务参数构建 =====

    fun buildSingleTask(
        taskType: MaaTaskType,
        filePath: String,
        config: CopilotConfig
    ): MaaTaskParams {
        if (taskType == MaaTaskType.PARADOX_COPILOT) {
            return MaaTaskParams(
                type = MaaTaskType.PARADOX_COPILOT,
                params = buildJsonObject {
                    put("filename", filePath)
                }.toString()
            )
        }
        return MaaTaskParams(
            type = taskType,
            params = buildJsonObject {
                put("filename", filePath)
                put("formation", config.formation)
                put("support_unit_usage", if (config.useSupportUnit) config.supportUnitUsage else 0)
                put("add_trust", config.addTrust)
                put("ignore_requirements", config.ignoreRequirements)
                put("loop_times", if (config.loop) config.loopTimes else 1)
                put("use_sanity_potion", config.useSanityPotion)
                if (config.useFormation) {
                    // 与 WPF 一致：1~4 直接透传，0 表示不指定
                    put("formation_index", config.formationIndex)
                }
                put("user_additional", parseUserAdditional(config))
            }.toString()
        )
    }

    fun buildListTask(
        tabIndex: Int,
        items: List<CopilotListItem>,
        config: CopilotConfig
    ): List<MaaTaskParams> {
        val checkedItems = items.filter { it.isChecked }
        // 上游 #16985: 每个作业项携带其在完整列表中的稳定下标 id(从0起), core 据此回传当前执行项,
        // 用于跳过失败作业后仍能把"成功"归属到正确项。坐标系须与 onCopilotTaskSuccess 对全列表取下标一致。
        val indexed = items.withIndex().filter { it.value.isChecked }
        if (tabIndex == 1) { // TAB_SSS — MAA Core SSSCopilot 只接受单个 filename，逐个提交
            return checkedItems.map { item ->
                MaaTaskParams(
                    type = MaaTaskType.SSS_COPILOT,
                    params = buildJsonObject {
                        put("filename", item.filePath)
                    }.toString()
                )
            }
        }
        if (tabIndex == 2) { // TAB_PARADOX
            return listOf(
                MaaTaskParams(
                    type = MaaTaskType.PARADOX_COPILOT,
                    params = buildJsonObject {
                        // core CopilotConfig{ id, filename } 两字段均必填
                        put("list", buildJsonArray {
                            indexed.forEach { (i, item) ->
                                add(buildJsonObject {
                                    put("id", i)
                                    put("filename", item.filePath)
                                })
                            }
                        })
                    }.toString()
                )
            )
        }

        return listOf(
            MaaTaskParams(
                type = MaaTaskType.COPILOT,
                params = buildJsonObject {
                    put("copilot_list", buildJsonArray {
                        indexed.forEach { (i, item) ->
                            add(buildJsonObject {
                                put("id", i)
                                put("filename", item.filePath)
                                put("stage_name", item.name)
                                put("is_raid", item.isRaid)
                            })
                        }
                    })
                    put("formation", config.formation)
                    put("support_unit_usage", if (config.useSupportUnit) config.supportUnitUsage else 0)
                    put("add_trust", config.addTrust)
                    put("ignore_requirements", config.ignoreRequirements)
                    // 与 WPF 一致：战斗列表模式固定单次消费，不复用单作业循环次数配置
                    put("loop_times", 1)
                    put("use_sanity_potion", config.useSanityPotion)
                    if (config.useFormation) {
                        put("formation_index", config.formationIndex)
                    }
                    put("user_additional", parseUserAdditional(config))
                }.toString()
            )
        )
    }

    // ===== 工具方法 =====

    /**
     * 解析作业站神秘代码，识别所有已知格式并提取数字 ID
     * 支持格式: "prts://1234", "prts://s1234", "s1234", "maa://1234", "maa://1234?list=1", "1234"
     */
    fun parseCopilotCode(input: String): CopilotCode? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // 带前缀的格式（从长到短匹配，避免 prts://s 被 prts:// 抢先）
        if (trimmed.startsWith(NEW_SET_ID_PREFIX, ignoreCase = true)) {
            val id = trimmed.drop(NEW_SET_ID_PREFIX.length).toIntOrNull() ?: return null
            return CopilotCode(CopilotCodeType.COPILOT_SET, id)
        }
        if (trimmed.startsWith(NEW_ID_PREFIX, ignoreCase = true)) {
            val id = trimmed.drop(NEW_ID_PREFIX.length).toIntOrNull() ?: return null
            return CopilotCode(CopilotCodeType.COPILOT, id)
        }
        // 支持 maa:// 旧格式
        if (trimmed.startsWith(LEGACY_ID_PREFIX, ignoreCase = true)) {
            val rest = trimmed.drop(LEGACY_ID_PREFIX.length)
            val id = rest.substringBefore("?").substringBefore("/").toIntOrNull() ?: return null
            // maa://1234?list=1 为既有的作业集约定；无参数时类型不明确，默认单个作业
            return if (rest.contains("list=", ignoreCase = true)) {
                CopilotCode(CopilotCodeType.COPILOT_SET, id)
            } else {
                CopilotCode(CopilotCodeType.COPILOT, id, ambiguous = true)
            }
        }
        // s1234 格式作业集
        if (trimmed.length > 1 && (trimmed[0] == 's' || trimmed[0] == 'S')) {
            trimmed.drop(1).toIntOrNull()?.let {
                return CopilotCode(CopilotCodeType.COPILOT_SET, it)
            }
        }
        // 纯数字，类型不明确，默认单个作业
        return trimmed.toIntOrNull()?.let {
            CopilotCode(CopilotCodeType.COPILOT, it, ambiguous = true)
        }
    }

    /**
     * 从输入字符串提取 copilot ID（不区分作业/作业集，路由由调用方负责）
     */
    private fun extractCopilotId(input: String): Int? {
        return parseCopilotCode(input)?.id
    }

    /**
     * 获取干员摘要（结构化）
     * 对齐 WPF CopilotModel.Output() 的展示逻辑
     */
    fun getOperatorSummary(data: CopilotTaskData): OperatorSummaryData {
        val operators = data.opers.map { oper ->
            val req = oper.requirements
            OperatorDisplayItem(
                name = oper.name,
                tags = buildOperatorTags(req, skill = oper.skill, showLevel = true)
            )
        }

        val groups = data.groups.map { group ->
            val groupOpers = group.opers.map { oper ->
                val req = oper.requirements
                OperatorDisplayItem(
                    name = oper.name,
                    tags = buildOperatorTags(req, skill = oper.skill, showLevel = false)
                )
            }
            group.name to groupOpers
        }

        return OperatorSummaryData(
            operators = operators,
            groups = groups,
            totalCount = operators.size + groups.size
        )
    }

    private fun buildOperatorTags(
        req: CopilotOperatorRequirements?,
        skill: Int,
        showLevel: Boolean
    ): List<String> {
        val tags = mutableListOf<String>()
        if (showLevel && req != null && (req.elite > 0 || req.level > 0)) {
            tags.add("精 ${req.elite} ${req.level}")
        }
        tags.add("技能 $skill")
        if (req != null && req.skillLevel in 1..10) {
            tags.add("技能 Lv.${req.skillLevel}")
        }
        if (req != null && req.module >= 0) {
            val moduleNames = arrayOf("χ", "γ", "α", "Δ")
            when (req.module) {
                0 -> tags.add("无模组")
                in 1..4 -> tags.add("模组 ${moduleNames[req.module - 1]}")
            }
        }
        return tags
    }

    private fun parseUserAdditional(config: CopilotConfig): JsonElement {
        // TODO: 恢复并重构“追加自定义干员”逻辑。
        return JsonArray(emptyList())
    }
}
