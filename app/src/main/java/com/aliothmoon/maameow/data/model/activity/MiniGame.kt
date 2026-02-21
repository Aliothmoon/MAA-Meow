package com.aliothmoon.maameow.data.model.activity

import com.aliothmoon.maameow.data.resource.MaaCoreVersion

/**
 * 小游戏（UI 使用）
 */
data class MiniGame(
    val display: String,           // 显示名称
    val value: String,             // 任务代码
    val utcStartTime: Long,        // UTC 开始时间
    val utcExpireTime: Long,       // UTC 结束时间
    val tip: String? = null,       // 提示信息（版本过低时显示警告）
    val tipKey: String? = null,    // 提示本地化键
    val isUnsupported: Boolean = false  // 版本不支持标记
) {
    val isOpen: Boolean
        get() {
            if (utcStartTime == 0L && utcExpireTime == Long.MAX_VALUE) return true
            val now = System.currentTimeMillis()
            return now in utcStartTime until utcExpireTime
        }

    companion object {
        /**
         * 从 API 入口创建（带版本检查）
         * 迁移自 WPF ParseMiniGameEntries
         */
        /**
         * 从 API 入口创建（带版本检查 + Display/Tip 解析）
         * 迁移自 WPF ParseMiniGameEntry + ParseMiniGameEntries
         */
        fun fromEntry(entry: MiniGameEntry): MiniGame {
            // Display 解析链: Display → DisplayKey → Value → ""
            val finalDisplay = when {
                !entry.display.isNullOrEmpty() -> entry.display
                !entry.displayKey.isNullOrEmpty() -> entry.displayKey
                !entry.value.isNullOrEmpty() -> entry.value
                else -> ""
            }
            val finalValue = entry.value ?: finalDisplay

            val minReq = entry.minimumRequired
            val unsupported = !minReq.isNullOrBlank() &&
                    !MaaCoreVersion.meetsMinimumRequired(minReq)

            // 版本过低时覆盖 tip/tipKey (WPF: ParseMiniGameEntries)
            val tip: String?
            val tipKey: String?
            if (unsupported) {
                tip = "版本过低\n最低要求: $minReq"
                tipKey = null
            } else {
                tip = entry.tip
                tipKey = entry.tipKey
            }

            return MiniGame(
                display = finalDisplay,
                value = finalValue,
                utcStartTime = StageActivityInfo.parseToUtcMillis(
                    entry.utcStartTime,
                    entry.timeZone
                ),
                utcExpireTime = StageActivityInfo.parseToUtcMillis(
                    entry.utcExpireTime,
                    entry.timeZone
                ),
                tip = tip,
                tipKey = tipKey,
                isUnsupported = unsupported
            )
        }
    }
}
