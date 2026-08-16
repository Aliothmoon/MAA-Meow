package com.aliothmoon.maameow.domain.models

import kotlinx.serialization.Serializable

enum class GestureRecordStatus {
    /** 从未录制，或结果已被取走 */
    IDLE,

    /** 已锁屏，等待用户解锁 */
    RECORDING,

    DONE,
    FAILED,
    ;

    /** 有结果可交；IDLE 只说明远端此刻没东西给，不代表本次录制结束了 */
    val isTerminal: Boolean get() = this == DONE || this == FAILED
}

/** 提权进程 → App 的录制状态快照，走 JSON 字符串跨进程 */
@Serializable
data class GestureRecordResult(
    val status: GestureRecordStatus,
    val gesture: UnlockGesture? = null,
    /** FAILED 时为 [com.aliothmoon.maameow.constant.WakeUnlockResult] 中的码 */
    val errorCode: Int = 0,
) {
    companion object {
        val IDLE = GestureRecordResult(GestureRecordStatus.IDLE)
        val RECORDING = GestureRecordResult(GestureRecordStatus.RECORDING)

        fun failed(errorCode: Int) =
            GestureRecordResult(GestureRecordStatus.FAILED, errorCode = errorCode)

        fun done(gesture: UnlockGesture) =
            GestureRecordResult(GestureRecordStatus.DONE, gesture = gesture)
    }
}
