package com.aliothmoon.maameow.data.model.toolbox

import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class OperBoxExportLabels(
    val name: String,
    val id: String,
    val rarity: String,
    val elite: String,
    val level: String,
    val own: String,
    val potential: String,
    val yes: String,
    val no: String,
    val skills: String,
    val equips: String,
)


object OperBoxExportFormatter {

    private val prettyJson = Json(JsonUtils.common) { prettyPrint = true }

    fun toJson(opers: List<OperBoxOperator>): String {
        val arr = buildJsonArray {
            opers.forEach { op ->
                add(buildJsonObject {
                    put("id", op.id)
                    put("name", op.name)
                    put("own", op.own)
                    put("rarity", op.rarity)
                    put("elite", op.elite)
                    put("level", op.level)
                    put("potential", op.potential)
                    if (op.skills.isNotEmpty()) put("skills", formatSkills(op))
                    if (op.equips.isNotEmpty()) put("equips", formatEquips(op))
                })
            }
        }
        return prettyJson.encodeToString(JsonArray.serializer(), arr)
    }

    fun toMarkdown(opers: List<OperBoxOperator>, labels: OperBoxExportLabels): String {
        val withTraining = opers.hasTraining()
        val sb = StringBuilder()
        val extraHeader = if (withTraining) " ${labels.skills} | ${labels.equips} |" else ""
        val extraAlign = if (withTraining) " :-- | :-- |" else ""
        sb.append("| ${labels.name} | ${labels.id} | ${labels.rarity} | ${labels.elite} | ${labels.level} | ${labels.own} | ${labels.potential} |$extraHeader\n")
        sb.append("| :-- | :-- | :-- | :-- | :-- | :-- | :-- |$extraAlign\n")
        opers.forEach { op ->
            val own = if (op.own) labels.yes else labels.no
            val extra = if (withTraining) " ${formatSkills(op)} | ${formatEquips(op)} |" else ""
            sb.append("| ${op.name} | ${op.id} | ${op.rarity} | ${op.elite} | ${op.level} | $own | ${op.potential} |$extra\n")
        }
        return sb.toString().trimEnd('\n')
    }

    fun toCsv(opers: List<OperBoxOperator>, labels: OperBoxExportLabels): String {
        val withTraining = opers.hasTraining()
        val sb = StringBuilder()
        val extraHeader = if (withTraining) ",${labels.skills},${labels.equips}" else ""
        sb.append("${labels.name},${labels.id},${labels.rarity},${labels.elite},${labels.level},${labels.own},${labels.potential}$extraHeader\n")
        opers.forEach { op ->
            val own = if (op.own) labels.yes else labels.no
            val extra = if (withTraining) ",${csvEscape(formatSkills(op))},${csvEscape(formatEquips(op))}" else ""
            sb.append("${csvEscape(op.name)},${op.id},${op.rarity},${op.elite},${op.level},$own,${op.potential}$extra\n")
        }
        return sb.toString().trimEnd('\n')
    }

    /** 只有一图流数据带专精与模组 */
    private fun List<OperBoxOperator>.hasTraining(): Boolean =
        any { it.skills.isNotEmpty() || it.equips.isNotEmpty() }

    /** 专精按技能顺序拼，如 3/3/0 */
    private fun formatSkills(op: OperBoxOperator): String =
        op.skills.joinToString("/") { it.level.toString() }

    /** 模组拼分支加等级，如 X3 Y1；未解锁的不列 */
    private fun formatEquips(op: OperBoxOperator): String =
        op.equips.filter { it.level > 0 }.joinToString(" ") { "${it.type}${it.level}" }

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
