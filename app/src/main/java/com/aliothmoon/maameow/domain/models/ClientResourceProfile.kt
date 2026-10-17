package com.aliothmoon.maameow.domain.models

import java.io.File

/** 客户端到资源档的归一规则，Core 加载与界面元数据共用 */
object ClientResourceProfile {

    /** 只加载 base 资源的客户端 */
    private val BASE_ONLY_CLIENT_TYPES = setOf("", "Official", "Bilibili")

    /** 官服/B服/空共用 base 归一为空串，各 global 客户端各自一档 */
    fun of(clientType: String): String =
        if (clientType in BASE_ONLY_CLIENT_TYPES) "" else clientType

    fun resourceParents(root: File, clientType: String, overridesEnabled: Boolean): List<File> = buildList {
        add(root)
        add(File(root, "cache"))
        if (of(clientType).isNotEmpty()) {
            add(File(root, "resource/global/$clientType"))
            add(File(root, "cache/resource/global/$clientType"))
        }
        if (overridesEnabled) add(File(root, "overrides"))
    }

    /** 元数据按档加载，base 档统一成 Official */
    fun metadataClient(clientType: String): String = of(clientType).ifEmpty { "Official" }
}
