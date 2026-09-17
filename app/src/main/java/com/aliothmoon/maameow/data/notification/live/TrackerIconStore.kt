package com.aliothmoon.maameow.data.notification.live

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
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

/**
 * 用户自定义的通知图标来源，统一输出位图。
 *
 * 通知每秒刷新，不能每次都做文件 IO / 位图解码 / SVG 解析，命中缓存即同步返回。
 * 内置图标也在这里转成位图：通知状态栏与超级岛焦点负载对位图的支持最稳。
 * 自定义图片由 [ensureDecoded] 在 IO 线程解码，完成后回调刷新当前通知。
 */
class TrackerIconStore(
    context: Context,
    private val appSettings: AppSettingsManager,
) {
    private val appContext = context.applicationContext
    private val cache = ConcurrentHashMap<String, Bitmap?>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val decodeJob = AtomicReference<Job?>(null)

    /** 当前设置下的自定义图标位图；DEFAULT 返回 null，由调用方沿用各自默认图标 */
    fun bitmapOrNull(): Bitmap? = when (appSettings.liveUpdateTrackerIcon.value) {
        AppSettingsManager.LiveUpdateTrackerIcon.DEFAULT -> null
        AppSettingsManager.LiveUpdateTrackerIcon.LOGO -> presetBitmap(R.drawable.ic_maa_logo)
        AppSettingsManager.LiveUpdateTrackerIcon.DOT -> presetBitmap(R.drawable.ic_tracker_dot)
        AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM -> customBitmap()
    }

    /** 自定义图标是否还没按当前设置解码完成 */
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

    private fun presetBitmap(@DrawableRes id: Int): Bitmap? {
        val key = "res|$id"
        cache[key]?.let { return it }
        if (cache.containsKey(key)) return null
        val bitmap = runCatching {
            AppCompatResources.getDrawable(appContext, id)?.toBitmap()
        }.getOrNull()
        cache[key] = bitmap
        return bitmap
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
