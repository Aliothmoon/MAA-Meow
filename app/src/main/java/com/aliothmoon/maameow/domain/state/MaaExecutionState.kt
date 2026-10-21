package com.aliothmoon.maameow.domain.state

enum class MaaExecutionState {
    IDLE,      // 空闲
    STARTING,  // 启动中
    RUNNING,   // 运行中
    STOPPING,  // 停止中
    ERROR      // 错误
}

val MaaExecutionState.isIdle: Boolean
    get() = this == MaaExecutionState.IDLE || this == MaaExecutionState.ERROR
