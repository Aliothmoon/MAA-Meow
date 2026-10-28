package com.aliothmoon.maameow.domain.service

/**
 * 读取已录制的解锁手势
 * 存储实现落在 data 层，这里只留接口，免得 domain 反向依赖 data
 */
interface UnlockGestureReader {

    /** 跨进程只传 JSON，未录制时为空串 */
    suspend fun readJson(): String

    /** 最近一次录制落盘的时间；未录制或读不到为 null */
    suspend fun recordedAtMs(): Long?
}
