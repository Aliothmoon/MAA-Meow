package com.aliothmoon.maameow.maa.callback

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.AsstMsg
import com.aliothmoon.maameow.maa.CallbackJsonAbbreviator
import timber.log.Timber

class MaaCallbackDispatcher(
    private val sessionLogger: MaaSessionLogger,
    private val stateHolder: MaaExecutionStateHolder,
    private val connectionInfoHandler: ConnectionInfoHandler,
    private val taskChainHandler: TaskChainHandler,
    private val subTaskHandler: SubTaskHandler,
    private val notificationCenter: MaaNotificationCenter,
) {

    fun onEvent(msg: Int, json: String?) {
        val message = AsstMsg.fromValue(msg)
        if (message == null) {
            Timber.w("收到未知消息类型: msg=$msg, json=$json")
            return
        }

        // 用格式化参数而非字符串模板：模板会在调用前无条件拼接整份 json
        // （SubTaskExtraInfo 携带识别结果时可达数百 KB），而这里在回调热路径上
        Timber.d("onEvent: msg=%s, json=%s", message, CallbackJsonAbbreviator.abbreviate(json))

        // 解析 JSON details
        val details: JSONObject? = try {
            if (json.isNullOrBlank()) null else JSON.parseObject(json)
        } catch (e: Exception) {
            Timber.e(e, "解析回调 JSON 失败: msg=$message, json=$json")
            null
        }

        // 根据消息类型分发
        when (message) {
            AsstMsg.InternalError -> handleInternalError(details)
            AsstMsg.InitFailed -> handleInitFailed(details)
            AsstMsg.ConnectionInfo -> handleConnectionInfo(details)
            AsstMsg.TaskChainStart -> handleTaskChainStart(details)
            AsstMsg.AllTasksCompleted -> handleAllTasksCompleted()
            AsstMsg.TaskChainError -> handleTaskChainError(details)
            AsstMsg.TaskChainCompleted -> handleTaskChainCompleted(details)
            AsstMsg.TaskChainStopped -> handleTaskChainStopped()
            AsstMsg.TaskChainExtraInfo -> handleTaskChainExtraInfo(details)
            AsstMsg.AsyncCallInfo -> handleAsyncCallInfo(details)
            AsstMsg.Destroyed -> handleDestroyed(details)
            AsstMsg.SubTaskError -> handleSubTaskError(details)
            AsstMsg.SubTaskStart -> handleSubTaskStart(details)
            AsstMsg.SubTaskCompleted -> handleSubTaskCompleted(details)
            AsstMsg.SubTaskExtraInfo -> handleSubTaskExtraInfo(details)
            AsstMsg.SubTaskStopped -> handleSubTaskStopped(details)
            AsstMsg.ReportRequest -> handleReportRequest(details)
        }
    }


    private fun handleInternalError(details: JSONObject?) {
        Timber.w("MaaCore 内部错误: ${details ?: ""}")
    }

    private fun handleInitFailed(details: JSONObject?) {
        val what = details?.getString("what") ?: ""
        val why = details?.getString("why") ?: ""
        Timber.e("MaaCore 初始化失败: what=$what, why=$why")
        stateHolder.reportRunState(MaaExecutionState.ERROR)
        sessionLogger.append(
            "初始化失败: $what${if (why.isNotEmpty()) " ($why)" else ""}",
            LogLevel.ERROR
        )
        sessionLogger.endSession("INIT_FAILED")
    }

    private fun handleConnectionInfo(details: JSONObject?) {
        details?.let { connectionInfoHandler.onConnectionInfo(it) }
    }

    private fun handleTaskChainStart(details: JSONObject?) {
        details?.let { taskChainHandler.onTaskChainStart(it) }
    }

    private fun handleAllTasksCompleted() {
        stateHolder.reportRunState(MaaExecutionState.IDLE)
        // 不依赖 details：全部完成的收尾（清运行时登记、写总结、发通知）
        // 不该因为这一条回调的 JSON 解析失败而被整个跳过
        taskChainHandler.onAllTasksCompleted()
        sessionLogger.endSession("COMPLETED")
    }

    private fun handleTaskChainError(details: JSONObject?) {
        // 单条任务链出错不终止全局状态和日志会话
        // MaaCore 会继续执行后续任务链，最终由 AllTasksCompleted 或 TaskChainStopped 收尾
        details?.let { taskChainHandler.onTaskChainError(it) }
    }

    private fun handleTaskChainCompleted(details: JSONObject?) {
        details?.let { taskChainHandler.onTaskChainCompleted(it) }
    }

    private fun handleTaskChainStopped() {
        stateHolder.reportRunState(MaaExecutionState.IDLE)
        // 同 handleAllTasksCompleted：停止收尾不依赖 details，不该被 JSON 解析失败跳过
        taskChainHandler.onTaskChainStopped()
        sessionLogger.endSession("STOPPED")
    }

    private fun handleTaskChainExtraInfo(details: JSONObject?) {
        details?.let { taskChainHandler.onTaskChainExtraInfo(it) }
    }

    private fun handleAsyncCallInfo(details: JSONObject?) {
        Timber.d("收到 AsyncCallInfo，由 MaaCompositionService 处理")
    }

    private fun handleDestroyed(details: JSONObject?) {
        stateHolder.reportRunState(MaaExecutionState.IDLE)
        Timber.i("MaaCore 实例已销毁")
        sessionLogger.completeSession("DESTROYED", "MaaCore 实例已销毁", LogLevel.WARNING)
    }

    private fun handleSubTaskError(details: JSONObject?) {
        details?.let { subTaskHandler.onSubTaskError(it) }
    }

    private fun handleSubTaskStart(details: JSONObject?) {
        details?.let { subTaskHandler.onSubTaskStart(it) }
    }

    private fun handleSubTaskCompleted(details: JSONObject?) {
        details?.let { subTaskHandler.onSubTaskCompleted(it) }
    }

    private fun handleSubTaskExtraInfo(details: JSONObject?) {
        details?.let { subTaskHandler.onSubTaskExtraInfo(it) }
    }

    private fun handleSubTaskStopped(details: JSONObject?) {
        Timber.d("SubTask 已停止")
    }

    private fun handleReportRequest(details: JSONObject?) {
        // TODO
        Timber.d("收到 ReportRequest（暂未实现上报功能）: $details")
    }

}
