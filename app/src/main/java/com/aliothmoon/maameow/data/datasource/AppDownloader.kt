package com.aliothmoon.maameow.data.datasource

import android.content.Context
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class AppDownloader(
    private val context: Context,
    private val httpClient: HttpClientHelper
) {

    companion object {
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
        context.cacheDir.listFiles()?.filter {
                it.name.startsWith("MaaMeow-") && (it.name.endsWith(".apk") || it.name.endsWith(
                    ".apk.dl"
                ))
            }?.filter { it.name != keepName }?.forEach { it.delete() }
    }

    suspend fun downloadToTempFile(
        url: String, version: String, onProgress: (DownloadProgress) -> Unit
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

            dlFile.delete()

            withContext(Dispatchers.IO) {
                val bfz = 4 * 1024 * 1024
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
}
