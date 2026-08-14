package com.aliothmoon.maameow.domain.models

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

fun JsonObjectBuilder.putReportFields(options: ReportOptions) {
    put("report_to_penguin", options.reportToPenguin)
    put("report_to_yituliu", options.reportToYituliu)
    put("server", options.server)
    if (options.reportToPenguin && options.penguinId.isNotBlank()) {
        put("penguin_id", options.penguinId)
    }
    if (options.reportToYituliu && options.penguinId.isNotBlank()) {
        put("yituliu_id", options.penguinId)
    }
}

/** 企鹅物流 / 一图流上报选项；一图流 uuid 复用 penguinId */
data class ReportOptions(
    val reportToPenguin: Boolean,
    val reportToYituliu: Boolean,
    val penguinId: String,
    val server: String,
) {
    companion object {
        val DEFAULT = ReportOptions(
            reportToPenguin = true,
            reportToYituliu = true,
            penguinId = "",
            server = "CN",
        )

        fun of(
            clientType: String,
            reportToPenguin: Boolean,
            reportToYituliu: Boolean,
            penguinId: String,
        ) = ReportOptions(
            reportToPenguin = reportToPenguin,
            reportToYituliu = reportToYituliu,
            penguinId = penguinId.trim(),
            server = serverOf(clientType),
        )

        fun serverOf(clientType: String): String = when (clientType) {
            "YoStarEN" -> "US"
            "YoStarJP" -> "JP"
            "YoStarKR" -> "KR"
            "txwy" -> "ZH_TW"
            else -> "CN"
        }
    }
}
