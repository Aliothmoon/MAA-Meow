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

    internal val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    @Volatile
    private var overrides: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())

    /**
     * core 实际加载生效的包名映射，由 [com.aliothmoon.maameow.domain.service.MaaResourceLoader]
     * 在成功 LoadResource 包名层后发布。
     *
     * 资源热更可能把基底 config.json 的 packageName 改成与 [defaults] 不同的值；只看
     * overrides/defaults 会让 App 去拉默认包、而 core 等热更后的包（CheckGameReadiness 判未安装）。
     * 顺序与写入层时保持一致：覆盖 > 生效映射 > 编译期默认。
     */
    @Volatile
    internal var appliedMapping: Map<String, String> = emptyMap()

    fun bindOverrides(source: StateFlow<Map<String, String>>) {
        overrides = source
    }

    operator fun get(type: String): String? =
        overrides.value[type] ?: appliedMapping[type] ?: defaults[type]

    fun isValidOverride(type: String, packageName: String): Boolean =
        type in defaults && packageName != defaults[type] && PACKAGE_NAME.matches(packageName)

    override fun iterator(): Iterator<Map.Entry<String, String>> {
        // 与 [get] 同序：覆盖 > 生效映射 > 编译期默认。
        // 只用 defaults + overrides 会让「热更改过基底包名」时的遍历（退出记录等）漏掉真实包名。
        return (defaults + appliedMapping + overrides.value).iterator()
    }
}
