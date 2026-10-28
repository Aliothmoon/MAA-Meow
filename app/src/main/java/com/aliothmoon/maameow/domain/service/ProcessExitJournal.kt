package com.aliothmoon.maameow.domain.service

/** 进程死因只落本地日志，不进遥测；App 自身在启动时记，游戏经提权服务查 */
interface ProcessExitJournal {

    /** 连上服务后补记各游戏包上次之后的退出，App 自己也被杀的那几次靠这里 */
    suspend fun recordGames()

    /** 任务中游戏没了，当场补记，不等下次连接 */
    suspend fun recordGame(packageName: String)
}
