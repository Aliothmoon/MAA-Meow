package com.aliothmoon.maameow.data.model.toolbox

import kotlinx.serialization.Serializable

/**
 * 公招识别结果 —— 一组标签组合对应的干员列表
 */
data class RecruitCalcResult(
    val tags: List<String>,
    val level: Int,
    val operators: List<RecruitOperator>,
)

data class RecruitOperator(
    val name: String,
    val level: Int,
)

/**
 * 仓库物品
 */
data class DepotItem(
    val id: String,
    val count: Int,
)

/**
 * 干员识别结果（可持久化）
 */
@Serializable
data class OperBoxOperator(
    val id: String,
    val name: String,
    val rarity: Int,
    val elite: Int,
    val level: Int,
    val potential: Int,
    val own: Boolean,
)
