package com.aliothmoon.maameow.constant

import com.aliothmoon.maameow.data.config.GamePackageConfig
import java.io.File

object Packages : Iterable<Map.Entry<String, String>> {
    @Volatile
    private var config: GamePackageConfig? = null

    @Volatile
    private var loadedPackages: Map<String, String> = emptyMap()

    fun initialize(config: GamePackageConfig?) {
        this.config = config
        loadedPackages = emptyMap()
    }

    operator fun get(type: String): String? =
        loadedPackages[type] ?: config?.resolve(type) ?: GamePackageDefaults.names[type]

    fun readPackageNames(root: File, clientType: String, overridesEnabled: Boolean = false): Map<String, String> =
        (config ?: GamePackageConfig(root)).snapshot(clientType, overridesEnabled)

    fun onResourcesLoaded(clientType: String, names: Map<String, String>) {
        // Official and Bilibili share one resource profile and do not trigger a Core reload.
        val clients = if (clientType in setOf("Official", "Bilibili", "")) {
            setOf("Official", "Bilibili")
        } else setOf(clientType)
        loadedPackages = names.filterKeys { it in clients }
    }

    override fun iterator(): Iterator<Map.Entry<String, String>> =
        GamePackageDefaults.names.keys.mapNotNull { type -> get(type)?.let { type to it } }.toMap().iterator()
}