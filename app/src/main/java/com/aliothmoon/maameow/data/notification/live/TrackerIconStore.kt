package com.aliothmoon.maameow.data.notification.live

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.notification.TrackerIconDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 追踪图标来源：内置资源或解码后的自定义位图 */
sealed interface TrackerIconSource {
    data class Res(@param:DrawableRes val id: Int) : TrackerIconSource
    data class Bmp(val bitmap: Bitmap) : TrackerIconSource
}

/**
 * 自定义追踪图标缓存。
 *
 * 通知每秒刷新，不能每次都做文件 IO / 位图解码 / SVG 解析：缓存命中时同步返回，
 * 未命中时由 [ensureDecoded] 在 IO 线程异步解码，完成后回调刷新当前通知。
 * 位数有限、路径切换少，缓存放 [ConcurrentHashMap] 即可，主线程读、IO 线程写均安全。
 */
class TrackerIconStore(
    private val appSettings: AppSettingsManager,
) {
    private val cache = ConcurrentHashMap<String, Bitmap?>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val decodeJob = AtomicReference<Job?>(null)

    /** 当前设置下的追踪图标；自定义图标尚未解码完成时回退默认图标 */
    fun resolve(): TrackerIconSource = when (appSettings.liveUpdateTrackerIcon.value) {
        AppSettingsManager.LiveUpdateTrackerIcon.LOGO -> TrackerIconSource.Res(R.drawable.ic_maa_logo)
        AppSettingsManager.LiveUpdateTrackerIcon.DOT -> TrackerIconSource.Res(R.drawable.ic_tracker_dot)
        AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM -> {
            val bitmap = customBitmap()
            if (bitmap != null) TrackerIconSource.Bmp(bitmap)
            else TrackerIconSource.Res(R.drawable.ic_progress_tracker)
        }
        AppSettingsManager.LiveUpdateTrackerIcon.DEFAULT -> TrackerIconSource.Res(R.drawable.ic_progress_tracker)
    }

    /** 自定义图标是否已按当前设置解码完成（用于判断是否需要触发异步解码） */
    fun needsDecode(): Boolean {
        if (appSettings.liveUpdateTrackerIcon.value != AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM) {
            return false
        }
        val path = appSettings.liveUpdateCustomTrackerPath.value
        if (path.isEmpty()) return false
        return !cache.containsKey(keyOf(path))
    }

    /**
     * 自定义图标未缓存时在 IO 解码，成功后回调（主线程）。
     * 同一时刻只允许一个解码任务；切换路径产生的过期结果直接丢弃，不覆盖新路径。
     */
    fun ensureDecoded(onReady: () -> Unit) {
        if (!needsDecode()) return
        if (decodeJob.get()?.isActive == true) return
        val path = appSettings.liveUpdateCustomTrackerPath.value
        val key = keyOf(path)
        val job = scope.launch {
            readCachedOrDecode(key, path)
            withContext(Dispatchers.Main) { onReady() }
        }
        decodeJob.set(job)
    }

    private fun readCachedOrDecode(key: String, path: String): Bitmap? {
        cache[key]?.let { return it }
        if (cache.containsKey(key)) return null
        val decoded = TrackerIconDecoder.decode(path)
        // 解码期间用户可能已切换路径，过期结果丢弃，避免覆盖新路径
        if (keyOf(appSettings.liveUpdateCustomTrackerPath.value) != key) {
            decoded?.recycle()
            return null
        }
        cache[key] = decoded
        return decoded
    }

    private fun customBitmap(): Bitmap? {
        val path = appSettings.liveUpdateCustomTrackerPath.value
        if (path.isEmpty()) return null
        return cache[keyOf(path)]
    }

    private fun keyOf(path: String): String = "custom|$path"
}
