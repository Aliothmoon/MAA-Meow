package com.aliothmoon.maameow.data.datasource

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.useCancellable
import com.aliothmoon.maameow.data.config.ResourceVersionHelper
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

            httpClient.rawClient().newCall(request).useCancellable { response ->
                if (!response.isSuccessful) {
                    response.close()
                    return@useCancellable Result.failure(
                        LocalizedException(
                            uiTextOf(R.string.update_error_http_status, response.code)
                        )
                    )
                }

                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: 0L
                val file = File(context.cacheDir, "MaaResources-${UUID.randomUUID()}.zip")
                tempFile = file

                withContext(Dispatchers.IO) {
                    val bfz = 256 * 1024
                    BufferedOutputStream(FileOutputStream(file)).use { output ->
                        body.byteStream().use { input ->
                            input.copyWithProgress(output, total, bfz, onProgress)
                        }
                    }
                }

                Result.success(file)
            }
        } catch (e: CancellationException) {
            tempFile?.delete()
            throw e
        } catch (e: Exception) {
            // 断连抛的 IOException 不是网络错误
            currentCoroutineContext().ensureActive()
            Timber.e(e, "下载文件失败")
            tempFile?.delete()
            Result.failure(LocalizedException(formatDownloadError(e), e))
        }
    }
}
