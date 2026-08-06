package com.aliothmoon.maameow.announcement

import android.content.Context
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.ETagCacheManager
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/** 公告内容与其指纹；[hash] 作为已读标记（与 ETag 无关，换源不误弹） */
data class AnnouncementContent(val markdown: String, val hash: String) {
    companion object {
        fun of(markdown: String): AnnouncementContent = AnnouncementContent(
            markdown = markdown,
            hash = MessageDigest.getInstance("SHA-256")
                .digest(markdown.toByteArray())
                .joinToString("") { "%02x".format(it) },
        )
    }
}

/**
 * 远端公告：ETag 条件请求 + 磁盘缓存 + 内置 assets 兜底
 * 304/网络失败时用缓存，内容未变则哈希与已读标记一致，不会重复弹窗
 */
class AnnouncementManager(
    private val context: Context,
    private val httpClient: HttpClientHelper,
    private val eTagCache: ETagCacheManager,
) {
    private val _content = MutableStateFlow<AnnouncementContent?>(null)
    val content: StateFlow<AnnouncementContent?> = _content.asStateFlow()

    suspend fun refresh(language: AppSettingsManager.AppLanguage) = withContext(Dispatchers.IO) {
        val zh = AnnouncementConfig.isZh(language)
        val url = if (zh) MaaApi.ANNOUNCEMENT_ZH else MaaApi.ANNOUNCEMENT_EN
        val cacheFile = cacheFile(zh)
        val markdown = fetchWithETag(url, cacheFile)
            ?: runCatching { cacheFile.takeIf { it.exists() }?.readText() }.getOrNull()
            ?: AnnouncementConfig.loadContent(context, language)
        if (markdown.isNotBlank()) {
            _content.value = AnnouncementContent.of(markdown)
        }
    }

    private fun cacheFile(zh: Boolean): File = File(
        File(context.filesDir, CACHE_DIR),
        if (zh) "announcement_zh.md" else "announcement_en.md",
    )

    private suspend fun fetchWithETag(url: String, cacheFile: File): String? {
        return try {
            httpClient.get(url, headers = eTagCache.getConditionalHeader(url)).use { resp ->
                when (resp.code) {
                    200 -> {
                        val body = resp.body.string()
                        eTagCache.updateConditionalHeaders(url, resp.headers)
                        runCatching {
                            cacheFile.parentFile?.mkdirs()
                            cacheFile.writeText(body)
                        }.onFailure { Timber.w(it, "announcement cache write failed") }
                        body
                    }

                    304 -> {
                        val cached = runCatching {
                            cacheFile.takeIf { it.exists() }?.readText()
                        }.getOrNull()
                        if (cached == null) {
                            // 磁盘缓存丢失但 ETag 还在，清掉让下次强制 200
                            eTagCache.invalidateUrl(url)
                        }
                        cached
                    }

                    else -> {
                        Timber.w("announcement fetch HTTP %d: %s", resp.code, url)
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "announcement fetch failed: %s", url)
            null
        }
    }

    private companion object {
        const val CACHE_DIR = "announcement"
    }
}
