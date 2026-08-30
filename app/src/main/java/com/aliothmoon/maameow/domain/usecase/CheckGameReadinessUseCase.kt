package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.AppAliveChecker
import com.aliothmoon.maameow.remote.AppAliveStatus
import timber.log.Timber

/**
 * 启动前的「游戏就绪性」闸门：检查目标游戏是否已安装、是否在运行、(后台模式)是否跑在目标虚拟屏上。
 *
 * 这是所有启动入口(主任务链 / Copilot / Toolbox)共享的唯一前置校验来源。新增前置检查时只需在此
 * 增加一个分支 + 对应的 [TaskStartAcknowledgement]/[TaskStartDecisionReason]，各入口自动继承。
 */
class CheckGameReadinessUseCase(
    private val appAliveChecker: AppAliveChecker,
    private val appSettings: AppSettingsManager,
    private val achievementReporter: AchievementReporter,
    private val isPackageInstalled: suspend (String) -> Boolean = { true },
) {
    /**
     * @param clientType   游戏客户端类型(用于解析包名)
     * @param launchesGame 本次启动是否会主动拉起游戏(会则跳过「未运行」检查)
     * @param context      启动模式 + 用户已确认的警告集合
     */
    suspend operator fun invoke(
        clientType: String,
        launchesGame: Boolean,
        context: TaskStartContext,
    ): GameReadiness {
        val packageName = Packages[clientType]
        if (packageName == null) {
            Timber.w(
                "CheckGameReadiness: cannot resolve package name for clientType=%s",
                clientType
            )
            return GameReadiness.Ready(gameAliveBeforeStart = null)
        }

        // 1) 安装检查
        if (!isPackageInstalled(packageName)
            && !context.acknowledgements.contains(TaskStartAcknowledgement.GAME_NOT_INSTALLED)
        ) {
            return confirmOrBlock(
                context.mode,
                reason = TaskStartDecisionReason.GAME_NOT_INSTALLED,
                ack = TaskStartAcknowledgement.GAME_NOT_INSTALLED,
            )
        }

        val runMode = appSettings.runMode.value
        val aliveStatus = appAliveChecker.isAppAlive(packageName)

        // 2) 会主动拉起游戏 / 前台模式 / 已确认「未运行」→ 跳过存活检查
        if (launchesGame
            || runMode == RunMode.FOREGROUND
            || context.acknowledgements.contains(TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP)
        ) {
            return GameReadiness.Ready(gameAliveBeforeStart = aliveStatus == AppAliveStatus.ALIVE)
        }

        // 3) 存活检查
        return when (aliveStatus) {
            AppAliveStatus.DEAD -> {
                achievementReporter.reportTaskStartBlocked(
                    TaskStartDecisionReason.GAME_NOT_RUNNING_WITHOUT_WAKE_UP.name
                )
                confirmOrBlock(
                    context.mode,
                    reason = TaskStartDecisionReason.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
                    ack = TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
                )
            }

            AppAliveStatus.ALIVE -> {
                // 游戏存活；后台模式下进一步确认游戏跑在目标 VD 上
                if (runMode == RunMode.BACKGROUND
                    && appAliveChecker.isAppOnBackgroundDisplay(packageName) == false
                ) {
                    GameReadiness.Blocked(TaskStartDecisionReason.GAME_NOT_ON_BACKGROUND_DISPLAY)
                } else {
                    GameReadiness.Ready(gameAliveBeforeStart = true)
                }
            }

            else -> {
                // UNKNOWN 或非预期状态：无法确认游戏是否存活，保守放行
                Timber.w(
                    "CheckGameReadiness: unknown alive status %d for %s",
                    aliveStatus,
                    packageName
                )
                GameReadiness.Ready(gameAliveBeforeStart = null)
            }
        }
    }

    private fun confirmOrBlock(
        mode: TaskStartMode,
        reason: TaskStartDecisionReason,
        ack: TaskStartAcknowledgement,
    ): GameReadiness = when (mode) {
        TaskStartMode.MANUAL -> GameReadiness.RequiresConfirmation(ack)
        TaskStartMode.SCHEDULED -> GameReadiness.Blocked(reason)
    }
}

sealed interface GameReadiness {
    /** 可以启动。[gameAliveBeforeStart] 仅用于成就统计：true/false=已确认存活状态，null=未知/未检查。 */
    data class Ready(val gameAliveBeforeStart: Boolean?) : GameReadiness

    /** 需用户确认后方可继续(手动模式)。*/
    data class RequiresConfirmation(val acknowledgement: TaskStartAcknowledgement) : GameReadiness

    /** 拦截，不可启动。*/
    data class Blocked(val reason: TaskStartDecisionReason) : GameReadiness
}
