package com.aliothmoon.maameow.data.config

import com.aliothmoon.maameow.constant.GamePackageDefaults
import com.aliothmoon.maameow.domain.models.ClientResourceProfile
import com.aliothmoon.maameow.remote.CoreResourceConfigs
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Owns only the package-name file and a generated Core resource; source resources are read-only. */
class ResourceConfigEditor(private val root: File) {
    private val stateFile get() = File(root, "game-package-settings.json")
    private val packageFile get() = File(root, PACKAGE_FILE)
    private val json = Json(JsonUtils.common) { prettyPrint = true }

    fun isEnabled(): Boolean =
        (readObject(stateFile)["enabled"] as? JsonPrimitive)?.booleanOrNull ?: false

    fun setEnabled(enabled: Boolean) {
        // Toggling never rewrites the saved package names or any Core source config.
        writeTransaction(mapOf(stateFile to encode(JsonObject(readObject(stateFile) + ("enabled" to JsonPrimitive(enabled))))))
    }

    fun packageNames(): JsonObject {
        val config = readObject(packageFile)
        require(config.keys.all { it == "packageName" }) { "Package config may only contain packageName" }
        val names = config["packageName"] as? JsonObject
            ?: if (config.isEmpty()) JsonObject(emptyMap()) else error("packageName must be an object")
        require(names.values.all {
            it is JsonPrimitive && it.isString && GamePackageConfig.isValidPackageName(it.content)
        }) { "Invalid package name" }
        return names
    }

    fun defaultPackageName(clientType: String, overridesEnabled: Boolean): String {
        var value = GamePackageDefaults.names[clientType] ?: error("Unknown client: $clientType")
        for (parent in ClientResourceProfile.resourceParents(root, clientType, overridesEnabled)) {
            val entry = readObject(File(parent, "resource/config.json"))["packageName"]
                ?.jsonObject?.get(clientType)
            if (entry is JsonPrimitive && entry.isString && GamePackageConfig.isValidPackageName(entry.content)) {
                value = entry.content
            }
        }
        return value
    }

    fun read(path: String, clientType: String, overridesEnabled: Boolean): String {
        requirePackagePath(path)
        check(File(root, "resource/config.json").isFile) { "Resource config is missing" }
        val value = packageNames()[clientType] ?: JsonPrimitive(defaultPackageName(clientType, overridesEnabled))
        return encode(buildJsonObject { put("packageName", buildJsonObject { put(clientType, value) }) })
    }

    fun isValid(content: String, clientType: String): Boolean = runCatching {
        val config = JsonUtils.common.parseToJsonElement(content) as? JsonObject ?: return false
        if (config.keys != setOf("packageName")) return false
        val names = config["packageName"] as? JsonObject ?: return false
        if (names.keys != setOf(clientType)) return false
        val name = names[clientType] as? JsonPrimitive ?: return false
        name.isString && GamePackageConfig.isValidPackageName(name.content)
    }.getOrDefault(false)

    fun save(path: String, content: String, clientType: String) {
        requirePackagePath(path)
        check(isEnabled()) { "Custom game packages are disabled" }
        require(isValid(content, clientType)) { "Invalid package JSON" }
        val edited = JsonUtils.common.parseToJsonElement(content).jsonObject["packageName"]!!.jsonObject
        writeTransaction(mapOf(packageFile to encode(buildJsonObject {
            put("packageName", JsonObject(packageNames() + edited))
        })))
    }

    fun reset(clientType: String) {
        if (!packageFile.exists()) return
        writeTransaction(mapOf(packageFile to encode(buildJsonObject {
            put("packageName", JsonObject(packageNames() - clientType))
        })))
    }

    /** Core accepts a resource directory, not an arbitrary package-only JSON filename. */
    fun prepareForCore(clientType: String, overridesEnabled: Boolean): File? {
        val target = File(root, CoreResourceConfigs.PACKAGE_RESOURCE_CONFIG)
        val names = if (isEnabled()) packageNames() else JsonObject(emptyMap())
        if (names.isEmpty()) {
            // A disabled/deleted override must also disappear from separated Core storage on prepare.
            Files.deleteIfExists(target.toPath())
            return null
        }
        // GeneralConfig parses each file independently. Replay the last standard config's options
        // unchanged, and use no connections: they have already been loaded by earlier resources.
        val config = ClientResourceProfile.resourceParents(root, clientType, overridesEnabled)
            .map { File(it, "resource/config.json") }.lastOrNull { it.isFile }
            ?: error("Resource config is missing")
        val standard = readObject(config)
        val resource = buildJsonObject {
            put("version", standard.getValue("version"))
            put("options", standard.getValue("options"))
            put("packageName", names)
            put("connection", JsonArray(emptyList()))
        }
        writeTransaction(mapOf(target to encode(resource)))
        return File(root, CoreResourceConfigs.PACKAGE_RESOURCE_PARENT)
    }

    private fun requirePackagePath(path: String) {
        require(path == PACKAGE_FILE) { "Unsupported package config: $path" }
        check(packageFile.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) { "Config escapes resource directory" }
    }

    private fun readObject(file: File): JsonObject = if (!file.exists()) JsonObject(emptyMap()) else {
        JsonUtils.common.parseToJsonElement(file.readText()) as? JsonObject ?: error("Config must be an object: $file")
    }

    private fun encode(value: JsonObject): String = json.encodeToString(JsonObject.serializer(), value) + "\n"

    /** Roll back completed writes if a later file fails; every individual replacement is atomic. */
    private fun writeTransaction(changes: Map<File, String>) {
        val original = changes.keys.associateWith { if (it.exists()) it.readBytes() else null }
        val written = mutableListOf<File>()
        try {
            for ((file, content) in changes) {
                writeAtomic(file, content.toByteArray(Charsets.UTF_8))
                written += file
            }
        } catch (failure: Exception) {
            for (file in written.asReversed()) runCatching {
                val bytes = original[file]
                if (bytes == null) Files.deleteIfExists(file.toPath()) else writeAtomic(file, bytes)
            }.onFailure { failure.addSuppressed(it) }
            throw failure
        }
    }

    private fun writeAtomic(destination: File, bytes: ByteArray) {
        check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
        // File.createTempFile uses normal file permissions (subject to umask/storage policy).
        // Files.createTempFile defaults to 0600, which prevents the elevated Core from reading it.
        val temporary = File.createTempFile(".config-", ".tmp", destination.parentFile!!)
        try {
            temporary.outputStream().use { it.write(bytes); it.fd.sync() }
            try {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }

    companion object {
        const val PACKAGE_FILE = "config_custom_package.json"
    }
}
