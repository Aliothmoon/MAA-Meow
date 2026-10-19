package com.aliothmoon.maameow.data.model.update

/**
 * 下载/安装过程状态
 */
sealed class UpdateProcessState {
    data object Idle : UpdateProcessState()
    data class Downloading(
        val progress: Int,
        val speed: String,
        val downloaded: Long,
        val total: Long
    ) : UpdateProcessState()

    data class Extracting(
        val progress: Int,
        val current: Int,
        val total: Int
    ) : UpdateProcessState()

    /** 已下载，等任务结束再解压 */
    data object Deferred : UpdateProcessState()

    data object Installing : UpdateProcessState()
    data object Success : UpdateProcessState()
    data class Failed(val error: UpdateError) : UpdateProcessState()

    val isInProgress: Boolean
        get() = this is Downloading || this is Deferred || this is Extracting || this is Installing
}