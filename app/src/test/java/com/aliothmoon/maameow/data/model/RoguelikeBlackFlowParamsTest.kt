package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.enums.RoguelikeBlackFlowStartAnimal
import com.aliothmoon.maameow.domain.enums.RoguelikeBlackFlowStartReward
import com.aliothmoon.maameow.domain.enums.RoguelikeMode
import com.aliothmoon.maameow.domain.enums.UiUsageConstants.Roguelike as RoguelikeUi
import com.aliothmoon.maameow.utils.JsonUtils
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 黑流树海（BlackFlow）主题的参数与选项契约，对齐上游 v6.21.0-beta.1 AsstRoguelikeTask.Serialize
 */
class RoguelikeBlackFlowParamsTest {

    private val resourceDataManager = mockk<ResourceDataManager>(relaxed = true) {
        every { normalizeCharacterName(any()) } answers { firstArg() }
    }

    private fun params(config: RoguelikeConfig) =
        Json.parseToJsonElement(
            config.toTaskParams(testTaskParamContext(resourceDataManager = resourceDataManager))
                .single().params
        ).jsonObject

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content

    private fun JsonObject.coreChars() =
        this["core_char_list"]!!.jsonArray.map { it.jsonObject }

    private val userStart = RoguelikeConfig(
        theme = THEME,
        squad = "地质调查分队",
        roles = RoguelikeUi.ROLE_FIRST_MOVE_ADVANTAGE,
        startingOpers = listOf(RoguelikeStartingOper("山", useSupport = true)),
        collectibleModeSquad = "开拓者分队",
    )

    @Test
    fun exp_withoutFlight_sendsTargetEnding_andKeepsUserStart() {
        val json = params(userStart.copy(mode = RoguelikeMode.Exp))
        assertEquals("ending1", json.str("blackflow_strategy"))
        assertEquals("地质调查分队", json.str("squad"))
        assertEquals(RoguelikeUi.ROLE_FIRST_MOVE_ADVANTAGE, json.str("roles"))
        assertEquals("山", json.coreChars().single().getValue("name").jsonPrimitive.content)
    }

