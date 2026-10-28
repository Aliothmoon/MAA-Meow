package com.aliothmoon.maameow.domain.service

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 界面元数据就绪门：干员表、公招 Tag 等按当前客户端与语言装好才放行 */
interface GameMetadataGate {

    /** 等当前档就绪；加载失败、资源初始化失败或超时返回 false，调用方按数据缺失降级 */
    suspend fun awaitCurrent(timeout: Duration = DEFAULT_AWAIT_TIMEOUT): Boolean

    companion object {
        val DEFAULT_AWAIT_TIMEOUT = 10.seconds
    }
}
