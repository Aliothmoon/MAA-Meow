package com.aliothmoon.maameow.domain.service

import androidx.annotation.StringRes
import com.aliothmoon.maameow.R

/** 进程退出原文不报遥测，仅游戏运行中消失时给当前轮次附带分类 */
interface ProcessExitJournal {

    /** 连上服务后补记各游戏包上次之后的退出，App 自己也被杀的那几次靠这里 */
    suspend fun recordGames()

    /** 任务中游戏没了，当场补记，不等下次连接；返回给用户看的死因，查不到或是 MaaMeow 自己停的为 null */
    suspend fun recordGame(packageName: String): GameExitCause?
}

data class GameExitCause(val kind: Kind, val timestamp: Long, val tag: String) {
    enum class Kind(@param:StringRes val messageRes: Int) {
        LOW_MEMORY(R.string.runlog_game_exit_low_memory),
        BACKGROUND_KILL(R.string.runlog_game_exit_background_kill),
        FORCE_STOPPED(R.string.runlog_game_exit_force_stopped),
        CRASH(R.string.runlog_game_exit_crash),
        EXIT_SELF(R.string.runlog_game_exit_self),
        PACKAGE_CHANGE(R.string.runlog_game_exit_package_change),
        OTHER(R.string.runlog_game_exit_other),
    }
}
