package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.StageResetMode
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.stagedActivityManager
import com.aliothmoon.maameow.utils.i18n.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 信用作战可用性判定：关卡列表含「当前/上次」或无今日开放关卡时禁用
 */
class MallCreditFightAvailabilityTest {

    @Test
    fun available_whenFightStageResolvable() {
        val result = MallCreditFightAvailability.resolve(
            listOf(
                TaskChainNode(name = "理智作战", order = 0, enabled = true, config = FightConfig(stage1 = "1-7"))
            ),
            stagedActivityManager(setOf("1-7")),
        )

        assertTrue(result.isAvailable)
    }

    @Test
    fun blockedByBlankStage_whenCurLast() {
        val result = MallCreditFightAvailability.resolve(
            listOf(
                TaskChainNode(name = "理智作战", order = 0, enabled = true, config = FightConfig(stage1 = ""))
            ),
            stagedActivityManager(setOf("1-7")),
        )

        assertFalse(result.isAvailable)
        assertEquals(
            R.string.mall_credit_fight_blocked_by_stage,
            (result.message as UiText.Resource).resId,
        )
    }

    @Test
    fun blockedByClosedStage_whenNoStageOpen() {
        val result = MallCreditFightAvailability.resolve(
            listOf(
                TaskChainNode(name = "理智作战", order = 0, enabled = true, config = FightConfig(stage1 = "UR-5"))
            ),
            stagedActivityManager(emptySet()),
        )

        assertFalse(result.isAvailable)
        assertEquals(
            R.string.mall_credit_fight_blocked_by_closed_stage,
            (result.message as UiText.Resource).resId,
        )
    }

    @Test
    fun blockedByClosedStage_evenWithBlankAlternateSlot() {
        // 备选空槽已被 getActiveStage 过滤，不应把「全不开放」错报成「当前/上次」
        val result = MallCreditFightAvailability.resolve(
            listOf(
                TaskChainNode(
                    name = "理智作战",
                    order = 0,
                    enabled = true,
                    config = FightConfig(
                        stage1 = "UR-5",
                        useAlternateStage = true,
                        stageResetMode = StageResetMode.IGNORE,
                        alternateStages = listOf(""),
                    ),
                )
            ),
            stagedActivityManager(emptySet()),
        )

        assertFalse(result.isAvailable)
        assertEquals(
            R.string.mall_credit_fight_blocked_by_closed_stage,
            (result.message as UiText.Resource).resId,
        )
    }
}
