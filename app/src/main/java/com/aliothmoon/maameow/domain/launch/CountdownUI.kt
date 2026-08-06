package com.aliothmoon.maameow.domain.launch

/** 倒计时呈现唯一 seam；Domain 不依赖 Overlay/Compose；仅后台 Dialog 路径使用 */
interface CountdownUI {
    /**
     * @return true 若用户点了立即执行；false 若自然走完或被取消（[shouldAbort]）
     */
    suspend fun await(
        request: LaunchRequest,
        onTick: (remainingSeconds: Int) -> Unit,
        shouldAbort: () -> Boolean,
    ): Boolean
}
