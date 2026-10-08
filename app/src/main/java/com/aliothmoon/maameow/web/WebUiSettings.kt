package com.aliothmoon.maameow.web

import com.aliothmoon.preferences.PrefKey
import com.aliothmoon.preferences.PrefSchema
import kotlinx.serialization.Serializable

/** 单独存放，不进配置备份：会话哈希不该被导出 */
@Serializable
@PrefSchema(name = "WebUiSettings")
data class WebUiSettings(
    @PrefKey(default = "false") val enabled: String = "false",
    @PrefKey(default = "8686") val port: String = "8686",
    /** [com.aliothmoon.maameow.web.auth.WebSession] 列表的 JSON */
    @PrefKey(default = "") val sessions: String = "",
)
