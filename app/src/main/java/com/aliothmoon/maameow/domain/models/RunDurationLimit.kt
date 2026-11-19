package com.aliothmoon.maameow.domain.models

/** 运行时长上限取值范围（分钟） */
object RunDurationLimit {
    const val DEFAULT_MINUTES = 240
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 1440

    /**
     * 本轮生效的上限（分钟），null 为不计时
     * 配置单独设置的优先，不看全局开关；未设置时才跟随全局
     */
    fun resolve(profileMinutes: Int?, globalEnabled: Boolean, globalMinutes: Int): Int? = when {
        profileMinutes != null -> profileMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        globalEnabled -> globalMinutes
        else -> null
    }
}
