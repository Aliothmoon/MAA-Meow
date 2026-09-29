package com.aliothmoon.maameow.domain.service.copilot

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 作业校正，对齐 MaaWpfGui CopilotViewModel 的技能/精英化校验与动作冗余字段清理
 *
 * 直接在 JSON 树上定点改，不走 typed model 回写
 * 反序列化配了 ignoreUnknownKeys，套回去会把作业里未建模的字段全吃掉
 */
object CopilotRequirementCorrector {

    /** 校正用到的最小干员数据，domain 不依赖 data 层模型 */
    data class OperatorInfo(val rarity: Int, val id: String)

    /** 阿米娅五星有三技能，上游按 id 放行 */
    private const val AMIYA_ID = "char_002_amiya"

    /** 同填干员与坐标时 Core 按坐标执行的动作，上游只认这几个写法，别名不管 */
    private val LOCATION_OVER_OPER_TYPES = setOf("Skill", "Retreat", "BulletTime", "SkillUsage")

    enum class Kind {
        /** 稀有度不够，取消技能选择 */
        UNSUPPORTED_SKILL,

        /** 没写精英化要求，按其他要求补一个 */
        ELITE_FILLED,

        /** 精英化要求偏低，上调 */
        ELITE_RAISED,

        /** 同填干员与坐标，去掉干员和职业，按坐标执行 */
        LOCATION_OVER_OPER,

        /** 点击同填像素区域与坐标，去掉坐标，按区域执行 */
        RECT_OVER_LOCATION,
    }

    /**
     * [target] 为干员名，动作类校正为「类型[坐标或区域]」
     * [from] 为技能号或原精英化等级，[to] 为修正后的精英化等级，动作类校正不用
     */
    data class Correction(val kind: Kind, val target: String, val from: Int = 0, val to: Int = 0)

    class Result(val json: String, val corrections: List<Correction>) {
        /** 补空不算改动过作业，与上游 is_corrected 的口径一致 */
        val altered: Boolean = corrections.any { it.kind != Kind.ELITE_FILLED }
    }

    /** [lookup] 查不到干员时返回 null */
    fun correct(source: String, lookup: (String) -> OperatorInfo?): Result {
        val root = runCatching { Json.parseToJsonElement(source) as? JsonObject }.getOrNull()
            ?: return Result(source, emptyList())

        val operCorrections = mutableListOf<Correction>()
        val actionCorrections = mutableListOf<Correction>()
        val corrected = buildJsonObject {
            root.forEach { (key, value) ->
                when (key) {
                    "opers" -> put(key, correctOpers(value, lookup, operCorrections))
                    "groups" -> put(key, correctGroups(value, lookup, operCorrections))
                    "actions" -> put(key, correctActions(value, actionCorrections))
                    else -> put(key, value)
                }
            }
        }
        // 提示顺序同上游：先干员后动作，不随 JSON 键序变
        val corrections = operCorrections + actionCorrections
        if (corrections.isEmpty()) return Result(source, emptyList())
        return Result(Json.encodeToString(JsonObject.serializer(), corrected), corrections)
    }

    private fun correctActions(element: JsonElement, out: MutableList<Correction>): JsonElement {
        val actions = element as? JsonArray ?: return element
        return buildJsonArray {
            actions.forEach { action ->
                add((action as? JsonObject)?.let { correctAction(it, out) } ?: action)
            }
        }
    }

    /** Core 本就按坐标 / 区域优先执行，这里删掉被覆盖的字段让作业与实际行为一致 */
    private fun correctAction(action: JsonObject, out: MutableList<Correction>): JsonObject {
        val type = (action["type"] as? JsonPrimitive)?.contentOrNull
        val removed = when {
            type in LOCATION_OVER_OPER_TYPES && action.has("location") && action.has("name") -> {
                out += Correction(Kind.LOCATION_OVER_OPER, "$type[${action.format("location")}]")
                setOf("name", "role")
            }

            type == "Click" && action.has("rect") && action.has("location") -> {
                out += Correction(Kind.RECT_OVER_LOCATION, "$type[${action.format("rect")}]")
                setOf("location")
            }

            else -> return action
        }
        return JsonObject(action.filterKeys { it !in removed })
    }

