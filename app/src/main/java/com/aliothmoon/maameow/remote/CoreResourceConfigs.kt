package com.aliothmoon.maameow.remote

import java.io.File

object CoreResourceConfigs {
    fun paths(root: File): List<String> = buildList {
        for (resourcePath in listOf("resource", "cache/resource")) {
            add("$resourcePath/config.json")
            val global = File(root, "$resourcePath/global")
            if (global.exists()) {
                val clients = checkNotNull(global.listFiles()) { "Cannot list resource configs: $global" }
                clients.filter { it.isDirectory }.sortedBy { it.name }.forEach {
                    add("$resourcePath/global/${it.name}/resource/config.json")
                }
            }
        }
        add("overrides/resource/config.json")
    }.filter { File(root, it).exists() }
}
