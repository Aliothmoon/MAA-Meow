package com.aliothmoon.maameow.maa.callback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/** 预检错误计数，收尾前等待异步拒收原因入日志 */
class SetParamsErrorSignal {
    private val count = MutableStateFlow(0)

    val current: Int get() = count.value

    fun mark() = count.update { it + 1 }

    suspend fun awaitAfter(since: Int, timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { count.first { it > since } } != null
}
