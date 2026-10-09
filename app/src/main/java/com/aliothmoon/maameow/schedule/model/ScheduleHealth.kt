package com.aliothmoon.maameow.schedule.model

/**
 * 定时任务「调度环境检查」的纯推导逻辑，便于单测
 *
 * 列表页健康卡与编辑页保存向导共用这一套判定
 */

/** 检查项，枚举顺序即展示/引导顺序（仅未通过项会出现在结果里） */
enum class ScheduleHealthIssue {
    /** 当前启动模式(Shizuku/Root)没在运行：定时触发的唤醒、拉起界面、执行都依赖它 */
    BACKEND_DOWN,

    /** 在运行但未授权 */
    BACKEND,

    /** 未忽略电池优化：后台触发可能被系统延迟或拦截 */
    BATTERY,

    /** 精确闹钟未允许，授权后恢复调度 */
    EXACT_ALARM,

    /** 通知权限未授予：定时执行与失败提醒的通知不可见 */
    NOTIFICATION,

    /** 启用的策略勾选了屏保，但悬浮窗未授予 */
    OVERLAY,

    /** 设备有安全锁屏但没配 PIN/手势：触发时若已锁屏会跳过执行 */
    UNLOCK_CREDENTIAL,
}

/**
 * 权限/状态快照，全部为「是否通过」语义
 *
 * [backendGranted] 只是已授权，不代表服务已连接 —— 环境检查发生在任务开跑前，
 * 此时服务本就未必绑定，用连接状态判断会误报
 */
data class ScheduleHealthSnapshot(
    val backendGranted: Boolean,
    /** 没在运行也就谈不上授权，两者分开报 */
    val backendAvailable: Boolean = true,
    val batteryWhitelist: Boolean,
    val notification: Boolean,
    val exactAlarmAllowed: Boolean,
    val overlayGranted: Boolean,
    /** 任一启用策略勾选了屏保选项 */
    val overlayNeeded: Boolean,
    /** 见 [ScheduleHealthLogic.unlockCredentialMissing]；向导不收这项 */
    val unlockCredentialMissing: Boolean = false,
)

object ScheduleHealthLogic {

    /** 安全锁屏 + 凭证没配 + 有启用策略 */
    fun unlockCredentialMissing(
        deviceSecure: Boolean,
        credentialReady: Boolean,
        strategies: List<ScheduleStrategy>,
    ): Boolean = deviceSecure && !credentialReady && strategies.any { it.enabled }

    /** 硬件关屏不使用悬浮窗；只有启用且实际显示屏保的策略才需要。 */
    fun overlayNeeded(
        strategies: List<ScheduleStrategy>,
        useHardwareScreenOff: Boolean = false,
    ): Boolean = !useHardwareScreenOff && strategies.any { it.enabled && it.autoScreenSaver }

    /** 推导未通过项；空列表 = 全部通过（健康卡应隐藏、向导无需弹出） */
    fun failingIssues(snapshot: ScheduleHealthSnapshot): List<ScheduleHealthIssue> = buildList {
        when {
            !snapshot.backendAvailable -> add(ScheduleHealthIssue.BACKEND_DOWN)
            !snapshot.backendGranted -> add(ScheduleHealthIssue.BACKEND)
        }
        if (!snapshot.batteryWhitelist) add(ScheduleHealthIssue.BATTERY)
        if (!snapshot.exactAlarmAllowed) add(ScheduleHealthIssue.EXACT_ALARM)
        if (!snapshot.notification) add(ScheduleHealthIssue.NOTIFICATION)
        if (snapshot.overlayNeeded && !snapshot.overlayGranted) add(ScheduleHealthIssue.OVERLAY)
        if (snapshot.unlockCredentialMissing) add(ScheduleHealthIssue.UNLOCK_CREDENTIAL)
    }

    /** 后端启动与授权、解锁凭证都当场处理不完，留给健康卡 */
    fun wizardItems(snapshot: ScheduleHealthSnapshot): List<ScheduleHealthIssue> =
        failingIssues(snapshot).filterNot { it in NOT_IN_WIZARD }

    private val NOT_IN_WIZARD = setOf(
        ScheduleHealthIssue.BACKEND_DOWN,
        ScheduleHealthIssue.BACKEND,
        ScheduleHealthIssue.UNLOCK_CREDENTIAL,
    )
}
