package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.DepotSnapshot
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.FightDropsRefresher
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TaskParamProvider] 的契约：外部输入统一由 [TaskParamContext] 注入，
 * 每个非容器型配置恰好展开为一个属于自己类型的 MaaCore 任务。
 */
class TaskParamProviderContractTest {

    private val baseActivity = alwaysOpenActivityManager()

    private fun ctx(
        clientType: String = "Official",
        chainAllowsCreditFight: Boolean = false,
        depotRepository: DepotRepository = mockk(relaxed = true),
        resourceDataManager: ResourceDataManager = mockk(relaxed = true),
        dropsRefresher: FightDropsRefresher = mockk(relaxed = true),
        logSink: CollectingPreflightLogSink = CollectingPreflightLogSink(),
    ) = testTaskParamContext(
        clientType = clientType,
        chainAllowsCreditFight = chainAllowsCreditFight,
        activityManager = baseActivity,
        depotRepository = depotRepository,
        resourceDataManager = resourceDataManager,
        dropsRefresher = dropsRefresher,
        logSink = logSink,
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
        val logSink = CollectingPreflightLogSink()
        val context = ctx(logSink = logSink)

        configs.forEach { (config, expectedType) ->
            val params = config.toTaskParams(context)
            assertEquals("${config::class.simpleName} 应恰好产出 1 个任务", 1, params.size)
            assertEquals(expectedType, params.single().type)
        }
        assertTrue(logSink.entries.isEmpty())
    }

    @Test
    fun mallConfig_requiresBothOwnSwitchAndChainPermission() {
        assertFalse(
            creditFightOf(MallConfig(creditFight = false), ctx(chainAllowsCreditFight = true))
        )
        assertFalse(
            creditFightOf(MallConfig(creditFight = true), ctx(chainAllowsCreditFight = false))
        )
        assertTrue(
            creditFightOf(MallConfig(creditFight = true), ctx(chainAllowsCreditFight = true))
        )
    }

    @Test
    fun mallConfig_mergesFixedBlacklistByClientType() {
        val blacklist = jsonOf(MallConfig(), ctx(clientType = "YoStarEN"))["blacklist"]!!
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
        val params = jsonOf(config, ctx(resourceDataManager = resourceDataManager))

        assertEquals("维什戴尔", params["core_char"]?.jsonPrimitive?.content)
    }

    @Test
    fun reclamationConfig_usesClientSpecificDefaultTool_whenToolToCraftEmpty() {
        assertEquals(listOf("荧光棒"), toolsToCraftOf(ctx(clientType = "Official")))
        assertEquals(listOf("ケミカルライト"), toolsToCraftOf(ctx(clientType = "YoStarJP")))
        assertEquals(listOf("Glow Stick"), toolsToCraftOf(ctx(clientType = "YoStarEN")))
    }

    @Test
    fun wakeUpConfig_emitsOwnClientType_notContextClientType() {
        val params = jsonOf(WakeUpConfig(clientType = "Bilibili"), ctx())

        assertEquals("Bilibili", params["client_type"]?.jsonPrimitive?.content)
    }

    @Test
    fun fightConfig_inventoryTarget_appendsNeedNotFullTarget() {
        val depot = mockk<DepotRepository> {
            every { snapshot } returns MutableStateFlow(DepotSnapshot(syncTimeMillis = 1L))
            every { countOf("30011") } returns 90
        }
        val refresher = mockk<FightDropsRefresher>(relaxed = true)
        val config = FightConfig(
            stage1 = "1-7",
            isSpecifiedDrops = true,
            isInventoryTarget = true,
            dropsItemId = "30011",
            dropsQuantity = 100,
        )
        val context = ctx(depotRepository = depot, dropsRefresher = refresher)
        val params = config.toTaskParams(context)
        val json = Json.parseToJsonElement(params.single().params).jsonObject

        assertEquals("10", json["drops"]!!.jsonObject["30011"]!!.jsonPrimitive.content)
        assertNull(params.single().slot) // slot 由 Analyze 盖戳，toTaskParams 不写
        verify(exactly = 1) {
            refresher.stage(
                slot = match { it.nodeId == context.node.id && it.index == 0 },
                target = match {
                    it.dropId == "30011" && it.dropCount == 100 && it.logLabel == context.node.name
                },
            )
        }
    }

    @Test
    fun fightConfig_inventoryTarget_alreadyEnough_skipsAppend() {
        val depot = mockk<DepotRepository> {
            every { snapshot } returns MutableStateFlow(DepotSnapshot(syncTimeMillis = 1L))
            every { countOf("30011") } returns 100
        }
        val refresher = mockk<FightDropsRefresher>(relaxed = true)
        val logSink = CollectingPreflightLogSink()
        val config = FightConfig(
            stage1 = "1-7",
            isSpecifiedDrops = true,
            isInventoryTarget = true,
            dropsItemId = "30011",
            dropsQuantity = 100,
        )
        val params = config.toTaskParams(
            ctx(depotRepository = depot, dropsRefresher = refresher, logSink = logSink),
        )

        assertTrue(params.isEmpty())
        assertEquals(1, logSink.entries.size)
        assertEquals(LogLevel.TRACE, logSink.entries.single().second)
        verify(exactly = 0) { refresher.stage(any(), any()) }
    }

    @Test
    fun fightConfig_inventoryTarget_noDepotData_skipsWithWarning() {
        val depot = mockk<DepotRepository> {
            every { snapshot } returns MutableStateFlow(DepotSnapshot())
        }
        val refresher = mockk<FightDropsRefresher>(relaxed = true)
        val logSink = CollectingPreflightLogSink()
        val config = FightConfig(
            stage1 = "1-7",
            isSpecifiedDrops = true,
            isInventoryTarget = true,
            dropsItemId = "30011",
            dropsQuantity = 100,
        )
        val params = config.toTaskParams(
            ctx(depotRepository = depot, dropsRefresher = refresher, logSink = logSink),
        )

        assertTrue(params.isEmpty())
        assertEquals(1, logSink.entries.size)
        assertEquals(LogLevel.WARNING, logSink.entries.single().second)
        assertEquals(
            R.string.runlog_fight_inventory_unavailable,
            (logSink.entries.single().first as UiText.Resource).resId,
        )
        verify(exactly = 0) { refresher.stage(any(), any()) }
    }

    private fun toolsToCraftOf(context: TaskParamContext): List<String> =
        jsonOf(ReclamationConfig(), context)["tools_to_craft"]!!
            .jsonArray.map { it.jsonPrimitive.content }

    private fun creditFightOf(config: MallConfig, context: TaskParamContext): Boolean =
        jsonOf(config, context)["credit_fight"]!!.jsonPrimitive.content.toBoolean()

    private fun jsonOf(config: TaskParamProvider, context: TaskParamContext) =
        Json.parseToJsonElement(config.toTaskParams(context).single().params).jsonObject
}
