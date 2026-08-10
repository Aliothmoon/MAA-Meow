package com.aliothmoon.maameow.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 任务链中的一项：引用某个 [TaskProfile]，允许同一 profile 重复出现。
 * [id] 为序列条目自身 ID（用于列表 key / 删除 / 排序），[profileId] 为被引用的配置。
 */
@Serializable
data class ProfileSequenceEntry(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
)
