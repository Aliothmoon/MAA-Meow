package com.aliothmoon.maameow.data.preferences

import com.aliothmoon.maameow.domain.models.AppSettings
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject


object AppSettingsSnapshot {

    const val ENTRY_NAME = "app_settings.txt"

    internal val MASKED = setOf(
        "mirrorChyanCdk",
        "wakeCredential",
        "yituliuOpenApiToken",
        "penguinId",
        "customBackgroundToken",
    )

    private val OMITTED = setOf(
        "pendingChangelogContent",
        "currentChangelogContent",
    )

    fun format(settings: AppSettings): String {
        val all = JsonUtils.common
            .encodeToJsonElement(AppSettings.serializer(), settings)
            .jsonObject
            .mapValues { (_, v) -> (v as? JsonPrimitive)?.content.orEmpty() }
        val omitted = all.keys.filter { it in OMITTED }
        val values = all.filterKeys { it !in OMITTED }

        val line = "=".repeat(60)
        val pad = values.keys.maxOfOrNull { it.length } ?: 0
        return buildString {
            append(line).append("\n")
            append("=== MaaMeow App Settings ===\n")
            for ((key, value) in values) {
                append(key.padEnd(pad)).append(" = ").append(display(key, value)).append("\n")
            }
            if (omitted.isNotEmpty()) {
                append("(omitted: ${omitted.joinToString()})\n")
            }
            append(line).append("\n")
        }
    }

    private fun display(key: String, value: String): String = when {
        key !in MASKED -> value
        value.isBlank() -> "<empty>"
        else -> "<set>"
    }
}
