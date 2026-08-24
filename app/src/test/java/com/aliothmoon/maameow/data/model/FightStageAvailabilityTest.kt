package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.utils.i18n.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 关卡可用性选关契约（对齐 WPF GetFightStage：未开放关卡不下发 Core）
 */
class FightStageAvailabilityTest {

    @Test
    fun blankPrimaryStage_meansCurLast_andPassesThrough() {
        assertEquals("", FightConfig(stage1 = "").getActiveStage(stagedActivityManager(emptySet())))
    }

    @Test
    fun openPrimaryStage_withoutAlternates_passesThrough() {
        assertEquals(
            "1-7",
            FightConfig(stage1 = "1-7").getActiveStage(stagedActivityManager(setOf("1-7"))),
        )
    }

    @Test
    fun closedPrimaryStage_withoutAlternates_returnsNull() {
        assertNull(FightConfig(stage1 = "UR-5").getActiveStage(stagedActivityManager(emptySet())))
    }

    @Test
    fun closedPrimaryStage_fallsBackToFirstOpenAlternate() {
        assertEquals(
            "1-7",
            FightConfig(
                stage1 = "UR-5",
                useAlternateStage = true,
                stageResetMode = StageResetMode.IGNORE,
                alternateStages = listOf("CE-6", "1-7"),
            ).getActiveStage(stagedActivityManager(setOf("1-7"))),
        )
    }

    @Test
    fun allCandidatesClosed_returnsNull_evenWithAlternates() {
        assertNull(
            FightConfig(
                stage1 = "UR-5",
                useAlternateStage = true,
                stageResetMode = StageResetMode.IGNORE,
                alternateStages = listOf("SL-8"),
            ).getActiveStage(stagedActivityManager(emptySet())),
        )
    }

    @Test
    fun expiredStageFilteredByMembership_fallsToOpenAlternate() {
        // CURRENT 模式按合并列表过滤成员，过期关卡 UR-5 被剔除后命中备选 1-7
        assertEquals(
            "1-7",
            FightConfig(
                stage1 = "UR-5",
                useAlternateStage = true,
                stageResetMode = StageResetMode.CURRENT,
                alternateStages = listOf("1-7"),
            ).getActiveStage(
                stagedActivityManager(setOf("1-7"), mergedStages = listOf("1-7"))
            ),
        )
    }

    @Test
    fun toTaskParams_skipsWithWarning_whenPrimaryClosed() {
        val log = CollectingPreflightLogSink()
        val params = FightConfig(stage1 = "UR-5").toTaskParams(
            testTaskParamContext(
                activityManager = stagedActivityManager(emptySet()),
                logSink = log,
            )
        )

        assertTrue(params.isEmpty())
        val entry = log.entries.single()
        assertEquals(LogLevel.WARNING, entry.second)
        assertEquals(
            R.string.runlog_fight_stage_unavailable,
            (entry.first as UiText.Resource).resId,
        )
    }

    @Test
    fun toTaskParams_skips_whenAllAlternatesClosed() {
        val params = FightConfig(
            stage1 = "UR-5",
            useAlternateStage = true,
            stageResetMode = StageResetMode.IGNORE,
            alternateStages = listOf("SL-8"),
        ).toTaskParams(testTaskParamContext(activityManager = stagedActivityManager(emptySet())))

        assertTrue(params.isEmpty())
    }

    @Test
    fun toTaskParams_emitsStage_whenOpen() {
        val params = FightConfig(stage1 = "1-7").toTaskParams(
            testTaskParamContext(activityManager = stagedActivityManager(setOf("1-7")))
        )

        assertEquals(1, params.size)
    }
}
