package com.aliothmoon.maameow.data.datasource

import android.content.Context
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.await
import com.aliothmoon.maameow.data.api.model.GitHubRelease
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
import java.util.Locale

class AppDownloader(
    private val context: Context,
    private val httpClient: HttpClientHelper
) {

    companion object {
        private val json = JsonUtils.common

        /**
         * 比较语义化版本号，支持 v 前缀和 prerelease
         * 遵循 SemVer: 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-beta < 1.0.0-rc.1 < 1.0.0
         */
        fun compareVersions(v1: String, v2: String): Int {
            val clean1 = v1.removePrefix("v").removePrefix("V")
            val clean2 = v2.removePrefix("v").removePrefix("V")

            val (main1, pre1) = splitVersion(clean1)
            val (main2, pre2) = splitVersion(clean2)

            val mainCompare = compareMainVersion(main1, main2)
            if (mainCompare != 0) return mainCompare

            return comparePrerelease(pre1, pre2)
        }

        private fun splitVersion(v: String): Pair<String, String?> {
            val idx = v.indexOf('-')
            return if (idx >= 0) v.take(idx) to v.substring(idx + 1) else v to null
        }

        private fun compareMainVersion(v1: String, v2: String): Int {
            val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        }

        private fun comparePrerelease(pre1: String?, pre2: String?): Int {
            if (pre1 == null && pre2 == null) return 0
            if (pre1 == null) return 1   // 1.0.0 > 1.0.0-xxx
            if (pre2 == null) return -1  // 1.0.0-xxx < 1.0.0

            val ids1 = pre1.split(".")
            val ids2 = pre2.split(".")
            val maxLen = maxOf(ids1.size, ids2.size)
            for (i in 0 until maxLen) {
                if (i >= ids1.size) return -1
                if (i >= ids2.size) return 1
                val n1 = ids1[i].toIntOrNull()
                val n2 = ids2[i].toIntOrNull()
                val cmp = when {
                    n1 != null && n2 != null -> n1.compareTo(n2)
                    n1 != null -> -1  // 数字 < 字符串
                    n2 != null -> 1
                    else -> ids1[i].compareTo(ids2[i])
                }
                if (cmp != 0) return cmp
            }
            return 0
        }
    }

    /**
     * 通过 GitHub Release API 检查更新
     */
    suspend fun checkVersionFromGitHub(): UpdateCheckResult {
        return try {
            val response = httpClient.get(MaaApi.APP_GITHUB_RELEASES)
            if (!response.isSuccessful) {
                return UpdateCheckResult.Error(
                    UpdateError.UnknownError(
                        "GitHub API 请求失败",
                        response.code
                    )
                )
            }

            val release = runCatching {
                val releases = json.decodeFromString<List<GitHubRelease>>(response.body.string())
                releases.firstOrNull()
            }.getOrElse { e ->
                return UpdateCheckResult.Error(
                    UpdateError.UnknownError(
                        "解析响应失败: ${e.message}",
                        -1
                    )
                )
            } ?: return UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)

            val remoteVersion = release.tagName.removePrefix("v").removePrefix("V")
            val currentVersion = BuildConfig.VERSION_NAME

            if (compareVersions(currentVersion, remoteVersion) >= 0) {
                return UpdateCheckResult.UpToDate(currentVersion)
            }

            // 查找 APK asset
            val apkAsset = release.assets.firstOrNull { it.name.endsWith("universal.apk") }
                ?: return UpdateCheckResult.Error(
                    UpdateError.UnknownError(
                        "Release 中未找到 APK 文件",
                        -1
                    )
                )

            UpdateCheckResult.Available(
                UpdateInfo(
                    version = remoteVersion,
                    downloadUrl = apkAsset.browserDownloadUrl,
                    releaseNote = release.body
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "GitHub 检查更新失败")
            UpdateCheckResult.Error(UpdateError.NetworkError(e.message ?: "网络错误"))
        }
    }

    /**
     * 通过 MirrorChyan API 检查更新
     */
    suspend fun checkVersionFromMirrorChyan(cdk: String = ""): UpdateCheckResult {
        return try {
            val currentVersion = BuildConfig.VERSION_NAME
            val response = httpClient.get(
                MaaApi.MIRROR_CHYAN_APP_RESOURCE,
                query = buildMap {
                    put("current_version", currentVersion)
                    put("user_agent", "MAA-Meow")
                    put("os", "android")
                    if (cdk.isNotBlank()) {
                        put("cdk", cdk)
                    }
                }
            )

            if (response.code == 500) {
                return UpdateCheckResult.Error(UpdateError.UnknownError("更新服务不可用", 500))
            }

            val body = runCatching {
                json.decodeFromString<MirrorChyanResponse>(response.body.string())
            }.getOrDefault(MirrorChyanResponse.UNKNOWN_ERR)

            if (body.code != 0) {
                return UpdateCheckResult.Error(UpdateError.fromCode(body.code, body.msg))
            }

            val data = body.data ?: return UpdateCheckResult.Error(
                UpdateError.UnknownError(
                    "数据为空",
                    -1
                )
            )
            val remoteVersion = data.versionName

            if (remoteVersion.isEmpty() || compareVersions(currentVersion, remoteVersion) >= 0) {
                return UpdateCheckResult.UpToDate(currentVersion)
            }

            val downloadUrl = if (cdk.isNotBlank()) {
                data.url ?: return UpdateCheckResult.Error(
                    UpdateError.UnknownError(
                        "下载链接为空",
                        -1
                    )
                )
            } else {
                ""
            }

            UpdateCheckResult.Available(
                UpdateInfo(
                    version = remoteVersion,
                    downloadUrl = downloadUrl,
                    releaseNote = data.releaseNote
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "MirrorChyan 检查 App 更新失败")
            UpdateCheckResult.Error(UpdateError.NetworkError(e.message ?: "网络错误"))
        }
    }

    /**
     * 查找已缓存的完整 APK（仅 .apk 后缀表示下载完成）
     */
    fun getCachedApk(version: String): File? {
        val file = File(context.cacheDir, apkFileName(version))
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * 清理其他版本的缓存 APK 和残留的 .dl 文件
     */
    fun cleanOldApks(keepVersion: String) {
        val keepName = apkFileName(keepVersion)
        context.cacheDir.listFiles()
            ?.filter {
                it.name.startsWith("MaaMeow-") && (it.name.endsWith(".apk") || it.name.endsWith(
                    ".apk.dl"
                ))
            }
            ?.filter { it.name != keepName }
            ?.forEach { it.delete() }
    }


    suspend fun downloadToTempFile(
        url: String,
        version: String,
        onProgress: (ResourceDownloader.DownloadProgress) -> Unit
    ): Result<File> {
        return try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.rawClient().newCall(request).await()

            if (!response.isSuccessful) {
                return Result.failure(Exception("服务器返回错误 (HTTP ${response.code})"))
            }

            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: 0L
            val apkFile = File(context.cacheDir, apkFileName(version))
            val dlFile = File(context.cacheDir, "${apkFileName(version)}.dl")

            // 清理可能残留的 .dl 文件
            dlFile.delete()

            withContext(Dispatchers.IO) {
                val bfz = 2 * 1024 * 1024
                BufferedOutputStream(FileOutputStream(dlFile), bfz).use { output ->
                    val buffer = ByteArray(bfz)
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
                                    ResourceDownloader.DownloadProgress(
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

                // 下载完成
                apkFile.delete()
                dlFile.renameTo(apkFile)
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Timber.e(e, "下载 APK 失败")
            Result.failure(Exception(formatDownloadError(e), e))
        }
    }

    private fun apkFileName(version: String): String = "MaaMeow-${version}.apk"

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format(
                Locale.US,
                "%.1f MB/s",
                bytesPerSecond / (1024.0 * 1024)
            )

            bytesPerSecond >= 1024 -> String.format(
                Locale.US,
                "%.1f KB/s", bytesPerSecond / 1024.0
            )

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
