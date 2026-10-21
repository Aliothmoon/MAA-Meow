package com.aliothmoon.maameow.constant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object Packages : Iterable<Map.Entry<String, String>> {
    /** 与 MaaCore resource/config.json 的 packageName 一致 */
    val defaults = mapOf(
        "Official" to "com.hypergryph.arknights",
        "Bilibili" to "com.hypergryph.arknights.bilibili",
        "YoStarEN" to "com.YoStarEN.Arknights",
        "YoStarJP" to "com.YoStarJP.Arknights",
        "YoStarKR" to "com.YoStarKR.Arknights",
        "txwy" to "tw.txwy.and.arknights"
    )

    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    @Volatile
    private var overrides: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())

    fun bindOverrides(source: StateFlow<Map<String, String>>) {
        overrides = source
    }

    operator fun get(type: String): String? = overrides.value[type] ?: defaults[type]

    fun isValidOverride(type: String, packageName: String): Boolean =
        type in defaults && packageName != defaults[type] && PACKAGE_NAME.matches(packageName)

    override fun iterator(): Iterator<Map.Entry<String, String>> {
        return (defaults + overrides.value).iterator()
    }
}
