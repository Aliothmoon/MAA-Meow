package com.aliothmoon.maameow.data.model

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong


/**
 * 参考 MaaWPFGUI 的 LogItemViewModel。
 * `@Immutable` 是给 Compose 的稳定性提示——绕开 LocalDateTime / List 的默认推断不稳定。
 */
@Immutable
data class LogItem(
    val id: Long = idGenerator.incrementAndGet(),
    val time: LocalDateTime = LocalDateTime.now(),
    /** 持久化用：与 time 并存，避免渲染端反复转换 */
    val timestampMillis: Long = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    val content: String,
    val level: LogLevel = LogLevel.MESSAGE,
    val showTime: Boolean = true,
    /** 纯文本详情 */
    val tooltip: String? = null,
    /** 结构化富文本详情，UI 层按主题着色 */
    val recruitTooltip: List<RecruitCombination>? = null,
    val recruitTags: RecruitTagsLog? = null,
    val kind: LogKind = LogKind.NORMAL,
    /** 任务起止条目的任务名 */
    val task: String? = null,
) {
    companion object {
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val idGenerator = AtomicLong(System.currentTimeMillis())
    }

    val formattedTime: String get() = time.format(timeFormatter)

    val hasDetails: Boolean
        get() = tooltip != null || recruitTooltip != null
}

/** 运行日志按任务分组用的结构标记 */
enum class LogKind { NORMAL, TASK_START, TASK_DONE, TASK_FAILED, SUMMARY }
