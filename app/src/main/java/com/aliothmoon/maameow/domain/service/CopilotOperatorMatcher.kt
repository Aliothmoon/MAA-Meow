package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.model.copilot.CopilotGroup
import com.aliothmoon.maameow.data.model.copilot.CopilotOperator
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator

/** 顺序即界面顺序 */
enum class OperatorMatchMode { READY, BORROW, TRAIN, BLOCKED }

data class OperatorMatch(
    val mode: OperatorMatchMode,
    /** 干员组记组名 */
    val missing: List<String>,
    val training: List<String>,
)

/**
 * 判定对齐 zoot-plus-frontend models/operatorMatcher.ts
 *
 * 没有技能与模组数据的干员（Core 识别结果）只比精英化与等级
 */
object CopilotOperatorMatcher {

    /** 12 名自带 + 1 名助战 */
    private const val SQUAD_LIMIT = 13

    /** 按星级，各精英阶段的等级上限 */
    private val MAX_LEVELS = mapOf(
        3 to intArrayOf(40, 55, 0),
        4 to intArrayOf(45, 60, 70),
        5 to intArrayOf(50, 70, 80),
        6 to intArrayOf(50, 80, 90),
    )
    private val LOW_RARITY_MAX_LEVELS = intArrayOf(30, 0, 0)

    /** 作业 module 1..5 对应的模组分支 */
    private val MODULE_TYPES = arrayOf("X", "Y", "A", "D", "B")

    /** [find] 未拥有时返回 null */
    fun match(
        opers: List<CopilotOperator>,
        groups: List<CopilotGroup>,
        find: (CopilotOperator) -> OperBoxOperator?,
    ): OperatorMatch {
        val missing = mutableListOf<String>()
        val training = mutableListOf<String>()

        for (oper in opers) {
            val owned = find(oper)
            when {
                owned == null -> missing += oper.name
                !meets(oper, owned) -> training += oper.name
            }
        }
        for (group in groups) {
            val candidates = group.opers.mapNotNull { oper -> find(oper)?.let { oper to it } }
            val label = group.name.ifBlank { group.opers.joinToString(" / ") { it.name } }
            when {
                candidates.any { (oper, owned) -> meets(oper, owned) } -> Unit
                candidates.isNotEmpty() -> training += label
                else -> missing += label
            }
        }

        val total = opers.size + groups.size
        val mode = when {
            total > SQUAD_LIMIT || missing.size >= 2 -> OperatorMatchMode.BLOCKED
            missing.size == 1 -> if (training.isEmpty()) OperatorMatchMode.BORROW else OperatorMatchMode.TRAIN
            training.isEmpty() -> if (total == SQUAD_LIMIT) OperatorMatchMode.BORROW else OperatorMatchMode.READY
            // 只差一名练度时借个助战顶上
            training.size == 1 && total < SQUAD_LIMIT -> OperatorMatchMode.BORROW
            else -> OperatorMatchMode.TRAIN
        }
        return OperatorMatch(mode, missing, training)
    }

    internal fun meets(oper: CopilotOperator, owned: OperBoxOperator): Boolean {
        val req = oper.requirements ?: return true

        if (req.elite > 0 || req.level > 0) {
            val level = req.level.coerceAtLeast(1)
            // 只写了等级时按等级反推精英阶段
            val elite = if (req.elite > 0) req.elite else inferElite(owned.rarity, level)
            if (progress(owned.rarity, owned.elite, owned.level) < progress(owned.rarity, elite, level)) {
                return false
            }
        }

        if (!owned.hasTrainingData) return true

        if (req.skillLevel >= 8) {
            if (owned.skills.isNotEmpty()) {
                val mastery = owned.skills.getOrNull(oper.skill - 1)?.level ?: 0
                if (mastery < req.skillLevel - 7) return false
            }
        } else if (req.skillLevel > 0) {
            val main = owned.mainSkillLevel
            if (main != null && main < req.skillLevel) return false
        }

        val moduleType = MODULE_TYPES.getOrNull(req.module - 1)
        if (moduleType != null && owned.equips.none { it.type == moduleType && it.level >= 1 }) {
            return false
        }
        return true
    }

    private fun maxLevels(rarity: Int) = MAX_LEVELS[rarity.coerceAtMost(6)] ?: LOW_RARITY_MAX_LEVELS

    /** 精英化折成累计等级，跨阶段可比 */
    private fun progress(rarity: Int, elite: Int, level: Int): Int {
        val caps = maxLevels(rarity)
        return level.coerceAtLeast(1) + (0 until elite).sumOf { caps.getOrElse(it) { 0 } }
    }

    private fun inferElite(rarity: Int, level: Int): Int {
        val caps = maxLevels(rarity)
        var elite = 0
        while (elite < caps.size - 1 && level > caps[elite]) elite++
        return elite
    }
}
