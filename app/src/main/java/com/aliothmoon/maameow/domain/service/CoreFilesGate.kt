package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.state.isIdle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 改 core 会读的文件只能走这里：等任务空闲，且与资源加载、任务启动互斥 */
interface CoreFilesGate {
    suspend fun <T> whenIdle(block: suspend () -> T): T
}

internal suspend fun <T> Mutex.withLockWhenIdle(
    state: StateFlow<MaaExecutionState>,
    block: suspend () -> T,
): T {
    while (true) {
        withLock { if (state.value.isIdle) return block() }
        state.first { it.isIdle }
    }
}
