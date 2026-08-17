package com.aliothmoon.maameow.schedule.model

/** 触发失败结果 → 修复入口的纯映射 */

/** 修复入口类型 */
enum class ScheduleFixAction {
    /** 解锁凭证（PIN/手势）配置或无障碍检查，应用内设置页 */
    UNLOCK_CREDENTIAL,

    /** 当前启动模式（Shizuku/Root）授权检查 */
    BACKEND_READY,
}

object ExecutionFixMapping {

    /** null = 无需/无法给出修复入口 */
    fun fixActionFor(result: ExecutionResult): ScheduleFixAction? = when (result) {
        ExecutionResult.SKIPPED_LOCKED -> ScheduleFixAction.UNLOCK_CREDENTIAL
        ExecutionResult.FAILED_UI_LAUNCH,
        ExecutionResult.FAILED_START -> ScheduleFixAction.BACKEND_READY
        else -> null
    }
}
