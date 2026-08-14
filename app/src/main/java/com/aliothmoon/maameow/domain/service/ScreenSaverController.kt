package com.aliothmoon.maameow.domain.service

/** 任务期间屏保，domain 只认 show/hide */
interface ScreenSaverController {
    /** 已盖上：overlay 持有 KEEP_SCREEN_ON，系统锁可能还在下面 */
    fun isShowing(): Boolean

    /** 返回是否真的盖上（无悬浮窗权限会失败） */
    suspend fun show(): Boolean

    suspend fun hide()
}
