package com.aliothmoon.maameow.data.notification.live

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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
 * 通知每秒刷新，不能每次都做文件 IO / 位图解码，命中缓存即同步返回；
 * 内置图标也在这里转成位图（状态栏与超级岛焦点负载对位图支持最稳）。
 * 自定义图片由 [ensureDecoded] 在 IO 线程解码，完成后回调刷新当前通知。
 *
 * [ConcurrentHashMap] 不允许 null，故成功位图与失败/无效标记分开存，
 * 避免解码失败时写入 null 抛异常，也避免每次刷新都重试解析。
 */
class TrackerIconStore(
    context: Context,
    private val appSettings: AppSettingsManager,
) {
    private val appContext = context.applicationContext
    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val unavailable = ConcurrentHashMap.newKeySet<String>()
    private val iconRevision = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val decodeJob = AtomicReference<Job?>(null)

    /** 当前设置下的自定义图标位图；DEFAULT 返回 null，由调用方沿用各自默认图标 */
    fun bitmapOrNull(): Bitmap? = when (appSettings.liveUpdateTrackerIcon.value) {
        AppSettingsManager.LiveUpdateTrackerIcon.DEFAULT -> null
        AppSettingsManager.LiveUpdateTrackerIcon.LOGO -> presetBitmap(R.drawable.ic_maa_logo)
        AppSettingsManager.LiveUpdateTrackerIcon.DOT -> presetBitmap(R.drawable.ic_tracker_dot)
        AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM -> customBitmap()
    }

    /**
     * 图标状态版本号：解码完成、缓存清理等会改变实际图标的变化时递增。
     * 供通知侧判断「外观变了」——图标解码不改 LiveSession 其它字段，只看指纹会被去重丢掉。
     */
    fun revision(): Int = iconRevision.get()

    /** 自定义图标是否还没按当前设置解码完成 */
    fun needsDecode(): Boolean {
        if (appSettings.liveUpdateTrackerIcon.value != AppSettingsManager.LiveUpdateTrackerIcon.CUSTOM) {
            return false
        }
        val path = appSettings.liveUpdateCustomTrackerPath.value
        if (path.isEmpty()) return false
        val key = keyOf(path)
        return !cache.containsKey(key) && !unavailable.contains(key)
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
        pruneStaleCustomEntries(key)
        val job = scope.launch {
            decodeToCache(key, path)
            withContext(Dispatchers.Main) { onReady() }
        }
        decodeJob.set(job)
    }

    private fun presetBitmap(@DrawableRes id: Int): Bitmap? {
        val key = "res|$id"
        cache[key]?.let { return it }
        if (unavailable.contains(key)) return null
        val bitmap = runCatching {
            AppCompatResources.getDrawable(appContext, id)?.toBitmap()
        }.getOrNull()
        if (bitmap == null) unavailable.add(key) else cache[key] = bitmap
        return bitmap
    }

    private fun decodeToCache(key: String, path: String) {
        val decoded = TrackerIconDecoder.decode(path)
        // 解码期间用户可能已切换路径，过期结果丢弃，避免覆盖新路径
        if (keyOf(appSettings.liveUpdateCustomTrackerPath.value) != key) {
            decoded?.recycle()
            return
        }
        if (decoded == null) unavailable.add(key) else cache[key] = decoded
        iconRevision.incrementAndGet()
    }

    private fun customBitmap(): Bitmap? {
        val path = appSettings.liveUpdateCustomTrackerPath.value
        if (path.isEmpty()) return null
        return cache[keyOf(path)]
    }

    /**
     * 清理旧路径的缓存条目：换图会换路径，不清理会随换图次数无上限增长。
     * 不主动 recycle——旧位图可能仍被已构建的通知引用，交给 GC 回收。
     */
    private fun pruneStaleCustomEntries(currentKey: String) {
        val removed = cache.keys.filter { it.startsWith(CUSTOM_PREFIX) && it != currentKey }
        removed.forEach { cache.remove(it) }
        val removedFlags = unavailable.removeIf { it.startsWith(CUSTOM_PREFIX) && it != currentKey }
        if (removed.isNotEmpty() || removedFlags) iconRevision.incrementAndGet()
    }

    private fun keyOf(path: String): String = "$CUSTOM_PREFIX$path"

    private companion object {
        const val CUSTOM_PREFIX = "custom|"
    }
}
