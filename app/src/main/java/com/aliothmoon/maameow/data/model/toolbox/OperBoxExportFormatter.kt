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
    val mainSkillLevel: String,
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
                    op.mainSkillLevel?.let { put("mainSkillLevel", it) }
                    if (op.skills.isNotEmpty()) put("skills", formatSkills(op))
                    if (op.equips.isNotEmpty()) put("equips", formatEquips(op))
                })
            }
        }
        return prettyJson.encodeToString(JsonArray.serializer(), arr)
    }

    fun toMarkdown(opers: List<OperBoxOperator>, labels: OperBoxExportLabels): String {
        val header = baseLabels(labels) + trainingLabels(labels, opers)
        val sb = StringBuilder()
        sb.append(markdownRow(header))
        sb.append(markdownRow(List(header.size) { ":--" }))
        opers.forEach { sb.append(markdownRow(cellsOf(it, labels, header.size))) }
        return sb.toString().trimEnd('\n')
    }

    fun toCsv(opers: List<OperBoxOperator>, labels: OperBoxExportLabels): String {
        val header = baseLabels(labels) + trainingLabels(labels, opers)
        val sb = StringBuilder()
        sb.append(header.joinToString(",")).append('\n')
        opers.forEach { op ->
            sb.append(cellsOf(op, labels, header.size).joinToString(",") { csvEscape(it) })
                .append('\n')
        }
        return sb.toString().trimEnd('\n')
    }

    private fun markdownRow(cells: List<String>) = cells.joinToString(" | ", "| ", " |\n")

    private fun baseLabels(labels: OperBoxExportLabels) = with(labels) {
        listOf(name, id, rarity, elite, level, own, potential)
    }

    /** 练度三列只在有一图流数据时出现 */
    private fun trainingLabels(labels: OperBoxExportLabels, opers: List<OperBoxOperator>) =
        if (opers.hasTraining()) listOf(labels.mainSkillLevel, labels.skills, labels.equips)
        else emptyList()

    private fun cellsOf(op: OperBoxOperator, labels: OperBoxExportLabels, columns: Int): List<String> {
        val base = listOf(
            op.name, op.id, op.rarity.toString(), op.elite.toString(), op.level.toString(),
            if (op.own) labels.yes else labels.no, op.potential.toString()
        )
        if (columns == base.size) return base
        return base + listOf(
            op.mainSkillLevel?.toString().orEmpty(), formatSkills(op), formatEquips(op)
        )
    }


    private fun List<OperBoxOperator>.hasTraining(): Boolean = any { it.hasTrainingData }

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
