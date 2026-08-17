package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.domain.enums.RoguelikeBlackFlowCultivationTarget
import com.aliothmoon.maameow.domain.enums.RoguelikeMode
import com.aliothmoon.maameow.domain.enums.UiUsageConstants.Roguelike as RoguelikeUi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 黑流树海（BlackFlow）主题的参数与选项契约，对齐上游 v6.17.0-beta.2
 *
 * 其余模式不下发 blackflow_strategy —— core 会按 mode + investment_enabled 自行推导
 */
class RoguelikeBlackFlowParamsTest {

    private fun params(config: RoguelikeConfig) =
        Json.parseToJsonElement(
            config.toTaskParams(testTaskParamContext()).single().params
        ).jsonObject

    @Test
    fun babyAnimalMode_sendsStrategyAndTarget() {
        val json = params(
            RoguelikeConfig(
                theme = THEME,
                mode = RoguelikeMode.BlackFlowBabyAnimal,
                blackFlowCultivationTarget = RoguelikeBlackFlowCultivationTarget.Cerberus,
            )
        )
        assertEquals("baby_animal", json["blackflow_strategy"]!!.jsonPrimitive.content)
        assertEquals(
            "swaddled_cerberus",
            json["blackflow_cultivation_target"]!!.jsonPrimitive.content,
        )
        assertEquals(30001, json["mode"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun otherModes_omitStrategy() {
        val json = params(RoguelikeConfig(theme = THEME, mode = RoguelikeMode.Exp))
        assertNull(json["blackflow_strategy"])
        assertNull(json["blackflow_cultivation_target"])
    }

    @Test
    fun babyAnimalMode_onOtherTheme_omitsStrategy() {
        // 主题与模式都对上才发，对齐 WPF AsstRoguelikeTask
        val json = params(
            RoguelikeConfig(theme = "JieGarden", mode = RoguelikeMode.BlackFlowBabyAnimal)
        )
        assertNull(json["blackflow_strategy"])
        assertNull(json["blackflow_cultivation_target"])
    }

    @Test
    fun investmentWithMoreScore_isDisabledForBlackFlow() {
        val json = params(
            RoguelikeConfig(
                theme = THEME,
                mode = RoguelikeMode.Investment,
                investmentEnabled = true,
                investmentWithMoreScore = true,
            )
        )
        assertFalse(json["investment_with_more_score"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun investmentWithMoreScore_stillWorksForOtherThemes() {
        val json = params(
            RoguelikeConfig(
                theme = "Sarkaz",
                mode = RoguelikeMode.Investment,
                investmentEnabled = true,
                investmentWithMoreScore = true,
            )
        )
        assertTrue(json["investment_with_more_score"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun modeList_isRestrictedToThreeStrategies() {
        assertEquals(
            listOf("Exp", "Investment", "BlackFlowBabyAnimal"),
            RoguelikeUi.getModeKeysForTheme(THEME),
        )
        assertFalse(RoguelikeUi.isModeValidForTheme(RoguelikeMode.Collectible, THEME))
        assertTrue(RoguelikeUi.isModeValidForTheme(RoguelikeMode.BlackFlowBabyAnimal, THEME))
    }

    @Test
    fun babyAnimalMode_isRejectedByOtherThemes() {
        assertFalse(RoguelikeUi.isModeValidForTheme(RoguelikeMode.BlackFlowBabyAnimal, "JieGarden"))
    }

    @Test
    fun squadList_dropsFirstClassAndKeepsIs6Squads() {
        val squads = RoguelikeUi.getSquadOptionsForTheme(THEME)
        assertFalse(squads.toString(), "高规格分队" in squads)
        assertTrue(squads.containsAll(listOf("本源研修分队", "地质调查分队", "特勤分队")))
        // 其他主题不受影响
        assertTrue("高规格分队" in RoguelikeUi.getSquadOptionsForTheme("JieGarden"))
    }

    @Test
    fun firstClassSquad_isWhitelisted_notBlacklisted() {
        // 白名单：将来新主题默认拿不到高规格分队，与上游一致
        assertFalse("高规格分队" in RoguelikeUi.getSquadOptionsForTheme("SomeFutureTheme"))
    }

    @Test
    fun roleList_matchesJieGarden() {
        assertEquals(
            RoguelikeUi.getRoleKeysForTheme("JieGarden"),
            RoguelikeUi.getRoleKeysForTheme(THEME),
        )
    }

    @Test
    fun maxDifficulty_is15() {
        assertEquals(15, RoguelikeUi.getMaxDifficultyForTheme(THEME))
    }

    private companion object {
        const val THEME = "BlackFlow"
    }
}