    /** 显式写 null 等同没写，同上游可空字段的反序列化口径 */
    private fun JsonObject.has(key: String): Boolean = this[key].let { it != null && it !is JsonNull }

    private fun JsonObject.format(key: String): String =
        (this[key] as? JsonArray)?.joinToString(",") ?: this[key].toString()

    private fun correctGroups(
        element: JsonElement,
        lookup: (String) -> OperatorInfo?,
        out: MutableList<Correction>,
    ): JsonElement {
        val groups = element as? JsonArray ?: return element
        return buildJsonArray {
            groups.forEach { group ->
                val obj = group as? JsonObject
                if (obj == null) {
                    add(group)
                    return@forEach
                }
                add(
                    buildJsonObject {
                        obj.forEach { (key, value) ->
                            if (key == "opers") put(
                                key,
                                correctOpers(value, lookup, out)
                            ) else put(key, value)
                        }
                    }
                )
            }
        }
    }

    private fun correctOpers(
        element: JsonElement,
        lookup: (String) -> OperatorInfo?,
        out: MutableList<Correction>,
    ): JsonElement {
        val opers = element as? JsonArray ?: return element
        return buildJsonArray {
            opers.forEach { oper ->
                add((oper as? JsonObject)?.let { correctOper(it, lookup, out) } ?: oper)
            }
        }
    }

    private fun correctOper(
        oper: JsonObject,
        lookup: (String) -> OperatorInfo?,
        out: MutableList<Correction>,
    ): JsonObject {
        val name = oper["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (name.isBlank()) return oper

        // 作业不写 skill 时上游默认 1 技能
        val skill = oper["skill"]?.jsonPrimitive?.intOrNull ?: 1
        val info = lookup(name)
        val rarity = info?.rarity ?: -1
        var newSkill = skill
        if ((skill == 3 && rarity < 6 && info?.id != AMIYA_ID) || (skill == 2 && rarity < 4) || (skill == 1 && rarity < 3)) {
            newSkill = 0
            out += Correction(Kind.UNSUPPORTED_SKILL, name, skill, 0)
        }

        val requirements = oper["requirements"] as? JsonObject
        // 二技能要精 1、三技能要精 2；专精和模组都要精 2
        val skillElite = newSkill - 1
        val skillLevel = requirements?.get("skill_level")?.jsonPrimitive?.intOrNull
        val skillLevelElite = when {
            skillLevel == null || skillLevel <= 4 -> 0
            skillLevel <= 7 -> 1
            skillLevel <= 10 -> 2
            else -> 0
        }
        val moduleElite =
            if ((requirements?.get("module")?.jsonPrimitive?.intOrNull ?: 0) > 0) 2 else 0
        val required = maxOf(skillElite, skillLevelElite, moduleElite)

        var newElite: Int? = null
        if (required > 0) {
            val elite = requirements?.get("elite")?.jsonPrimitive?.intOrNull
            if (elite == null) {
                newElite = required
                out += Correction(Kind.ELITE_FILLED, name, 0, required)
            } else if (elite < required) {
                newElite = required
                out += Correction(Kind.ELITE_RAISED, name, elite, required)
            }
        }

        if (newSkill == skill && newElite == null) return oper
        return buildJsonObject {
            oper.forEach { (key, value) -> put(key, value) }
            if (newSkill != skill) put("skill", newSkill)
            newElite?.let { elite ->
                put(
                    "requirements",
                    buildJsonObject {
                        requirements?.forEach { (key, value) -> put(key, value) }
                        put("elite", elite)
                    }
                )
            }
        }
    }
}
