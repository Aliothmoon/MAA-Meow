package com.aliothmoon.maameow.data.model.toolbox

import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
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
                })
            }
        }
        return prettyJson.encodeToString(JsonArray.serializer(), arr)
    }

    fun toMarkdown(opers: List<OperBoxOperator>, labels: OperBoxExportLabels): String {
        val sb = StringBuilder()
        sb.append("| ${labels.name} | ${labels.id} | ${labels.rarity} | ${labels.elite} | ${labels.level} | ${labels.own} | ${labels.potential} |\n")
        sb.append("| :-- | :-- | :-- | :-- | :-- | :-- | :-- |\n")
        opers.forEach { op ->
            val own = if (op.own) labels.yes else labels.no
            sb.append("| ${op.name} | ${op.id} | ${op.rarity} | ${op.elite} | ${op.level} | $own | ${op.potential} |\n")
        }
        return sb.toString().trimEnd('\n')
    }

    fun toCsv(opers: List<OperBoxOperator>, labels: OperBoxExportLabels): String {
        val sb = StringBuilder()
        sb.append("${labels.name},${labels.id},${labels.rarity},${labels.elite},${labels.level},${labels.own},${labels.potential}\n")
        opers.forEach { op ->
            val own = if (op.own) labels.yes else labels.no
            sb.append("${csvEscape(op.name)},${op.id},${op.rarity},${op.elite},${op.level},$own,${op.potential}\n")
        }
        return sb.toString().trimEnd('\n')
    }

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
