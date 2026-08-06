package com.aliothmoon.maameow.domain.launch

/** 倒计时呈现唯一 seam；Domain 不依赖 Overlay/Compose */
interface CountdownUI {
    /**
     * @return true 若用户点了立即执行；false 若自然走完或被取消（[shouldAbort]）
     */
    suspend fun await(
        request: LaunchRequest,
        mode: CountdownMode,
        onTick: (remainingSeconds: Int) -> Unit,
        shouldAbort: () -> Boolean,
    ): Boolean
}
