package com.aliothmoon.maameow.remote.internal

/**
 * 由单调递增的帧计数与纳秒时间戳估算区间帧率；纯逻辑，便于单测
 */
class FrameCountFpsEstimator {

    private var lastCount = -1L
    private var lastNs = 0L

    /** 返回自上次采样以来的 fps；首个样本、计数回绕（截图器重建）或时间未前进时返回 null */
    fun sample(frameCount: Long, nowNs: Long): Float? {
        val valid = lastCount >= 0 && frameCount >= lastCount && nowNs > lastNs
        val fps = if (valid) (frameCount - lastCount) * 1_000_000_000f / (nowNs - lastNs) else null
        lastCount = frameCount
        lastNs = nowNs
        return fps
    }
}
