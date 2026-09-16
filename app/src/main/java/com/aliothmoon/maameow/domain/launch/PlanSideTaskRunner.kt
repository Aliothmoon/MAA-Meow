package com.aliothmoon.maameow.domain.launch

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.domain.models.PlanSideTask
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.domain.service.OperBoxYituliuSync
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.resolve
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 跑计划里的旁路任务，一律在自己的 scope 里执行，调用方销毁或取消不打断拉取
 *
 * 结果写会话日志，并回一条给调用方做提示
 */
class PlanSideTaskRunner(
    private val appContext: Context,
    private val operBoxYituliuSync: OperBoxYituliuSync,
    private val sessionLogger: MaaSessionLogger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    data class Outcome(val ok: Boolean, val message: UiText)

    /** 主任务链还要起 Core，发出去就不管；日志由会话接住 */
    fun launch(tasks: List<PlanSideTask>) {
        tasks.forEach { task -> scope.launch { run(task) } }
    }

    /**
     * 只有旁路任务的一轮：开始与结果都给 [notify]，有失败取失败
     *
     * 必须等在这里 —— 定时的前台服务与唤醒锁只覆盖到调用方返回为止
     */
    suspend fun runWithoutCore(
        tasks: List<PlanSideTask>,
        notify: suspend (UiText) -> Unit = {},
    ): Outcome? {
        if (tasks.isEmpty()) return null
        tasks.forEach { notify(uiTextOf(it.runningRes)) }
        val outcomes = tasks.map { task -> scope.async { run(task) } }.map { it.await() }
        val outcome = outcomes.firstOrNull { !it.ok } ?: outcomes.last()
        notify(outcome.message)
        return outcome
    }

    private suspend fun run(task: PlanSideTask): Outcome = try {
        when (task) {
            PlanSideTask.OPER_BOX_YITULIU -> runOperBoxYituliu()
        }
    } catch (e: Exception) {
        // scope 没挂异常处理器，漏出去会崩进程
        Timber.e(e, "Side task failed: %s", task)
        Outcome(false, uiTextOf(R.string.oper_box_yituliu_internal_error))
    }

    private suspend fun runOperBoxYituliu(): Outcome {
        // 用 appendAndWait 而非 append：没有会话时入队的日志没人排空
        sessionLogger.appendAndWait(appContext.getString(R.string.oper_box_yituliu_fetching))
        return when (val result = operBoxYituliuSync.sync()) {
            is OperBoxYituliuSync.Result.Success -> {
                val message = result.message
                sessionLogger.appendAndWait(message.resolve(appContext), LogLevel.SUCCESS)
                Outcome(true, message)
            }

            is OperBoxYituliuSync.Result.Failed -> {
                sessionLogger.appendAndWait(
                    appContext.getString(
                        R.string.runlog_oper_box_yituliu_failed,
                        result.message.resolve(appContext),
                    ),
                    LogLevel.ERROR,
                )
                Outcome(false, result.message)
            }
        }
    }
}
