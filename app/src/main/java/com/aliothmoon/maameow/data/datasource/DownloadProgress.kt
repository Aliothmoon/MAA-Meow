package com.aliothmoon.maameow.data.datasource

import java.io.IOException
import java.util.Locale

data class DownloadProgress(
    val progress: Int,
    val speed: String,
    val downloaded: Long,
    val total: Long,
)

internal fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024 * 1024 -> String.format(
        Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024)
    )
    bytesPerSecond >= 1024 -> String.format(
        Locale.US, "%.1f KB/s", bytesPerSecond / 1024.0
    )
    else -> "$bytesPerSecond B/s"
}

internal fun formatDownloadError(e: Exception): String = when (e) {
    is IOException -> "网络异常，请检查网络连接后重试"
    else -> e.message ?: "未知错误"
}
