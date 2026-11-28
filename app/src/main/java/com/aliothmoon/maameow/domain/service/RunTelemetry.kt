package com.aliothmoon.maameow.domain.service

import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.domain.models.VirtualDisplayBackend
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.AsstMsg
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.remote.VirtualDisplayStatus
import com.aliothmoon.maameow.schedule.model.ExecutionResult

/** 除 [onAppExit] 外全部方法可在任意线程调用且不阻塞；开关关着或构建没带 DSN 时空转 */
interface RunTelemetry {

    /** 会话开启之后、追加任务之前调用；返回本轮的关联 ID，未启用时为 null */
    fun onRunStarted(kind: RunKind, tasks: List<MaaTaskParams>): String?

    fun onTaskRegistered(taskId: Int, params: String)

    fun onVirtualDisplayStarted(mode: VirtualDisplayBackend, status: VirtualDisplayStatus)

    fun onGameProcessGone(kind: GameExitCause.Kind?)

    fun onCallback(message: AsstMsg, details: JSONObject?)

    /** [code] 是失败环节的状态码；哪些算故障由实现筛 */
    fun onStartFailed(code: String, cause: Throwable? = null)

    /** [state] 须是置 ERROR 之前的执行状态 */
    fun onServiceDied(state: MaaExecutionState)

    /** 启动以外的提权侧调用卡死；启动时的走 [onStartFailed] */
    fun onRemoteCallStuck(call: String, threadDump: String?)

    /** 定时或外部触发的一次启动收尾，成功与否都调；哪些结果算故障由实现筛 */
    fun onLaunchFinished(outcome: LaunchOutcome)

    /** 发出 Shizuku 未运行提醒时调 */
    fun onShizukuDown(afterBoot: Boolean, enabledSchedules: Int)

    /** 资源初始化（从内置资产解压）失败时调 */
    fun onResourceInitFailed(report: ResourceInitFailureReport)

    /** 进程马上要退出：挂起到在途上报发完，再关 SDK 把队列发出去 */
    suspend fun onAppExit()
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

/** 一次资源初始化（从内置资产解压）失败的结构化摘要，供遥测归组与附带诊断日志 */
data class ResourceInitFailureReport(
    /** checkAndInit / reInitialize / direct */
    val trigger: String,
    /** manifest / mkdirs / assets.open / FileOutputStream / copy / close / unknown */
    val phase: String,
    /** JDK / Android 标准异常简单名，避免 R8 混淆自家异常类 */
    val errorType: String,
    /** ErrnoException 的 POSIX 错误名（如 ENOENT / EACCES / ENOSPC），无则为 null */
    val errnoName: String?,
    val cause: String,
    val extractedCount: Int,
    val totalCount: Int,
    val attemptFailures: Int,
    val durationMs: Long,
    /** Environment.getExternalStorageState() */
    val externalStorageState: String,
    /** 失败后外置包目录是否仍存在 */
    val externalDirPresent: Boolean,
    /** 失败后内部 filesDir 写入探针结果 */
    val internalWritable: Boolean?,
    /** 失败后目标 asset 直读探针结果 */
    val assetReadable: Boolean?,
    /** 目标存储分区可用空间（MB） */
    val availStorageMb: Long?,
    /** 单次运行的完整诊断报告正文，脱敏后走 Sentry Logs */
    val diagnosticLog: String?,
)

enum class RunKind(val value: String) {
    CHAIN("chain"),

    /** 作业、工具箱、小游戏 */
    AUX("aux"),
}