    @Test
    fun exp_withFlight_omitsStrategy_andUsesMechanicStart() {
        val json = params(userStart.copy(mode = RoguelikeMode.Exp, blackFlowFastFlight = true))
        assertNull(json["blackflow_strategy"])
        assertEquals("特勤分队", json.str("squad"))
        assertEquals("稳扎稳打", json.str("roles"))
        val oper = json.coreChars().single()
        assertEquals("机械师", oper.getValue("name").jsonPrimitive.content)
        assertFalse(oper.getValue("use_support").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun exp_onOtherTheme_ignoresBlackFlowOptions() {
        val json = params(
            userStart.copy(theme = "JieGarden", mode = RoguelikeMode.Exp, blackFlowFastFlight = true)
        )
        assertNull(json["blackflow_strategy"])
        assertEquals("地质调查分队", json.str("squad"))
        assertEquals("山", json.coreChars().single().getValue("name").jsonPrimitive.content)
    }

    @Test
    fun startRun_sendsAnimalsRewardsAndPrepSquad() {
        val json = params(
            userStart.copy(
                mode = RoguelikeMode.BlackFlowStartRun,
                blackFlowStartAnimal1 = RoguelikeBlackFlowStartAnimal.Cat,
                blackFlowStartAnimal2 = RoguelikeBlackFlowStartAnimal.FeatheredSerpent,
                blackFlowStartRewards = setOf(
                    RoguelikeBlackFlowStartReward.SpaceLease,
                    RoguelikeBlackFlowStartReward.GoldenCrow,
                ),
            )
        )
        assertEquals(30003, json.str("mode").toInt())
        assertEquals(
            listOf("swaddled_cat", "swaddled_feathered_serpent"),
            json["blackflow_start_animals"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(4 or 2048, json.str("blackflow_start_rewards").toInt())
        assertEquals("开拓者分队", json.str("collectible_mode_squad"))
        // 目标局用常规分队，职业组与开局干员只作用于准备局
        assertEquals("地质调查分队", json.str("squad"))
        assertEquals("山", json.coreChars().single().getValue("name").jsonPrimitive.content)
    }

    @Test
    fun startRun_withoutSerpent_dropsSerpentRewards() {
        val json = params(
            userStart.copy(
                mode = RoguelikeMode.BlackFlowStartRun,
                blackFlowStartAnimal1 = RoguelikeBlackFlowStartAnimal.Dog,
                blackFlowStartRewards = setOf(
                    RoguelikeBlackFlowStartReward.UnnumberedItem,
                    RoguelikeBlackFlowStartReward.Pegasus,
                ),
            )
        )
        assertEquals(1, json.str("blackflow_start_rewards").toInt())
        assertEquals(
            listOf("swaddled_dog"),
            json["blackflow_start_animals"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun startRun_alwaysSendsRequiredFields() {
        // core 在模式 30003 下缺任一字段都会拒绝任务
        val json = params(userStart.copy(mode = RoguelikeMode.BlackFlowStartRun))
        assertTrue(json["blackflow_start_animals"]!!.jsonArray.isEmpty())
        assertEquals(0, json.str("blackflow_start_rewards").toInt())
    }

    @Test
    fun startRun_withFlight_sendsPrepStartSeparately_andKeepsTargetStart() {
        val json = params(
            userStart.copy(mode = RoguelikeMode.BlackFlowStartRun, blackFlowStartRunFastFlight = true)
        )
        assertEquals("特勤分队", json.str("collectible_mode_squad"))
        assertEquals("稳扎稳打", json.str("collectible_mode_roles"))
        val prepOper = json["collectible_mode_core_char_list"]!!.jsonArray.single().jsonObject
        assertEquals("机械师", prepOper.getValue("name").jsonPrimitive.content)
        // 目标局凹实托邦时要按常规设置开局
        assertEquals("地质调查分队", json.str("squad"))
        assertEquals(RoguelikeUi.ROLE_FIRST_MOVE_ADVANTAGE, json.str("roles"))
        assertEquals("山", json.coreChars().single().getValue("name").jsonPrimitive.content)
        assertNull(json["blackflow_strategy"])
    }

    @Test
    fun startRun_withoutFlight_omitsPrepStart() {
        val json = params(userStart.copy(mode = RoguelikeMode.BlackFlowStartRun))
        assertNull(json["collectible_mode_roles"])
        assertNull(json["collectible_mode_core_char_list"])
    }

    @Test
    fun startRun_utopia_sendsFullPriorityIds() {
        val priority = listOf(
            RoguelikeBlackFlowStartReward.GoldenCrow,
            RoguelikeBlackFlowStartReward.SurveyAdvance,
        )
        val json = params(
            userStart.copy(
                mode = RoguelikeMode.BlackFlowStartRun,
                difficulty = 8,
                blackFlowStartUtopia = true,
                blackFlowStartRewardPriority = priority,
            )
        )
        assertTrue(json["blackflow_start_utopia"]!!.jsonPrimitive.content.toBoolean())
        val ids = json["blackflow_start_reward_priority"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(12, ids.size)
        assertEquals(listOf("golden_crow", "survey_advance", "pegasus"), ids.take(3))
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun startRun_utopia_needsDifficultySixOrCurrent() {
        fun utopiaOf(difficulty: Int) = params(
            userStart.copy(
                mode = RoguelikeMode.BlackFlowStartRun,
                difficulty = difficulty,
                blackFlowStartUtopia = true,
            )
        )["blackflow_start_utopia"]

        assertNull(utopiaOf(5))
        assertTrue(utopiaOf(6) != null)
        assertTrue(utopiaOf(-1) != null)
        assertTrue(utopiaOf(Int.MAX_VALUE) != null)
    }

    @Test
    fun utopia_onOtherMode_isIgnored() {
        val json = params(userStart.copy(mode = RoguelikeMode.Exp, blackFlowStartUtopia = true))
        assertNull(json["blackflow_start_utopia"])
        assertNull(json["blackflow_start_reward_priority"])
    }

    @Test
    fun rewardPriority_isNormalizedOnMigrate() {
        val migrated = userStart.copy(
            blackFlowStartRewardPriority = listOf(
                RoguelikeBlackFlowStartReward.Hydra,
                RoguelikeBlackFlowStartReward.Hydra,
            )
        ).migrate()
        val priority = migrated.blackFlowStartRewardPriority
        assertEquals(RoguelikeBlackFlowStartReward.Hydra, priority.first())
        assertEquals(RoguelikeBlackFlowStartReward.entries.toSet(), priority.toSet())
        assertEquals(12, priority.size)
    }

    @Test
    fun legacyBabyAnimal_isMigratedToStartRun_andLegacyFieldIsDropped() {
        val legacy = JsonUtils.common.decodeFromString<RoguelikeConfig>(
            """{"theme":"BlackFlow","mode":"BlackFlowBabyAnimal","blackFlowCultivationTarget":"Dog"}"""
        )
        val migrated = legacy.migrate()
        assertEquals(RoguelikeMode.BlackFlowStartRun, migrated.mode)
        assertEquals(RoguelikeBlackFlowStartAnimal.Dog, migrated.blackFlowStartAnimal1)
        assertEquals(RoguelikeBlackFlowStartAnimal.None, migrated.blackFlowStartAnimal2)
        assertFalse(
            "blackFlowCultivationTarget" in JsonUtils.common.encodeToString(migrated)
        )
    }

    @Test
    fun legacyCultivationTarget_onOtherMode_isOnlyDropped() {
        val migrated = JsonUtils.common.decodeFromString<RoguelikeConfig>(
            """{"theme":"BlackFlow","mode":"Investment","blackFlowCultivationTarget":"Dog"}"""
        ).migrate()
        assertEquals(RoguelikeMode.Investment, migrated.mode)
        assertEquals(RoguelikeBlackFlowStartAnimal.None, migrated.blackFlowStartAnimal1)
        assertNull(migrated.legacyBlackFlowCultivationTarget)
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
        assertFalse(json.str("investment_with_more_score").toBoolean())
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
        assertTrue(json.str("investment_with_more_score").toBoolean())
    }

    @Test
    fun modeList_isRestrictedToThreeStrategies() {
        assertEquals(
            listOf("Exp", "Investment", "BlackFlowStartRun"),
            RoguelikeUi.getModeKeysForTheme(THEME),
        )
        assertFalse(RoguelikeUi.isModeValidForTheme(RoguelikeMode.Collectible, THEME))
        assertFalse(RoguelikeUi.isModeValidForTheme(RoguelikeMode.BlackFlowBabyAnimal, THEME))
        assertFalse(RoguelikeUi.isModeValidForTheme(RoguelikeMode.BlackFlowStartRun, "JieGarden"))
    }

    @Test
    fun squadList_dropsFirstClassAndKeepsIs6Squads() {
        val squads = RoguelikeUi.getSquadOptionsForTheme(THEME)
        assertFalse(squads.toString(), "高规格分队" in squads)
        assertTrue(
            squads.containsAll(
                listOf("本源研修分队", "地质调查分队", "团结阵线分队", "踏足遍历分队", "特勤分队")
            )
        )
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
    fun maxDifficulty_is16() {
        assertEquals(16, RoguelikeUi.getMaxDifficultyForTheme(THEME))
    }

    private companion object {
        const val THEME = "BlackFlow"
    }
}
