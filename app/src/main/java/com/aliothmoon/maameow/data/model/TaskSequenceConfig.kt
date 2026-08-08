package com.aliothmoon.maameow.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 一套任务链配置：有独立名称，内含有序的 [ProfileSequenceEntry] 列表。
 * 与用户 [TaskProfile] 配置无关；同一用户配置可在链中重复引用。
 */
@Serializable
data class TaskSequenceConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val entries: List<ProfileSequenceEntry> = emptyList(),
)