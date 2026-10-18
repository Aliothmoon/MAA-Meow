package com.aliothmoon.maameow.remote

import java.io.File

object CoreResourceConfigs {
    // Core reads the custom file immediately after the standard config in each layer.
    val filenames = listOf("config.json", "config_custom.json")

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
    }.filter { File(root, it).exists() }
}
