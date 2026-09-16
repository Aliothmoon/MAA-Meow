package com.aliothmoon.maameow.data.model.toolbox

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OperBoxOperator(
    val id: String,
    val name: String,
    val rarity: Int,
    val elite: Int,
    val level: Int,
    val potential: Int,
    val own: Boolean,
    // 全局 encodeDefaults 为真，不排除会给每个干员写两个空数组
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val skills: List<OperBoxSkill> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val equips: List<OperBoxEquip> = emptyList(),
)

/** 技能专精，[level] 0–3 */
@Serializable
data class OperBoxSkill(
    val id: String,
    val level: Int,
)

/** 模组，[type] 为分支 X / Y */
@Serializable
data class OperBoxEquip(
    val id: String,
    val type: String,
    val level: Int,
)
