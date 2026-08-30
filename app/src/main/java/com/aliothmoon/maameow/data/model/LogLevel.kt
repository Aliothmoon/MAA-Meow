package com.aliothmoon.maameow.data.model

import androidx.annotation.StringRes
import com.aliothmoon.maameow.R

/** 参考 MaaWPFGUI 的 UiLogColor；颜色经 [LogColorRole] 间接表达。标签文案走资源 id，由 UI 层 resolve。 */
enum class LogLevel(
    @param:StringRes val labelRes: Int,
    val colorRole: LogColorRole,
    val severity: LogSeverity
) {
    MESSAGE(R.string.log_level_message, LogColorRole.DEFAULT, LogSeverity.MESSAGE),
    INFO(R.string.log_level_info, LogColorRole.INFO, LogSeverity.INFO),
    SUCCESS(R.string.log_level_success, LogColorRole.SUCCESS, LogSeverity.INFO),
    WARNING(R.string.log_level_warning, LogColorRole.WARNING, LogSeverity.WARNING),
    ERROR(R.string.log_level_error, LogColorRole.ERROR, LogSeverity.ERROR),
    TRACE(R.string.log_level_trace, LogColorRole.TRACE, LogSeverity.TRACE),

    RECRUIT_STAR_1(R.string.log_level_recruit_star_1, LogColorRole.STAR_1, LogSeverity.INFO),
    RECRUIT_STAR_2(R.string.log_level_recruit_star_2, LogColorRole.STAR_2, LogSeverity.INFO),
    RECRUIT_STAR_3(R.string.log_level_recruit_star_3, LogColorRole.STAR_3, LogSeverity.INFO),
    RECRUIT_STAR_4(R.string.log_level_recruit_star_4, LogColorRole.STAR_4, LogSeverity.INFO),
    RECRUIT_STAR_5(R.string.log_level_recruit_star_5, LogColorRole.STAR_5, LogSeverity.INFO),
    RECRUIT_STAR_6(R.string.log_level_recruit_star_6, LogColorRole.STAR_6, LogSeverity.INFO),
    RECRUIT_ROBOT(R.string.log_level_recruit_robot, LogColorRole.ROBOT, LogSeverity.INFO),

    ROGUELIKE_SUCCESS(
        R.string.log_level_roguelike_success,
        LogColorRole.ROGUELIKE_SUCCESS,
        LogSeverity.INFO
    ),
    ROGUELIKE_COMBAT(
        R.string.log_level_roguelike_combat,
        LogColorRole.ROGUELIKE_COMBAT,
        LogSeverity.INFO
    ),
    ROGUELIKE_EMERGENCY(
        R.string.log_level_roguelike_emergency,
        LogColorRole.ROGUELIKE_EMERGENCY,
        LogSeverity.INFO
    ),
    ROGUELIKE_BOSS(
        R.string.log_level_roguelike_boss,
        LogColorRole.ROGUELIKE_BOSS,
        LogSeverity.INFO
    ),
    ROGUELIKE_ABANDON(
        R.string.log_level_roguelike_abandon,
        LogColorRole.ROGUELIKE_ABANDON,
        LogSeverity.INFO
    ),

    RARE(R.string.log_level_rare, LogColorRole.RARE, LogSeverity.INFO);

    companion object {
        /** 1..6 → 对应星级，超出范围回退到 [MESSAGE]。 */
        fun forRecruitStar(star: Int): LogLevel = when (star) {
            1 -> RECRUIT_STAR_1
            2 -> RECRUIT_STAR_2
            3 -> RECRUIT_STAR_3
            4 -> RECRUIT_STAR_4
            5 -> RECRUIT_STAR_5
            6 -> RECRUIT_STAR_6
            else -> MESSAGE
        }
    }
}
