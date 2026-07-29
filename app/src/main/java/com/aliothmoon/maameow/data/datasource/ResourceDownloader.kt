package com.aliothmoon.maameow.data.datasource

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.await
import com.aliothmoon.maameow.data.config.ResourceVersionHelper
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ResourceDownloader(
    private val context: Context,
    private val httpClient: HttpClientHelper
) {

    companion object {
        fun formatVersionForDisplay(version: String): String =
            ResourceVersionHelper.formatVersionForDisplay(version)

        fun compareVersions(v1: String, v2: String): Int =
            ResourceVersionHelper.compareVersions(v1, v2)
    }

    suspend fun downloadToTempFile(
        url: String,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> {
        var tempFile: File? = null
        return try {
            val request = Request.Builder().url(url)
                .header("Accept-Encoding", "identity")
                .build()
            val response = httpClient.rawClient().newCall(request).await()

            if (!response.isSuccessful) {
                response.close()
                return Result.failure(
                    LocalizedException(uiTextOf(R.string.update_error_http_status, response.code))
                )
            }

            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: 0L
            tempFile = File(context.cacheDir, "MaaResources-${UUID.randomUUID()}.zip")

            withContext(Dispatchers.IO) {
                BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var downloaded = 0L
                    var lastUpdateTime = System.currentTimeMillis()
                    var lastDownloaded = 0L

                    body.byteStream().use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime >= 300) {
                                val speed = if (now > lastUpdateTime) {
                                    (downloaded - lastDownloaded) * 1000 / (now - lastUpdateTime)
                                } else 0L

                                val progress =
                                    if (total > 0) (downloaded * 100 / total).toInt() else 0

                                onProgress(
                                    DownloadProgress(
                                        progress = progress,
                                        speed = formatSpeed(speed),
                                        downloaded = downloaded,
                                        total = total
                                    )
                                )

                                lastUpdateTime = now
                                lastDownloaded = downloaded
                            }
                        }
                    }
                }
            }

            Result.success(tempFile)
        } catch (e: Exception) {
            Timber.e(e, "下载文件失败")
            tempFile?.delete()
            Result.failure(LocalizedException(formatDownloadError(e), e))
        }
    }
}
