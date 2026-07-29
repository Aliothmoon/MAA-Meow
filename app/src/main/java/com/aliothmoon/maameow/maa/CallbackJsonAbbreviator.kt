package com.aliothmoon.maameow.maa

/**
 * MaaCore 回调 JSON 的日志缩略。
 *
 * 回调日志（日志包里的 `logcat/core/` 目录）是排障的一手证据，不能删；但 `SubTaskExtraInfo`
 * 携带仓库/干员识别结果时可达数百 KB，原样打印会在回调热路径上多做一次全量字符串
 * 拼接 + 一次大写入，直接拖慢每一条回调的送达。
 *
 * 折中：保留足以定位问题的前缀（`what` / `taskchain` / `subtask` 等键都在 JSON 靠前的位置），
 * 其余截断并**显式标注原始长度**，避免读日志的人误以为内容就这么多。
 *
 * 本对象运行在提权进程，只做纯字符串处理 —— 不得引入 Timber 或任何 Android 依赖。
 */
object CallbackJsonAbbreviator {

    /** 前缀保留长度。足以覆盖 MaaCore 回调里的路由字段，又不至于让单条日志过大。 */
    const val MAX_LOGGED_CHARS = 512

    fun abbreviate(json: String?): String {
        if (json == null) return "null"
        if (json.length <= MAX_LOGGED_CHARS) return json
        return json.take(MAX_LOGGED_CHARS) + "…<truncated, total ${json.length} chars>"
    }
}
