package com.aliothmoon.maameow.domain.service

/** 任务期间屏保，domain 只认 show/hide */
interface ScreenSaverController {
    /** 返回是否真的盖上（无悬浮窗权限会失败） */
    suspend fun show(): Boolean

    suspend fun hide()
}
