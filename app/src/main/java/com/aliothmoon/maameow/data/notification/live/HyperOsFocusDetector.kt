package com.aliothmoon.maameow.data.notification.live

import android.content.Context
import android.os.SystemClock
import com.xzakota.hyper.notification.focus.util.FocusUtils

class HyperOsFocusDetector(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    private var permissionCache: Pair<Long, Boolean>? = null

    fun protocolVersion(): Int = FocusUtils.getFocusProtocolVersion(appContext)

    fun isSupportIsland(): Boolean = FocusUtils.isSupportIsland()

    fun hasFocusPermission(): Boolean {
        val now = SystemClock.elapsedRealtime()
        permissionCache?.let { (at, value) ->
            if (now - at < CACHE_MS) return value
        }
        val granted = FocusUtils.hasFocusPermission(appContext)
        permissionCache = now to granted
        return granted
    }

    fun isLikelyDevice(): Boolean {
        // 只发 V3 岛格式：协议 <3 且无岛特性时 JSON 被系统忽略
        return protocolVersion() >= 3 || isSupportIsland()
    }

    fun isAvailable(): Boolean = isLikelyDevice() && hasFocusPermission()

    /** 用户可能刚在系统设置里改过开关，设置页刷新时丢缓存 */
    fun invalidate() {
        permissionCache = null
    }

    private companion object {
        // FocusUtils.hasFocusPermission 是 ContentProvider 往返（官方标注耗时），
        // 进度 1Hz 刷新时 1s 缓存等于每次都穿透
        const val CACHE_MS = 30_000L
    }
}
