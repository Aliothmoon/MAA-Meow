package com.aliothmoon.maameow.domain.launch

import timber.log.Timber

/**
 * 自动化启动全局互斥：同一时刻至多一次 in-flight 启动流
 * 进程内内存；不跨进程；start 成功/失败后应立即 [release]，不占满 MAA RUNNING 全程
 */
class LaunchMutex {

    data class Holder(val requestId: String, val label: String)

    private var holder: Holder? = null

    val current: Holder?
        @Synchronized get() = holder

    /** 已有持有者（含同 requestId）一律失败 */
    @Synchronized
    fun tryAcquire(requestId: String, label: String = "pipeline"): Boolean {
        val existing = holder
        if (existing != null) {
            Timber.i(
                "LaunchMutex: %s/%s acquire failed, held by %s/%s",
                label,
                requestId,
                existing.label,
                existing.requestId,
            )
            return false
        }
        holder = Holder(requestId, label)
        return true
    }

    /** 抢占：释放任意现有 holder（调用方负责 cancel Job / stop composition） */
    @Synchronized
    fun forceAcquire(requestId: String, label: String = "pipeline") {
        holder = Holder(requestId, label)
    }

    @Synchronized
    fun release(requestId: String) {
        if (holder?.requestId == requestId) {
            holder = null
        }
    }

    @Synchronized
    fun releaseAny() {
        holder = null
    }
}
