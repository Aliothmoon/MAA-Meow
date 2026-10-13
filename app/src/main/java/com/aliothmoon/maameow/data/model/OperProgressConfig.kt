package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.Locale
import java.util.UUID

@Serializable
data class OperProgressConfig(
    val plans: List<OperProgressPlan> = emptyList(),
) : TaskParamProvider {
    override fun toTaskParams(ctx: TaskParamContext): List<MaaTaskParams> {
        val active = plans.filter { it.hasTargets }
        if (active.isEmpty()) {
            ctx.appendLog(uiTextOf(R.string.oper_progress_empty))
            return emptyList()
        }
        if (active.map { it.key }.distinct().size != active.size) {
            ctx.appendLog(uiTextOf(R.string.oper_progress_duplicate), LogLevel.ERROR)
            return emptyList()
        }
        val operators = ctx.resourceDataManager.operators.value.values
        // 元数据等待超时时表可能为空，交给 core 校验
        val invalid = if (operators.isEmpty()) null else active.firstOrNull { plan ->
            val oper = operators.firstOrNull { it.name == plan.name && it.coreRole == plan.role }
            oper == null || !plan.isValidFor(oper)
        }
        if (invalid != null) {
            ctx.appendLog(uiTextOf(R.string.oper_progress_invalid_plan, invalid.name), LogLevel.ERROR)
            return emptyList()
        }
        val params = buildJsonObject {
            put("plans", JsonArray(active.map { plan ->
                buildJsonObject {
                    put("role", JsonPrimitive(plan.role))
                    put("name", JsonPrimitive(plan.name))
                    if (plan.elite > 0) put("elite", JsonPrimitive(plan.elite))
                    if (plan.skillLevel > 0) put("skill_level", JsonPrimitive(plan.skillLevel))
                    if (plan.skillMastery.any { it > 0 }) {
                        put("skill_mastery", JsonArray(plan.skillMastery.map(::JsonPrimitive)))
                    }
                }
            }))
        }
        return listOf(MaaTaskParams(
            MaaTaskType.OPER_PROGRESS,
            params.toString(),
            logName = UiText.Dynamic(ctx.node.name),
        ))
    }

    fun applyResults(results: List<OperProgressResult>, removeFinished: Boolean = false): OperProgressConfig {
        val updated = plans.map { original ->
            results.filter { it.name == original.name && it.role == original.role }
                .fold(original) { plan, result ->
                    val mastery = plan.skillMastery.mapIndexed { index, target ->
                        if (!result.trainingStarted && (result.skillMastery?.getOrNull(index) ?: 0) >= target) 0
                        else target
                    }
                    val training = if (result.trainingStarted) {
                        result.skillMastery?.withIndex()?.firstOrNull { it.value > 0 }
                            ?.let { OperProgressTraining(it.index + 1, it.value) } ?: plan.training
                    } else {
                        plan.training?.takeUnless {
                            mastery.none { target -> target > 0 } ||
                                (result.skillMastery?.getOrNull(it.skill - 1) ?: 0) >= it.level
                        }
                    }
                    plan.copy(
                        elite = if ((result.elite ?: 0) >= plan.elite) 0 else plan.elite,
                        skillLevel = if ((result.skillLevel ?: 0) >= plan.skillLevel) 0 else plan.skillLevel,
                        skillMastery = mastery,
                        training = training,
                    )
                }
        }
        return copy(plans = if (removeFinished) updated.filter { it.hasTargets } else updated)
    }
}

@Serializable
data class OperProgressPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String,
    val elite: Int = 0,
    val skillLevel: Int = 0,
    val skillMastery: List<Int> = listOf(0, 0, 0),
    val training: OperProgressTraining? = null,
) {
    val key get() = role to name
    val hasTargets get() = elite > 0 || skillLevel > 0 || skillMastery.any { it > 0 }

    fun isValidFor(oper: CharacterInfo): Boolean =
        oper.supportsOperProgress && name == oper.name && role == oper.coreRole &&
            elite in 0..oper.maxProgressElite && (skillLevel == 0 || skillLevel in 2..7) &&
            skillMastery.size == 3 && skillMastery.all { it in 0..3 } &&
            skillMastery.withIndex().all { (index, level) ->
                level == 0 || (oper.rarity >= 4 && index < oper.progressSkillCount)
            } && !(skillLevel in 2..6 && skillMastery.any { it > 0 })

    fun withSkillLevel(level: Int): OperProgressPlan = copy(
        skillLevel = level,
        skillMastery = if (level in 2..6) listOf(0, 0, 0) else skillMastery,
        training = null,
    )

    fun withMastery(index: Int, level: Int): OperProgressPlan = copy(
        skillMastery = List(3) { if (it == index) level else skillMastery.getOrElse(it) { 0 } },
        skillLevel = if (level > 0 && skillLevel in 2..6) 7 else skillLevel,
        training = null,
    )

    companion object {
        fun forOperator(oper: CharacterInfo): OperProgressPlan = OperProgressPlan(
            name = oper.name,
            role = oper.coreRole,
            elite = oper.maxProgressElite,
            skillLevel = 7,
            skillMastery = List(3) { if (oper.rarity >= 4 && it < oper.progressSkillCount) 3 else 0 },
        )
    }
}

@Serializable
data class OperProgressTraining(val skill: Int, val level: Int)

data class OperProgressResult(
    val name: String,
    val role: String,
    val elite: Int? = null,
    val skillLevel: Int? = null,
    val skillMastery: List<Int>? = null,
    val trainingStarted: Boolean = false,
)

val CharacterInfo.coreRole: String
    get() = profession.orEmpty().lowercase(Locale.ROOT).replaceFirstChar { it.uppercaseChar() }

val CharacterInfo.supportsOperProgress: Boolean
    get() = isOperator && id.startsWith("char_") && rarity >= 3 &&
        id !in setOf("char_002_amiya", "char_1001_amiya2", "char_1037_amiya3")

val CharacterInfo.maxProgressElite: Int get() = if (rarity >= 4) 2 else 1
val CharacterInfo.progressSkillCount: Int get() = when (rarity) {
    6 -> 3
    4, 5 -> 2
    3 -> 1
    else -> 0
}
