package com.aliothmoon.maameow.data.datasource

import android.annotation.SuppressLint
import android.content.Context
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.await
import com.aliothmoon.maameow.data.api.model.MirrorChyanResponse
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateError
import com.aliothmoon.maameow.data.model.update.UpdateInfo
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class ResourceDownloader(
    private val context: Context,
    private val httpClient: HttpClientHelper
) {

    data class DownloadProgress(
        val progress: Int,
        val speed: String,
        val downloaded: Long,
        val total: Long
    )

    companion object {
        private val VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        private val DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        val json = JsonUtils.common

        fun formatVersionForDisplay(version: String): String {
            return runCatching {
                LocalDateTime.parse(version, VERSION_FORMATTER)
                    .atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .format(DISPLAY_FORMATTER)
            }.getOrDefault(version)
        }

        fun compareVersions(v1: String, v2: String): Int {
            return try {
                val v1t = runCatching {
                    LocalDateTime.parse(v1, VERSION_FORMATTER)
                }.getOrDefault(LocalDateTime.MIN)
                val v2t = runCatching {
                    LocalDateTime.parse(v2, VERSION_FORMATTER)
                }.getOrDefault(LocalDateTime.MIN)
                v1t.compareTo(v2t)
            } catch (e: Exception) {
                Timber.w(e, "版本比较失败: v1=$v1, v2=$v2")
                0
            }
        }
    }

    suspend fun checkVersion(current: String, cdk: String = ""): UpdateCheckResult {
        return try {
            val result = httpClient.get(
                MaaApi.MIRROR_CHYAN_RESOURCE,
                query = buildMap {
                    put("current_version", current)
                    put("user_agent", "MAA-Meow")
                    if (cdk.isNotBlank()) {
                        put("cdk", cdk)
                    }
                }
            )

            if (result.code == 500) {
                return UpdateCheckResult.Error(UpdateError.UnknownError("更新服务不可用", 500))
            }

            val response = runCatching {
                json.decodeFromString<MirrorChyanResponse>(result.body.string())
            }.getOrDefault(MirrorChyanResponse.UNKNOWN_ERR)

            parseVersionResponse(response, current, cdk.isNotBlank())
        } catch (e: Exception) {
            Timber.e(e, "检查版本失败")
            UpdateCheckResult.Error(UpdateError.NetworkError(e.message ?: "网络错误"))
        }
    }

    /**
     * 通过 MirrorChyan API 获取下载链接
     */
    suspend fun resolveDownloadUrl(currentVersion: String, cdk: String): UpdateCheckResult {
        return checkVersion(currentVersion, cdk)
    }

    private fun parseVersionResponse(
        body: MirrorChyanResponse,
        current: String,
        useMirrorChyan: Boolean
    ): UpdateCheckResult {
        if (body.code != 0) {
            return UpdateCheckResult.Error(UpdateError.fromCode(body.code, body.msg))
        }

        val data =
            body.data ?: return UpdateCheckResult.Error(UpdateError.UnknownError("数据为空", -1))
        val remote = data.versionName

        if (remote.isEmpty() || compareVersions(current, remote) >= 0) {
            return UpdateCheckResult.UpToDate(current)
        }

        val downloadUrl = if (useMirrorChyan) {
            data.url ?: return UpdateCheckResult.Error(UpdateError.UnknownError("下载链接为空", -1))
        } else {
            ""
        }

        return UpdateCheckResult.Available(
            UpdateInfo(version = formatVersionForDisplay(remote), downloadUrl = downloadUrl)
        )
    }

    suspend fun downloadToTempFile(
        url: String,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> {
        return try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.rawClient().newCall(request).await()

            if (!response.isSuccessful) {
                return Result.failure(Exception("服务器返回错误 (HTTP ${response.code})"))
            }

            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: 0L
            val tempFile = File(context.cacheDir, "MaaResources-${UUID.randomUUID()}.zip")

            withContext(Dispatchers.IO) {
                BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                    val buffer = ByteArray(16 * 1024)
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
            Result.failure(Exception(formatDownloadError(e), e))
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format(
                "%.1f MB/s",
                bytesPerSecond / (1024.0 * 1024)
            )

            bytesPerSecond >= 1024 -> String.format("%.1f KB/s", bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }


    private fun formatDownloadError(e: Exception): String {
        return when (e) {
            is IOException -> "网络异常，请检查网络连接后重试"
            else -> e.message ?: "未知错误"
        }
    }
}
