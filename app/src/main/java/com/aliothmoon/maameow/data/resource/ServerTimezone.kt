package com.aliothmoon.maameow.data.resource

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 鹰历的"一天"从服务器当地时间凌晨 04:00 开始，
 * 04:00 之前仍属于前一天。
 */
object ServerTimezone {

    private const val YJ_DAY_START_HOUR = 4L

    /**
     * 客户端类型 → UTC 偏移量（小时）
     * see WPF DateTimeExtension._clientTypeTimezone
     */
    private val CLIENT_TYPE_TIMEZONE = mapOf(
        "Official" to 8,
        "Bilibili" to 8,
        "txwy" to 8,
        "YoStarEN" to -7,
        "YoStarJP" to 9,
        "YoStarKR" to 9
    )

    /**
     * 获取客户端类型对应的服务器时区
     */
    fun getServerZone(clientType: String): ZoneId {
        val offsetHours = CLIENT_TYPE_TIMEZONE[clientType] ?: 8
        return ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(offsetHours))
    }

    /**
     * 当前时刻对应的鹰角历时间（服务器当地时间回退 4 小时）
     */
    private fun yjNow(clientType: String): ZonedDateTime =
        ZonedDateTime.now(getServerZone(clientType)).minusHours(YJ_DAY_START_HOUR)

    /**
     * 获取指定客户端类型的鹰角历星期几
     */
    fun getYjDayOfWeek(clientType: String): DayOfWeek = yjNow(clientType).dayOfWeek

    /**
     * 获取指定客户端类型的鹰角历日期
     * see WPF DateTimeExtension.ToYjDate
     */
    fun getYjDate(clientType: String): LocalDate = yjNow(clientType).toLocalDate()

    /**
     * 获取指定客户端类型的鹰角历星期几（中文名）
     */
    fun getYjDayOfWeekName(clientType: String): String {
        return when (getYjDayOfWeek(clientType)) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
    }
}
