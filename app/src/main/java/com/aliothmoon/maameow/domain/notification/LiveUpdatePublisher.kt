package com.aliothmoon.maameow.domain.notification

import android.app.Notification

interface LiveUpdatePublisher {
    val capability: LiveCapability

    /** 丢掉探测缓存后重新取；设置页刷新用 */
    fun refreshCapability(): LiveCapability = capability

    fun notifyId(sessionId: String): Int = LiveNotifyIds.of(sessionId)
    fun build(session: LiveSession): Notification
    fun publish(session: LiveSession)

    /** 进度会话即将开始，后端可在此做耗时准备（HyperOS 断网闸门）；调用方须保证不在主线程 */
    fun prepareProgress() = Unit

    /** 构建一次、先 notify、返回同一实例供 FGS startForeground 用 */
    fun publishForeground(session: LiveSession): Notification
    fun cancel(sessionId: String)
}
