package com.aliothmoon.maameow.data.datasource

import android.annotation.SuppressLint
import android.content.Context
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.await
import com.aliothmoon.maameow.data.api.model.MirrorChyanResponse
import com.aliothmoon.maameow.data.model.update.UpdateInfo
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
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

    sealed class VersionCheckResult {
        data class UpdateAvailable(val info: UpdateInfo) : VersionCheckResult()
        data class NoUpdate(val currentVersion: String) : VersionCheckResult()
        data class Error(val code: Int, val message: String? = null) : VersionCheckResult()
    }

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

    suspend fun checkVersion(current: String, cdk: String = ""): VersionCheckResult {
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
                return VersionCheckResult.Error(500, "更新服务不可用")
            }

            val response = runCatching {
                json.decodeFromString<MirrorChyanResponse>(result.body.string())
            }.getOrDefault(MirrorChyanResponse.UNKNOWN_ERR)

            parseVersionResponse(response, current, cdk.isNotBlank())
        } catch (e: Exception) {
            Timber.e(e, "检查版本失败")
            VersionCheckResult.Error(-1, e.message ?: "网络错误")
        }
    }

    /**
     * 通过 MirrorChyan API 获取下载链接
     */
    suspend fun resolveDownloadUrl(currentVersion: String, cdk: String): VersionCheckResult {
        return checkVersion(currentVersion, cdk)
    }

    private fun parseVersionResponse(
        body: MirrorChyanResponse,
        current: String,
        useMirrorChyan: Boolean
    ): VersionCheckResult {
        if (body.code != 0) {
            return VersionCheckResult.Error(body.code, body.msg)
        }

        val data = body.data ?: return VersionCheckResult.Error(-1, "数据为空")
        val remote = data.versionName

        if (remote.isEmpty() || compareVersions(current, remote) >= 0) {
            return VersionCheckResult.NoUpdate(current)
        }

        val downloadUrl = if (useMirrorChyan) {
            data.url ?: return VersionCheckResult.Error(-1, "下载链接为空")
        } else {
            ""
        }

        return VersionCheckResult.UpdateAvailable(
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
                return Result.failure(Exception("HTTP ${response.code}"))
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
            Result.failure(e)
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
}
