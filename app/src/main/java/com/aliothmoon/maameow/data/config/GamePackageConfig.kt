package com.aliothmoon.maameow.data.config

import com.aliothmoon.maameow.constant.GamePackageDefaults
import com.aliothmoon.maameow.domain.models.ClientResourceProfile
import com.aliothmoon.maameow.remote.CoreResourceConfigs
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.io.File

/** Read-only access to the resource config files passed to Core. */
class GamePackageConfig(
    private val root: File,
    private val overridesEnabled: () -> Boolean = { false },
) {
    fun resolve(clientType: String): String? = packageNames(readConfigs(clientType))[clientType]

    /** A failed config read must be a resource error, not a guessed watchdog target. */
    fun snapshot(clientType: String, includeOverrides: Boolean = overridesEnabled()): Map<String, String> {
        val configs = readConfigs(clientType, includeOverrides, strict = true)
        check(configs.isNotEmpty()) { "Cannot read Core game package config" }
        return packageNames(configs)
    }

    private fun readConfigs(
        clientType: String,
        includeOverrides: Boolean = overridesEnabled(),
        strict: Boolean = false,
    ): List<JsonObject> = ClientResourceProfile.resourceParents(root, clientType, includeOverrides).flatMap { parent ->
        CoreResourceConfigs.filenames.map { File(parent, "resource/$it") }
    }.mapNotNull { file ->
        try {
            if (file.exists()) {
                JsonUtils.common.parseToJsonElement(file.readText()) as? JsonObject
                    ?: error("Core config must be a JSON object")
            } else null
        } catch (e: Exception) {
            if (strict) throw IllegalStateException("Cannot read Core config: $file", e)
            Timber.w(e, "Unable to read game package config: %s", file)
            null
        }
    }

    private fun packageNames(configs: List<JsonObject>): Map<String, String> = buildMap {
        putAll(GamePackageDefaults.names)
        for (config in configs) {
            val names = config["packageName"] as? JsonObject ?: continue
            for ((client, value) in names) {
                val name = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                if (isValidPackageName(name)) put(client, name)
            }
        }
    }

    companion object {
        private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

        private fun isValidPackageName(value: String): Boolean = PACKAGE_NAME.matches(value)
    }
}
