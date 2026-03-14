package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.api.CopilotApiService
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.model.copilot.CopilotOperatorRequirements
import com.aliothmoon.maameow.data.model.copilot.CopilotTaskData
import com.aliothmoon.maameow.data.repository.CopilotRepository
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CopilotSetInfo(
    val id: Int,
    val name: String,
    val description: String,
    val copilotIds: List<Int>
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
    // ===== 作业解析 =====

    /**
     * 从 PRTS Plus ID 解析作业
     * 支持 "maa://1234", "1234", "maa://1234?list=1" 格式
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
            ?: return Result.failure(Exception("文件不存在: $filePath"))
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
    ): MaaTaskParams {
        val checkedItems = items.filter { it.isChecked }
        if (tabIndex == 2) {
            return MaaTaskParams(
                type = MaaTaskType.PARADOX_COPILOT,
                params = buildJsonObject {
                    put("list", buildJsonArray {
                        checkedItems.forEach { item ->
                            add(JsonPrimitive(item.filePath))
                        }
                    })
                }.toString()
            )
        }

        return MaaTaskParams(
            type = MaaTaskType.COPILOT,
            params = buildJsonObject {
                put("copilot_list", buildJsonArray {
                    checkedItems.forEach { item ->
                        add(buildJsonObject {
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
    }

    // ===== 工具方法 =====

    /**
     * 从输入字符串提取 copilot ID
     * 支持格式: "maa://1234", "1234", "maa://1234?list=1"
     */
    private fun extractCopilotId(input: String): Int? {
        val trimmed = input.trim()
        // maa://1234 or maa://1234?...
        val maaPrefix = "maa://"
        if (trimmed.startsWith(maaPrefix, ignoreCase = true)) {
            val idPart = trimmed.drop(maaPrefix.length).substringBefore("?").substringBefore("/")
            return idPart.toIntOrNull()
        }
        // Pure number
        return trimmed.toIntOrNull()
    }

    /**
     * 检查输入是否为作业集 ID (带 list 参数)
     */
    fun isSetId(input: String): Boolean {
        return input.trim().contains("list=", ignoreCase = true)
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
