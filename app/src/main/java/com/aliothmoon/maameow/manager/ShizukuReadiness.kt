package com.aliothmoon.maameow.manager

enum class ShizukuReadinessStage {
    /** 未安装 Shizuku（也未检测到 Sui） */
    NotInstalled,

    /** 已安装但服务未启动 */
    NotRunning,

    /** 服务运行中但未授权 */
    NeedAuth,

    /** 就绪：已授权、当前后端非 Shizuku，或用户已跳过检查 */
    Ready,
}


data class ShizukuReadiness(
    val stage: ShizukuReadinessStage = ShizukuReadinessStage.Ready,
    val canSwitchToRoot: Boolean = false,
) {
    /** 是否需要向用户展示引导弹窗 */
    val needsGuidance: Boolean
        get() = stage != ShizukuReadinessStage.Ready

    /** 有没有用户能照着做的修复步骤 */
    val fixable: Boolean
        get() = stage == ShizukuReadinessStage.NotInstalled ||
                stage == ShizukuReadinessStage.NotRunning ||
                stage == ShizukuReadinessStage.NeedAuth
}
