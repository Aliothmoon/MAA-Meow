package com.aliothmoon.maameow.schedule.service

/**
 * 自启动引导的纯决策逻辑，与 Android Intent 解析解耦，便于单测
 */
sealed interface AutoStartTarget {
    /** [id] 对应 [AutoStartHelper.OemEntry.id] */
    data class Oem(val id: String) : AutoStartTarget

    /** 兜底：任何 ROM 都能打开 */
    data object AppDetails : AutoStartTarget
}

object AutoStartResolution {

    /** 厂商页可解析则跳厂商页，受限厂商解析不到退应用详情页，其余不引导 */
    fun select(
        resolvableOemIds: List<String>,
        knownRestrictiveManufacturer: Boolean,
    ): AutoStartTarget? = when {
        resolvableOemIds.isNotEmpty() -> AutoStartTarget.Oem(resolvableOemIds.first())
        knownRestrictiveManufacturer -> AutoStartTarget.AppDetails
        else -> null
    }

    /** token 每次开机变化，与上次提醒所在周期不同才提醒 */
    fun shouldRemindByBootId(currentBootId: String?, lastRemindedBootId: String?): Boolean =
        currentBootId != null && currentBootId != lastRemindedBootId

    /**
     * 读不到 BOOT_COUNT 时的兜底：uptime 单调递增，回落即新周期
     *
     * 重启后隔得比上次提醒时更久才打开会漏一轮 —— 宁可漏也不重复打扰
     */
    fun shouldRemindByUptime(currentUptimeMs: Long, lastRemindedUptimeMs: Long?): Boolean =
        lastRemindedUptimeMs == null || currentUptimeMs < lastRemindedUptimeMs

    /** 统一入口；不把「读不到 token」当成「本轮已提醒过」 */
    fun shouldRemind(
        neverRemind: Boolean,
        currentBootToken: String?,
        lastRemindedBootToken: String?,
        currentUptimeMs: Long,
        lastRemindedUptimeMs: Long?,
    ): Boolean = when {
        neverRemind -> false
        currentBootToken != null -> shouldRemindByBootId(currentBootToken, lastRemindedBootToken)
        else -> shouldRemindByUptime(currentUptimeMs, lastRemindedUptimeMs)
    }
}
