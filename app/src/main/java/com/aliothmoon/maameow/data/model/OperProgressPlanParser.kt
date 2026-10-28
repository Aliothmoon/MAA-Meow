package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.resource.CharacterInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

class OperProgressImportException(val textRes: Int, val operatorName: String = "") :
    IllegalArgumentException()

object OperProgressPlanParser {
    fun parse(
        text: String,
        operators: Collection<CharacterInfo>,
        existing: List<OperProgressPlan> = emptyList(),
    ): List<OperProgressPlan> {
        if (text.isBlank()) throw OperProgressImportException(R.string.oper_progress_import_empty)
        val array = runCatching { Json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?: throw OperProgressImportException(R.string.oper_progress_import_invalid)
        if (array.isEmpty()) throw OperProgressImportException(R.string.oper_progress_import_invalid)
        val seen = existing.map { it.key }.toMutableSet()
        return array.map { item ->
            val obj = item as? JsonObject
                ?: throw OperProgressImportException(R.string.oper_progress_import_invalid)
            if (obj.keys.any { it !in setOf("role", "name", "elite", "skill_level", "skill_mastery") }) {
                throw OperProgressImportException(R.string.oper_progress_import_invalid)
            }
            val name = (obj["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw OperProgressImportException(R.string.oper_progress_import_invalid)
            val role = if ("role" in obj) {
                (obj["role"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw OperProgressImportException(R.string.oper_progress_import_invalid)
            } else "Unknown"
            // 认不出的职业名（Guard、近卫等别名）按只给名字处理，对齐上游 WPF 导入
            val knownRole = operators.any { role.equals(it.coreRole, true) }
            val candidates = operators.filter { oper ->
                (!knownRole || role.equals(oper.coreRole, true)) &&
                    listOfNotNull(
                        oper.name,
                        oper.nameEn.takeUnless { oper.nameEnUnavailable },
                        oper.nameTw.takeUnless { oper.nameTwUnavailable },
                        oper.nameJp.takeUnless { oper.nameJpUnavailable },
                        oper.nameKr.takeUnless { oper.nameKrUnavailable },
                    ).any { it.equals(name, true) }
            }
            val oper = candidates.singleOrNull()?.takeIf { it.supportsOperProgress }
                ?: throw OperProgressImportException(R.string.oper_progress_import_unknown, name)
            fun number(key: String, range: IntRange): Int {
                if (key !in obj) return 0
                return (obj[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
                    ?.takeIf { it in range }
                    ?: throw OperProgressImportException(R.string.oper_progress_invalid_plan, name)
            }
            val mastery = if ("skill_mastery" in obj) {
                val values = obj["skill_mastery"] as? JsonArray
                    ?: throw OperProgressImportException(R.string.oper_progress_invalid_plan, name)
                if (values.size != 3) throw OperProgressImportException(R.string.oper_progress_invalid_plan, name)
                values.map { value ->
                    (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull?.takeIf { it in 0..3 }
                        ?: throw OperProgressImportException(R.string.oper_progress_invalid_plan, name)
                }
            } else listOf(0, 0, 0)
            val plan = OperProgressPlan(
                name = oper.name,
                role = oper.coreRole,
                elite = number("elite", 1..2),
                skillLevel = number("skill_level", 2..7),
                skillMastery = mastery,
            )
            if (!plan.hasTargets || !plan.isValidFor(oper)) {
                throw OperProgressImportException(R.string.oper_progress_invalid_plan, name)
            }
            if (!seen.add(plan.key)) throw OperProgressImportException(R.string.oper_progress_duplicate)
            plan
        }
    }
}
