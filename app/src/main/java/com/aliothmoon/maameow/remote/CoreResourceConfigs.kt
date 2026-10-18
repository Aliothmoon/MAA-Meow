package com.aliothmoon.maameow.remote

import java.io.File

object CoreResourceConfigs {
    // Meow synchronizes standard configs and its own generated package resource only.
    val filenames = listOf("config.json")
    const val PACKAGE_RESOURCE_PARENT = "package-config"
    const val PACKAGE_RESOURCE_CONFIG = "$PACKAGE_RESOURCE_PARENT/resource/config.json"

    fun paths(root: File): List<String> = buildList {
        for (resourcePath in listOf("resource", "cache/resource")) {
            filenames.forEach { add("$resourcePath/$it") }
            val global = File(root, "$resourcePath/global")
            if (global.exists()) {
                val clients = checkNotNull(global.listFiles()) { "Cannot list resource configs: $global" }
                clients.filter { it.isDirectory }.sortedBy { it.name }.forEach {
                    val client = it.name
                    filenames.forEach { name -> add("$resourcePath/global/$client/resource/$name") }
                }
            }
        }
        filenames.forEach { add("overrides/resource/$it") }
        add(PACKAGE_RESOURCE_CONFIG)
    }.filter { File(root, it).exists() }
}
