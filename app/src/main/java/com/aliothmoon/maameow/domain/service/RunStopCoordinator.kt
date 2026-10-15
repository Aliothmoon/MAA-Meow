package com.aliothmoon.maameow.domain.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 所有停止入口共享一次执行；排队的同轮请求取得同一结果，不接管新会话。 */
internal class RunStopCoordinator {
    private val mutex = Mutex()
    private var completed: Pair<Long, MaaCompositionService.StopResult>? = null

    suspend fun stop(
        runToken: Long,
        canStop: () -> Boolean,
        perform: suspend () -> MaaCompositionService.StopResult,
    ): MaaCompositionService.StopResult = mutex.withLock {
        completed?.takeIf { it.first == runToken }?.let { return@withLock it.second }
        if (!canStop()) return@withLock MaaCompositionService.StopResult.Success
        perform().also { completed = runToken to it }
    }
}
