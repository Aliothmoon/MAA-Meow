package com.aliothmoon.maameow.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TaskProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val chain: List<TaskChainNode>,
    /** 单独设置的运行时长上限（分钟），null 跟随全局设置 */
    val runDurationLimitMinutes: Int? = null,
)
