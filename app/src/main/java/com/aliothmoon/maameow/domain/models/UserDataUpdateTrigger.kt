package com.aliothmoon.maameow.domain.models

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlinx.serialization.Serializable

/**
 * 「更新数据」任务的触发间隔。
 * 迁移自 WPF UserDataUpdateTriggerInterval。
 */
@Serializable
enum class UserDataUpdateTriggerInterval {
    /** 每次任务链执行到本节点都同步 */
    EVERY_TIME,

    /** 鹰历日期变化后才同步 */
    DAILY,

    /** 鹰历 ISO 周变化后才同步 */
    WEEKLY,
}

/**
 * 判断某侧（干员箱 / 仓库）在给定间隔下是否到期。
 *
 * 对齐上游 IsTriggerDue：比较的是「上次同步时刻」与「现在」在同一套日历上的日/周。
 * 调用方应传入 [yjToday] = [com.aliothmoon.maameow.data.resource.ServerTimezone.getYjDate]，
 * 以及 [yjZone] = 对应客户端的服务器时区，使 lastSync 也映射到同一时区的日历日
 * （上游对 last 做 ToYjDateTime）。
 *
 * @param lastSyncMillis 上次完整识别成功时间（epoch ms）；≤0 表示从未同步
 */
fun isUserDataUpdateDue(
    lastSyncMillis: Long,
    interval: UserDataUpdateTriggerInterval,
    yjToday: LocalDate,
    yjZone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    if (interval == UserDataUpdateTriggerInterval.EVERY_TIME) return true
    if (lastSyncMillis <= 0L) return true

    // 与 getYjDate 一致：服务器当地时间回退 4 小时得到鹰历日
    val lastYjDate = Instant.ofEpochMilli(lastSyncMillis)
        .atZone(yjZone)
        .minusHours(4)
        .toLocalDate()

    return when (interval) {
        UserDataUpdateTriggerInterval.DAILY -> yjToday.isAfter(lastYjDate)
        UserDataUpdateTriggerInterval.WEEKLY -> {
            val lastYear = lastYjDate.get(IsoFields.WEEK_BASED_YEAR)
            val lastWeek = lastYjDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val nowYear = yjToday.get(IsoFields.WEEK_BASED_YEAR)
            val nowWeek = yjToday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            nowYear != lastYear || nowWeek != lastWeek
        }
    }
}
