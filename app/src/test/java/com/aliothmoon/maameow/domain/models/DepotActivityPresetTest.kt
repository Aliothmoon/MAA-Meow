package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.data.model.activity.ActivityStage
import com.aliothmoon.maameow.data.model.activity.StageActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** 上游 a7ee2c2bf7：库存保持「当前活动」预设 */
class DepotActivityPresetTest {

    @Test
    fun knownDropStages_becomePlansWithDefaultCount() {
        val group = activity("落叶逐火")
        val presets = listOpen(
            stage("ME-6", "30012", "act1", group),
            stage("ME-7", "30063", "act1", group),
            stage("ME-8", "活动商店兑换", "act1", group), // 文案型掉落
            stage("ME-1", null, "act1", group),
        )

        assertEquals(listOf("act1" to "落叶逐火"), presets.map { it.key to it.name })
        assertEquals(
            listOf(
                Triple("ME-6", "30012", DepotActivityPreset.DEFAULT_COUNT),
                Triple("ME-7", "30063", DepotActivityPreset.DEFAULT_COUNT),
            ),
            presets.single().plans.map { Triple(it.stage, it.dropId, it.dropCount) },
        )
        assertEquals(200, DepotActivityPreset.DEFAULT_COUNT)
    }

    @Test
    fun openWindow_isJudgedAtTheGivenMoment() {
        val presets = listOpen(
            stage("A-1", "30012", "pending", activity("未开始", start = NOW + 1, end = NOW + DAY)),
            stage("B-1", "30012", "started", activity("刚开始", start = NOW, end = NOW + DAY)),
            stage("C-1", "30012", "expired", activity("刚结束", start = NOW - DAY, end = NOW)),
        )

        assertEquals(listOf("started"), presets.map { it.key })
    }

    /** 组内后半段关卡另有尚未开放的关卡级窗口，仍随活动一起导入，运行时按未开放跳过 */
    @Test
    fun stageOverride_doesNotDropLaterStagesOfAnOpenActivity() {
        val group = activity("落叶逐火")
        val later = activity("落叶逐火·后篇", start = NOW + DAY, end = NOW + 2 * DAY)
        val presets = listOpen(
            stage("ME-6", "30012", "act1", group),
            stage("ME-9", "30063", "act1", group, stageActivity = later),
        )

        assertEquals(listOf("ME-6", "ME-9"), presets.single().plans.map { it.stage })
        // 名称取组级，不被首关之外的覆盖带偏
        assertEquals("落叶逐火", presets.single().name)
    }

    /** 组级已结束，即便某关的覆盖窗口还开着也不再列出 */
    @Test
    fun stageOverride_doesNotKeepAnEndedActivityListed() {
        val group = activity("已结束", start = NOW - 2 * DAY, end = NOW - DAY)
        val presets = listOpen(
            stage("ME-6", "30012", "act1", group, stageActivity = activity("延长关卡")),
        )

        assertEquals(emptyList<DepotActivityPreset>(), presets)
    }

    @Test
    fun activitiesWithoutUsableStagesOrGroupInfo_areNotListed() {
        val presets = listOpen(
            stage("A-1", "活动商店兑换", "textOnly", activity("只有文案掉落")),
            stage("B-1", "30012", "noGroupInfo", null, stageActivity = activity("只有关卡级")),
        )

        assertEquals(emptyList<DepotActivityPreset>(), presets)
    }

    @Test
    fun groupsByKeyInSourceOrder_andFallsBackToKeyForBlankName() {
        val presets = listOpen(
            stage("B-1", "30012", "actB", activity("")),
            stage("A-1", "30063", "actA", activity("活动 A")),
            stage("B-2", "30063", "actB", activity("")),
        )

        assertEquals(listOf("actB" to "actB", "actA" to "活动 A"), presets.map { it.key to it.name })
        assertEquals(listOf("B-1", "B-2"), presets.first().plans.map { it.stage })
    }

    private fun listOpen(vararg stages: ActivityStage) =
        DepotActivityPreset.listOpen(stages.toList(), KNOWN, NOW)

    private fun activity(
        name: String,
        start: Long = NOW - DAY,
        end: Long = NOW + DAY,
    ) = StageActivityInfo(name = name, tip = "", utcStartTime = start, utcExpireTime = end)

    /** 与 ActivityManager 解析一致：关卡级覆盖优先，否则落到组级 */
    private fun stage(
        code: String,
        drop: String?,
        key: String,
        groupActivity: StageActivityInfo?,
        stageActivity: StageActivityInfo? = null,
    ) = ActivityStage(
        display = code,
        value = code,
        drop = drop,
        activity = stageActivity ?: groupActivity,
        activityKey = key,
        groupActivity = groupActivity,
    )

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val DAY = 24 * 60 * 60 * 1000L
        val KNOWN = setOf("30012", "30063")
    }
}
