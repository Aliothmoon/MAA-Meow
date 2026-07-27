package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.maa.task.MaaTaskType
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TaskParamProvider] 的契约：外部输入统一由 [TaskParamContext] 注入，
 * 每个非容器型配置恰好展开为一个属于自己类型的 MaaCore 任务。
 */
class TaskParamProviderContractTest {

    private val ctx = testTaskParamContext(
        activityManager = alwaysOpenActivityManager(),
    )

    @Test
    fun everyNonContainerConfig_expandsToExactlyOneTaskOfItsOwnType() {
        val configs: List<Pair<TaskParamProvider, MaaTaskType>> = listOf(
            WakeUpConfig() to MaaTaskType.START_UP,
            RecruitConfig() to MaaTaskType.RECRUIT,
            InfrastConfig() to MaaTaskType.INFRAST,
            FightConfig(stage1 = "1-7") to MaaTaskType.FIGHT,
            MallConfig() to MaaTaskType.MALL,
            AwardConfig() to MaaTaskType.AWARD,
            RoguelikeConfig() to MaaTaskType.ROGUELIKE,
            ReclamationConfig() to MaaTaskType.RECLAMATION,
        )

        configs.forEach { (config, expectedType) ->
            val result = config.toTaskParams(ctx)
            assertEquals("${config::class.simpleName} 应恰好产出 1 个任务", 1, result.params.size)
            assertEquals(expectedType, result.params.single().type)
            assertTrue(result.logs.isEmpty())
        }
    }

    @Test
    fun mallConfig_requiresBothOwnSwitchAndChainPermission() {
        // 链级前提成立但本任务没开 → 不下发
        assertFalse(
            creditFightOf(MallConfig(creditFight = false), ctx.copy(chainAllowsCreditFight = true))
        )
        // 本任务开了但链级前提不成立（有作战任务关卡为「当前/上次」）→ 不下发
        assertFalse(
            creditFightOf(MallConfig(creditFight = true), ctx.copy(chainAllowsCreditFight = false))
        )
        // 两者皆成立才下发
        assertTrue(
            creditFightOf(MallConfig(creditFight = true), ctx.copy(chainAllowsCreditFight = true))
        )
    }

    @Test
    fun mallConfig_mergesFixedBlacklistByClientType() {
        val blacklist = jsonOf(MallConfig(), ctx.copy(clientType = "YoStarEN"))["blacklist"]!!
            .jsonArray.map { it.jsonPrimitive.content }

        assertTrue("英文服应合入英文固定黑名单", blacklist.containsAll(listOf("Courier", "Gavial")))
        assertFalse("英文服不应出现简中固定黑名单", blacklist.contains("讯使"))
    }

    @Test
    fun roguelikeConfig_normalizesCoreCharThroughResourceDataManager() {
        val resourceDataManager = mockk<ResourceDataManager> {
            every { getCharacterByNameOrAlias("維什戴爾") } returns CharacterInfo(name = "维什戴尔")
        }
        val config = RoguelikeConfig(coreChar = "維什戴爾")
        val params = jsonOf(config, ctx.copy(resourceDataManager = resourceDataManager))

        assertEquals("维什戴尔", params["core_char"]?.jsonPrimitive?.content)
    }

    @Test
    fun reclamationConfig_usesClientSpecificDefaultTool_whenToolToCraftEmpty() {
        // 旧代码的无参重载硬编码 "Official"，非官服会拿到错误的默认造物
        assertEquals(
            listOf("荧光棒"),
            toolsToCraftOf(ctx.copy(clientType = "Official")),
        )
        assertEquals(
            listOf("ケミカルライト"),
            toolsToCraftOf(ctx.copy(clientType = "YoStarJP")),
        )
        assertEquals(
            listOf("Glow Stick"),
            toolsToCraftOf(ctx.copy(clientType = "YoStarEN")),
        )
    }

    @Test
    fun wakeUpConfig_emitsOwnClientType_notContextClientType() {
        // WakeUpConfig 自身携带 clientType，ctx 里的同名字段与它无关，勿混用
        val params = jsonOf(WakeUpConfig(clientType = "Bilibili"), ctx)

        assertEquals("Bilibili", params["client_type"]?.jsonPrimitive?.content)
    }

    @Test
    fun fightConfig_inventoryTarget_appendsNeedNotFullTarget() {
        val depot = mockk<com.aliothmoon.maameow.data.repository.DepotRepository> {
            every { countOf("30011") } returns 90
        }
        val config = FightConfig(
            stage1 = "1-7",
            isSpecifiedDrops = true,
            isInventoryTarget = true,
            dropsItemId = "30011",
            dropsQuantity = 100,
        )
        val params = jsonOf(config, ctx.copy(depotRepository = depot))

        assertEquals("10", params["drops"]!!.jsonObject["30011"]!!.jsonPrimitive.content)
        assertTrue(config.toTaskParams(ctx.copy(depotRepository = depot)).params.single().dropTarget != null)
    }

    @Test
    fun fightConfig_inventoryTarget_alreadyEnough_setsTimesZero() {
        val depot = mockk<com.aliothmoon.maameow.data.repository.DepotRepository> {
            every { countOf("30011") } returns 100
        }
        val config = FightConfig(
            stage1 = "1-7",
            isSpecifiedDrops = true,
            isInventoryTarget = true,
            dropsItemId = "30011",
            dropsQuantity = 100,
        )
        val params = jsonOf(config, ctx.copy(depotRepository = depot))

        assertEquals("0", params["times"]!!.jsonPrimitive.content)
        assertEquals("1", params["drops"]!!.jsonObject["30011"]!!.jsonPrimitive.content)
    }

    private fun toolsToCraftOf(context: TaskParamContext): List<String> =
        jsonOf(ReclamationConfig(), context)["tools_to_craft"]!!
            .jsonArray.map { it.jsonPrimitive.content }

    private fun creditFightOf(config: MallConfig, context: TaskParamContext): Boolean =
        jsonOf(config, context)["credit_fight"]!!.jsonPrimitive.content.toBoolean()

    private fun jsonOf(config: TaskParamProvider, context: TaskParamContext) =
        Json.parseToJsonElement(config.toTaskParams(context).params.single().params).jsonObject
}
