package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 仅追踪本页面成功启动的会话，停止入口可以位于任意页面。 */
internal class TaskStopObserver(
    private val service: MaaCompositionService,
    private val scope: CoroutineScope,
) {
    private var observation: Job? = null

    fun track(
        runToken: Long,
        onStopping: () -> Unit,
        onStopped: (MaaCompositionService.StopResult) -> Unit,
        onFinished: () -> Unit = {},
    ) {
        // 启动结果到达调用方前可能已进入下一轮；不能取消该轮已经建立的观察。
        if (service.currentRunToken != runToken) return
        observation?.cancel()
        // 同步建立订阅，避免启动返回后立即停止时漏过 STOPPING。
        observation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var manuallyStopped = false
            service.state.first { state ->
                if (service.currentRunToken != runToken) return@first true
                if (state == MaaExecutionState.STOPPING &&
                    service.lastStopOrigin == MaaCompositionService.StopOrigin.USER &&
                    !manuallyStopped
                ) {
                    manuallyStopped = true
                    onStopping()
                }
                state == MaaExecutionState.IDLE || state == MaaExecutionState.ERROR
            }
            // StateFlow 可能合并终态与下一轮启动，不用旧收尾覆盖新会话。
            onFinished()
            if (service.currentRunToken != runToken) return@launch
            val result = service.lastStopResult
            if (service.lastStopOrigin == MaaCompositionService.StopOrigin.USER && result != null &&
                service.currentRunToken == runToken
            ) {
                onStopped(result)
            }
        }
    }
}
