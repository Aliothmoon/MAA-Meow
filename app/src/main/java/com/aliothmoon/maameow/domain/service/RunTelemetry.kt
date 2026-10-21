package com.aliothmoon.maameow.domain.service

import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.AsstMsg
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.schedule.model.ExecutionResult

/** 全部方法可在任意线程调用且不阻塞；开关关着或构建没带 DSN 时空转 */
interface RunTelemetry {

    /** 会话开启之后、追加任务之前调用；返回本轮的关联 ID，未启用时为 null */
    fun onRunStarted(kind: RunKind, tasks: List<MaaTaskParams>): String?

    fun onTaskRegistered(taskId: Int, params: String)

    /** 后台虚拟屏建好后调，[vdm] 为 VirtualDevice 后端，[flagTier] 为 0 即完整权限 */
    fun onVirtualDisplayStarted(vdm: Boolean, flagTier: Int)

    fun onCallback(message: AsstMsg, details: JSONObject?)

    /** [code] 是失败环节的状态码；哪些算故障由实现筛 */
    fun onStartFailed(code: String, cause: Throwable? = null)

    /** [state] 须是置 ERROR 之前的执行状态 */
    fun onServiceDied(state: MaaExecutionState)

    /** 定时或外部触发的一次启动收尾，成功与否都调；哪些结果算故障由实现筛 */
    fun onLaunchFinished(outcome: LaunchOutcome)

    /** 发出 Shizuku 未运行提醒时调 */
    fun onShizukuDown(afterBoot: Boolean, enabledSchedules: Int)
}

data class LaunchOutcome(
    val result: ExecutionResult,
    /** 终局文案的英文模板，不随界面语言和参数变，用来归类；没有文案时为 null */
    val reason: String?,
    /** 按界面语言解析好的完整文案 */
    val message: String?,
    /** 实际触发比计划时间晚了多少；非定时触发没有计划时间 */
    val delayMs: Long?,
    val runMode: String,
    /** 触发日志相对 debug 目录的路径 */
    val logFile: String,
    /** 唤醒解锁没成时才有 */
    val unlock: UnlockAttempt? = null,
)

/** 一次没成的唤醒解锁；不带凭证本身 */
data class UnlockAttempt(
    /** 设置里选的解锁方式 */
    val type: String,
    /** 凭证是否配好：PIN 已填或手势已录 */
    val ready: Boolean,
    /** 提权侧结果，[WakeUnlockEngine.WakeResult] 的小写名 */
    val result: String,
)

enum class RunKind(val value: String) {
    CHAIN("chain"),

    /** 作业、工具箱、小游戏 */
    AUX("aux"),
}
