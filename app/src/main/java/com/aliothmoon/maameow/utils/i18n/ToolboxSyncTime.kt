package com.aliothmoon.maameow.utils.i18n

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SYNC_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 将 epoch 毫秒格式化为本地「上次同步」展示文案的时间部分。 */
fun formatToolboxSyncTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(SYNC_TIME_FORMAT)
}
