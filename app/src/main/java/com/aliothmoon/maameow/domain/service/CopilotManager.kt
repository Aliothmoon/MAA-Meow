package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.api.CopilotApiService
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.model.copilot.CopilotTaskData
import com.aliothmoon.maameow.data.repository.CopilotRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

class CopilotManager(
    private val apiService: CopilotApiService,
    private val repository: CopilotRepository,
) {
    companion object {
        private const val TAG = "CopilotManager"
    }

    // ===== 作业解析 =====

    /**
     * 从 PRTS Plus ID 解析作业
     * 支持 "maa://1234", "1234", "maa://1234?list=1" 格式
     * @return Triple(copilotId, taskData, originalJsonContent)
     */
    suspend fun parseFromId(idString: String): Result<Triple<Int, CopilotTaskData, String>> {
        val id = extractCopilotId(idString)
            ?: return Result.failure(IllegalArgumentException("无效的作业 ID: $idString"))
        val response = apiService.getCopilot(id).getOrElse { return Result.failure(it) }
        if (response.statusCode != 200 || response.data == null) {
            return Result.failure(Exception("获取作业失败: status=${response.statusCode}"))
        }
        val content = response.data.content
        val taskData = parseJson(content).getOrElse { return Result.failure(it) }
        // Save to local file
        repository.saveCopilotJson(id, content)
        return Result.success(Triple(id, taskData, content))
    }

    /**
     * 从 JSON 字符串解析作业数据
     */
    fun parseJson(json: String): Result<CopilotTaskData> {
        return runCatching {
            HttpClientHelper.httpJson.decodeFromString<CopilotTaskData>(json)
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
    suspend fun getCopilotSetIds(idString: String): Result<List<Int>> {
        val id = extractCopilotId(idString)
            ?: return Result.failure(IllegalArgumentException("无效的作业集 ID"))
        val response = apiService.getCopilotSet(id).getOrElse { return Result.failure(it) }
        if (response.statusCode != 200 || response.data == null) {
            return Result.failure(Exception("获取作业集失败: status=${response.statusCode}"))
        }
        return Result.success(response.data.copilotIds)
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

    /**
     * 构建单作业模式的 MAA Core 参数 JSON
     *
     * MAA Core 期望格式:
     * {
     *   "filename": "/path/to/copilot.json",
     *   "formation": true,
     *   "support_unit_usage": 1,
     *   "add_trust": false,
     *   "ignore_requirements": false,
     *   "loop_times": 1,
     *   "use_sanity_potion": false,
     *   "formation_index": 0,
     *   "user_additional": [{"name":"XX","skill":2,"module":0}]
     * }
     */
    fun buildSingleTaskParams(filePath: String, config: CopilotConfig): String {
        return buildJsonObject {
            put("filename", filePath)
            put("formation", config.formation)
            put("support_unit_usage", if (config.useSupportUnit) config.supportUnitUsage else 0)
            put("add_trust", config.addTrust)
            put("ignore_requirements", config.ignoreRequirements)
            put("loop_times", if (config.loop) config.loopTimes else 1)
            put("use_sanity_potion", config.useSanityPotion)
            if (config.useFormation) {
                put("formation_index", config.formationIndex - 1) // UI 1-based -> Core 0-based
            }
            if (config.addUserAdditional && config.userAdditional.isNotBlank()) {
                // Parse user additional JSON array
                try {
                    val arr = HttpClientHelper.httpJson.parseToJsonElement(config.userAdditional)
                    put("user_additional", arr)
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: 自定义干员 JSON 解析失败")
                    put("user_additional", JsonArray(emptyList()))
                }
            } else {
                put("user_additional", JsonArray(emptyList()))
            }
        }.toString()
    }

    /**
     * 构建多作业列表模式的 MAA Core 参数 JSON
     *
     * MAA Core 期望格式:
     * {
     *   "copilot_list": [
     *     {"filename": "/path/1.json", "stage_name": "1-7", "is_raid": false},
     *     ...
     *   ],
     *   "formation": true,
     *   ...
     * }
     */
    fun buildListTaskParams(items: List<CopilotListItem>, config: CopilotConfig): String {
        val checkedItems = items.filter { it.isChecked }
        return buildJsonObject {
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
            put("use_sanity_potion", config.useSanityPotion)
            if (config.useFormation) {
                put("formation_index", config.formationIndex - 1)
            }
            if (config.addUserAdditional && config.userAdditional.isNotBlank()) {
                try {
                    val arr = HttpClientHelper.httpJson.parseToJsonElement(config.userAdditional)
                    put("user_additional", arr)
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: 自定义干员 JSON 解析失败")
                    put("user_additional", JsonArray(emptyList()))
                }
            } else {
                put("user_additional", JsonArray(emptyList()))
            }
        }.toString()
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
     * 获取干员摘要文本
     */
    fun getOperatorSummary(data: CopilotTaskData): String {
        val parts = mutableListOf<String>()
        if (data.opers.isNotEmpty()) {
            parts.add("干员: ${data.opers.joinToString(", ") { "${it.name}(技能${it.skill})" }}")
        }
        if (data.groups.isNotEmpty()) {
            parts.add("备选组: ${data.groups.joinToString(", ") { it.name }}")
        }
        return parts.joinToString("\n")
    }
}
