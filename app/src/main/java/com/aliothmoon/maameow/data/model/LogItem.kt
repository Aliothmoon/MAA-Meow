package com.aliothmoon.maameow.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong


/**
 * 日志条目
 * 参考 MaaWPFGUI 的 LogItemViewModel
 */
data class LogItem(
    /** 唯一标识 */
    val id: Long = idGenerator.incrementAndGet(),
    /** 日志时间 */
    val time: LocalDateTime = LocalDateTime.now(),
    /** 持久化使用的时间戳 */
    val timestampMillis: Long = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    /** 日志内容 */
    val content: String,
    /** 日志级别 */
    val level: LogLevel = LogLevel.MESSAGE,
    /** 是否显示时间 */
    val showTime: Boolean = true,
    /** 工具提示（点击查看详情） */
    val tooltip: String? = null,
    /** 富文本工具提示（带颜色标注，优先于 tooltip 展示） */
    val annotatedTooltip: AnnotatedString? = null,
    /** 附加截图路径（用于错误截图） */
    val screenshotPath: String? = null
    // TODO: 实现截图缩略图支持（错误时截图）
) {
    companion object {
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        /** 全局唯一 ID 生成器 */
        private val idGenerator = AtomicLong(System.currentTimeMillis())
    }

    /** 格式化的时间字符串 */
    val formattedTime: String
        get() = time.format(timeFormatter)

    /** 日志颜色 */
    val color: Color
        get() = level.color

    /** 是否有详情可查看 */
    val hasDetails: Boolean
        get() = tooltip != null || annotatedTooltip != null || screenshotPath != null
}
