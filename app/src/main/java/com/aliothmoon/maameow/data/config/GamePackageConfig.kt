package com.aliothmoon.maameow.data.config

import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.domain.models.ClientResourceProfile
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.io.File

/** Read-only access to the resource config files passed to Core. */
class GamePackageConfig(private val root: File) {
    fun resolve(clientType: String): String? = packageNames(readConfigs(clientType))[clientType]

    /** A failed config read must be a resource error, not a guessed watchdog target. */
    fun snapshot(clientType: String): Map<String, String> {
        val configs = readConfigs(clientType, strict = true)
        check(configs.isNotEmpty()) { "Cannot read Core game package config" }
        return packageNames(configs)
    }

    /** Same order as MaaResourceLoader. The built-in tasks override does not contain config.json. */
    private fun readConfigs(clientType: String, strict: Boolean = false): List<JsonObject> = buildList {
        add(File(root, "resource/config.json"))
        add(File(root, "cache/resource/config.json"))
        if (ClientResourceProfile.of(clientType).isNotEmpty()) {
            add(File(root, "resource/global/$clientType/resource/config.json"))
            add(File(root, "cache/resource/global/$clientType/resource/config.json"))
        }
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
        putAll(Packages.defaults)
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
