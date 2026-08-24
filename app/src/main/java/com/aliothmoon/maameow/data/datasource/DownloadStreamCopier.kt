package com.aliothmoon.maameow.data.datasource

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream

private const val PROGRESS_INTERVAL_MS = 300L

/** 边拷贝边回报进度，每轮检查取消 */
internal suspend fun InputStream.copyWithProgress(
    output: OutputStream,
    total: Long,
    bufferSize: Int,
    onProgress: (DownloadProgress) -> Unit,
) {
    val buffer = ByteArray(bufferSize)
    var downloaded = 0L
    var lastUpdateTime = System.currentTimeMillis()
    var lastDownloaded = 0L

    while (true) {
        currentCoroutineContext().ensureActive()

        val read = read(buffer)
        if (read == -1) break
        output.write(buffer, 0, read)
        downloaded += read

        val now = System.currentTimeMillis()
        if (now - lastUpdateTime >= PROGRESS_INTERVAL_MS) {
            val speed = if (now > lastUpdateTime) {
                (downloaded - lastDownloaded) * 1000 / (now - lastUpdateTime)
            } else 0L

            onProgress(
                DownloadProgress(
                    progress = if (total > 0) (downloaded * 100 / total).toInt() else 0,
                    speed = formatSpeed(speed),
                    downloaded = downloaded,
                    total = total,
                )
            )

            lastUpdateTime = now
            lastDownloaded = downloaded
        }
    }
}
