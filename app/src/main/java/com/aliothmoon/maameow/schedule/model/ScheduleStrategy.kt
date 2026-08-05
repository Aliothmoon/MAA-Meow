package com.aliothmoon.maameow.schedule.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
enum class ScheduleType {
    /** 固定星期 + 时刻 */
    FIXED_TIME,
    /** 指定开始时间 + 间隔周期 */
    INTERVAL,
}

/** 将 DayOfWeek 序列化为 ISO 数值（1=周一 … 7=周日） */
object DayOfWeekSerializer : KSerializer<DayOfWeek> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DayOfWeek", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: DayOfWeek) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): DayOfWeek =
        DayOfWeek.of(decoder.decodeInt())
}

/** 将 LocalTime 序列化为 "HH:mm" 字符串 */
object LocalTimeSerializer : KSerializer<LocalTime> {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.format(formatter))
    }

    override fun deserialize(decoder: Decoder): LocalTime =
        LocalTime.parse(decoder.decodeString(), formatter)
}

@Serializable
data class ScheduleStrategy(
    val id: String = UUID.randomUUID().toString(),
    /** 策略名称 */
    val name: String,
    val enabled: Boolean = true,
    /** 调度类型，默认固定时间（向后兼容） */
    val scheduleType: ScheduleType = ScheduleType.FIXED_TIME,
    /** [FIXED_TIME] 启用的星期，使用 ISO 值序列化 */
    val daysOfWeek: Set<@Serializable(with = DayOfWeekSerializer::class) DayOfWeek> = emptySet(),
    /** [FIXED_TIME] 每日触发时刻列表，已排序，格式 "HH:mm" */
    val executionTimes: List<@Serializable(with = LocalTimeSerializer::class) LocalTime> = emptyList(),
    /** [INTERVAL] 首次执行的绝对时间（epoch ms） */
    val startTimeMs: Long? = null,
    /** [INTERVAL] 执行间隔（分钟） */
    val intervalMinutes: Int? = null,
    /** 关联的任务配置 Profile ID */
    val profileId: String,
    /** 触发时若有任务运行，强制停止后再启动 */
    val forceStart: Boolean = false,
    /** 触发时先唤醒+解锁屏幕（需在设置页配置解锁方式） */
    val wakeUnlockEnabled: Boolean = false,
    /** 任务结束后自动熄屏 */
    val autoSleepAfterTask: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastExecutedAt: Long? = null,
    val lastResult: ExecutionResult? = null,
    val lastResultMessage: String? = null,
)
