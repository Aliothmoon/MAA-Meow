package com.aliothmoon.maameow.data.model.activity

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 活动信息（UI 使用）
 * 迁移自 WPF StageActivityInfo
 */
data class StageActivityInfo(
    val name: String,              // 活动名称
    val tip: String,               // 活动提示
    val utcStartTime: Long,        // UTC 开始时间（毫秒）
    val utcExpireTime: Long,       // UTC 结束时间（毫秒）
    val isResourceCollection: Boolean = false  // 是否为资源收集活动
) {
    /**
     * 活动是否正在进行中
     */
    val isOpen: Boolean
        get() {
            val now = System.currentTimeMillis()
            return now in utcStartTime until utcExpireTime
        }

    /**
     * 活动是否已过期
     */
    val isExpired: Boolean
        get() = System.currentTimeMillis() >= utcExpireTime

    /**
     * 活动是否尚未开始
     */
    val isPending: Boolean
        get() = System.currentTimeMillis() < utcStartTime

    /**
     * 获取剩余天数文本
     * 迁移自 WPF StageManager.GetDaysLeftText
     */
    fun getDaysLeftText(): String {
        val now = System.currentTimeMillis()
        val daysLeft = (utcExpireTime - now) / (24 * 60 * 60 * 1000)
        return if (daysLeft > 0) "${daysLeft}天" else "不足1天"
    }

    /**
     * 获取剩余天数（整数）
     */
    fun getDaysLeft(): Long {
        val now = System.currentTimeMillis()
        return (utcExpireTime - now) / (24 * 60 * 60 * 1000)
    }

    companion object {
        // 时间格式: "yyyy/MM/dd HH:mm:ss"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")

        /**
         * 解析时间字符串为 UTC 毫秒
         * @param dateStr 时间字符串 (格式: "yyyy/MM/dd HH:mm:ss")
         * @param timeZone 时区偏移（小时）
         * @return UTC 毫秒时间戳
         */
        fun parseToUtcMillis(dateStr: String?, timeZone: Int): Long {
            if (dateStr.isNullOrBlank()) return 0L
            return try {
                val localDateTime = LocalDateTime.parse(dateStr, DATE_FORMAT)
                // 将本地时间转换为 UTC：减去时区偏移
                val offset = ZoneOffset.ofHours(timeZone)
                localDateTime.toInstant(offset).toEpochMilli()
            } catch (e: Exception) {
                0L
            }
        }

        /**
         * 从 ActivityInfo 创建
         */
        fun fromActivityInfo(name: String, info: ActivityInfo): StageActivityInfo {
            return StageActivityInfo(
                name = info.stageName ?: name,
                tip = info.tip ?: "",
                utcStartTime = parseToUtcMillis(info.utcStartTime, info.timeZone),
                utcExpireTime = parseToUtcMillis(info.utcExpireTime, info.timeZone),
                isResourceCollection = false
            )
        }


        /**
         * 从 ResourceCollectionInfo 创建
         */
        fun fromResourceCollection(info: ResourceCollectionInfo): StageActivityInfo {
            return StageActivityInfo(
                name = "资源收集",
                tip = info.tip ?: "",
                utcStartTime = parseToUtcMillis(info.utcStartTime, info.timeZone),
                utcExpireTime = parseToUtcMillis(info.utcExpireTime, info.timeZone),
                isResourceCollection = true
            )
        }
    }
}
