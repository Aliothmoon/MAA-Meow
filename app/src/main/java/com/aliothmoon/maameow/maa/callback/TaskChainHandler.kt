package com.aliothmoon.maameow.maa.callback

import android.content.Context
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.domain.service.RuntimeLogCenter
import com.aliothmoon.maameow.maa.AsstMsg
import timber.log.Timber

/**
 * 处理 TaskChain 级别回调（msg 10000-10004 + AllTasksCompleted=3）
 */
class TaskChainHandler(
    applicationContext: Context,
    private val runtimeLogCenter: RuntimeLogCenter,
    private val copilotRuntimeStateStore: CopilotRuntimeStateStore
)  {
    private val resources = applicationContext.resources
    private val packageName = applicationContext.packageName
    /**
     * 处理 TaskChain 回调消息
     *
     * @param msg 回调消息类型
     * @param details 回调详情 JSON
     */
    fun handle(msg: AsstMsg, details: JSONObject) {
        when (msg) {
            AsstMsg.TaskChainError -> handleTaskChainError(details)
            AsstMsg.TaskChainStart -> handleTaskChainStart(details)
            AsstMsg.TaskChainCompleted -> handleTaskChainCompleted(details)
            AsstMsg.TaskChainExtraInfo -> handleTaskChainExtraInfo(details)
            AsstMsg.TaskChainStopped -> handleTaskChainStopped(details)
            AsstMsg.AllTasksCompleted -> handleAllTasksCompleted()
            else -> Timber.w("TaskChainHandler received unexpected msg: $msg")
        }
    }

    /**
     * TaskChainError (10000): 任务链错误
     */
    private fun handleTaskChainError(details: JSONObject) {
        val taskchain = details.getString("taskchain") ?: "Unknown"
        val taskName = str(taskchain)
        runtimeLogCenter.append("${str("TaskError")}$taskName", LogLevel.ERROR)
    }

    /**
     * TaskChainStart (10001): 任务链开始
     */
    private fun handleTaskChainStart(details: JSONObject) {
        val taskchain = details.getString("taskchain") ?: "Unknown"
        val taskName = str(taskchain)
        runtimeLogCenter.append("${str("StartTask")}$taskName", LogLevel.TRACE)
    }

    /**
     * TaskChainCompleted (10002): 任务链完成
     */
    private fun handleTaskChainCompleted(details: JSONObject) {
        val taskchain = details.getString("taskchain") ?: "Unknown"
        val taskName = str(taskchain)
        runtimeLogCenter.append("${str("CompleteTask")}$taskName", LogLevel.SUCCESS)
        // TODO 实现 Fight 任务的理智消耗
    }

    /**
     * TaskChainExtraInfo (10003): 任务链额外信息
     */
    private fun handleTaskChainExtraInfo(details: JSONObject) {
        val what = details.getString("what")
        when (what) {
            "StageDrops-Stars-3", "StageDrops-Stars-Adverse" -> {
                copilotRuntimeStateStore.markTaskSuccess()
                runtimeLogCenter.append(str("CompleteCombat"), LogLevel.INFO)
            }
            "RoutingRestart" -> {
                val why = details.getString("why")
                if (why == "TooManyBattlesAhead") {
                    val cost = details.getString("node_cost") ?: "?"
                    runtimeLogCenter.append(
                        str("RoutingRestartTooManyBattles", cost),
                        LogLevel.WARNING
                    )
                } else {
                    Timber.d("TaskChainExtraInfo RoutingRestart with unhandled why=$why")
                }
            }
            else -> {
                Timber.d("TaskChainExtraInfo unhandled what=$what, details=$details")
            }
        }
    }

    /**
     * TaskChainStopped (10004): 任务链停止（用户手动停止）
     */
    private fun handleTaskChainStopped(details: JSONObject) {
        val taskchain = details.getString("taskchain") ?: "Unknown"
        val taskName = str(taskchain)
        runtimeLogCenter.append("${str("CompleteTask")}$taskName", LogLevel.INFO)
    }

    /**
     * AllTasksCompleted (3): 所有任务完成
     */
    private fun handleAllTasksCompleted() {
        // TODO 计算耗时，处理 SanityReport
        runtimeLogCenter.append(str("AllTasksComplete", ""), LogLevel.SUCCESS)
    }

    /**
     * 辅助方法：获取 i18n 字符串（无参数）
     */
    private fun str(key: String): String {
        return MaaStringRes.getString(resources, packageName, key)
    }

    /**
     * 辅助方法：获取 i18n 字符串（带参数）
     */
    private fun str(key: String, vararg args: Any): String {
        return MaaStringRes.getString(resources, packageName, key, *args)
    }
}
