package com.aliothmoon.maameow.data.model.copilot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 作业难度标志
 */
object DifficultyFlags {
    const val NONE = 0
    const val NORMAL = 1
    const val RAID = 2
}

/**
 * 作业文档信息
 */
@Serializable
data class CopilotDocumentation(
    @SerialName("title")
    val title: String = "",
    @SerialName("title_color")
    val titleColor: String = "dark",
    @SerialName("details")
    val details: String = "",
    @SerialName("details_color")
    val detailsColor: String = "dark"
)

/**
 * 干员需求
 */
@Serializable
data class CopilotOperatorRequirements(
    @SerialName("elite")
    val elite: Int = 0,
    @SerialName("level")
    val level: Int = 1,
    @SerialName("skill_level")
    val skillLevel: Int = 1,
    @SerialName("module")
    val module: Int = 0,
    @SerialName("potentiality")
    val potentiality: Int = 1
)

/**
 * 作业干员信息
 */
@Serializable
data class CopilotOperator(
    @SerialName("name")
    val name: String = "",
    @SerialName("skill")
    val skill: Int = 1,
    @SerialName("skill_usage")
    val skillUsage: Int = 0,
    @SerialName("skill_times")
    val skillTimes: Int = 0,
    @SerialName("requirements")
    val requirements: CopilotOperatorRequirements = CopilotOperatorRequirements()
)

/**
 * 干员组（备用干员组）
 */
@Serializable
data class CopilotGroup(
    @SerialName("name")
    val name: String = "",
    @SerialName("opers")
    val opers: List<CopilotOperator> = emptyList()
)

/**
 * 作业操作步骤
 */
@Serializable
data class CopilotAction(
    @SerialName("type")
    val type: String = "",
    @SerialName("name")
    val name: String = "",
    @SerialName("location")
    val location: List<Int> = emptyList(),
    @SerialName("direction")
    val direction: String = "",
    @SerialName("kills")
    val kills: Int = 0,
    @SerialName("costs")
    val costs: Int = 0,
    @SerialName("cost_changes")
    val costChanges: Int = 0,
    @SerialName("pre_delay")
    val preDelay: Int = 0,
    @SerialName("rear_delay")
    val rearDelay: Int = 0,
    @SerialName("timeout")
    val timeout: Int = 0,
    @SerialName("doc")
    val doc: String = "",
    @SerialName("doc_color")
    val docColor: String = "dark"
)

/**
 * MAA Copilot 作业数据
 */
@Serializable
data class CopilotTaskData(
    @SerialName("stage_name")
    val stageName: String = "",
    @SerialName("minimum_required")
    val minimumRequired: String = "",
    @SerialName("doc")
    val doc: CopilotDocumentation = CopilotDocumentation(),
    @SerialName("opers")
    val opers: List<CopilotOperator> = emptyList(),
    @SerialName("groups")
    val groups: List<CopilotGroup> = emptyList(),
    @SerialName("actions")
    val actions: List<CopilotAction> = emptyList(),
    @SerialName("difficulty")
    val difficulty: Int = DifficultyFlags.NONE
)
