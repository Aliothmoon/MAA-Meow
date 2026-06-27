package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.model.activity.StageActivityInfo
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * [ActivityManager.isPermanentStage] 单测。
 *
 * 常驻判定 = `openDays 为空 && (activity == null || activity.isResourceCollection)`，
 * 迁移自 WPF FightSettingsUserControlModel.IsPermanentStage。覆盖：
 * 无周期/无活动、每日资源本、周期资源本、限时活动、当前·上次（空串）、字典外过期活动关卡。
 */
class ActivityManagerPermanentStageTest {

    private val resourceCollection = StageActivityInfo(
        name = "资源收集", tip = "", utcStartTime = 0L, utcExpireTime = 0L,
        isResourceCollection = true,
    )
    private val limitedActivity = StageActivityInfo(
        name = "限时活动", tip = "", utcStartTime = 0L, utcExpireTime = Long.MAX_VALUE,
        isResourceCollection = false,
    )

    private fun managerWith(stages: Map<String, MergedStageInfo>): ActivityManager {
        val manager = ActivityManager(
            context = mockk(relaxed = true),
            chainState = mockk(relaxed = true),
            maaApiService = mockk(relaxed = true),
            itemHelper = mockk(relaxed = true),
        )
        val field = ActivityManager::class.java.getDeclaredField("_stages").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(manager) as MutableStateFlow<Map<String, MergedStageInfo>>).value = stages
        return manager
    }

    private val manager = managerWith(
        mapOf(
            // 常驻主线：无周期限制、无活动
            "1-7" to MergedStageInfo(code = "1-7", displayName = "1-7"),
            // 每日资源本：无周期限制、活动为资源收集
            "LS-6" to MergedStageInfo(code = "LS-6", displayName = "LS-6", activity = resourceCollection),
            // 周期资源本：有周期限制（即便活动为资源收集，也非常驻）
            "CE-6" to MergedStageInfo(
                code = "CE-6", displayName = "CE-6",
                openDays = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                activity = resourceCollection,
            ),
            // 限时活动关卡：无周期限制，但活动为限时（非资源收集）
            "SW-9" to MergedStageInfo(code = "SW-9", displayName = "SW-9", activity = limitedActivity),
        )
    )

    @Test
    fun permanentMainlineStage_isPermanent() {
        assertTrue(manager.isPermanentStage("1-7"))
    }

    @Test
    fun dailyResourceStage_isPermanent() {
        assertTrue(manager.isPermanentStage("LS-6"))
    }

    @Test
    fun periodicResourceStage_isNotPermanent() {
        assertFalse(manager.isPermanentStage("CE-6"))
    }

    @Test
    fun limitedActivityStage_isNotPermanent() {
        assertFalse(manager.isPermanentStage("SW-9"))
    }

    @Test
    fun currentOrLastStage_emptyCode_isPermanent() {
        // 空串经 getStageInfo fallback 后无周期、无活动 → 视为常驻（当前/上次同样会阻断备选）
        assertTrue(manager.isPermanentStage(""))
    }

    @Test
    fun unknownExpiredActivityStage_isNotPermanent() {
        // 字典外、匹配 "XX-N" 的过期活动关卡 → getStageInfo 赋予一个非资源收集活动 → 非常驻
        assertFalse(manager.isPermanentStage("UR-5"))
    }
}
