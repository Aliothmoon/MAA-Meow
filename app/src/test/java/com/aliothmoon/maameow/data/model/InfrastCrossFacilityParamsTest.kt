package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.domain.enums.InfrastRoomType
import com.aliothmoon.maameow.domain.enums.UiUsageConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 基建常规模式效率算法参数契约，对齐 WPF AsstInfrastTask.Serialize
 */
class InfrastCrossFacilityParamsTest {

    private val crossFacilityKeys = listOf(
        "use_pinus_sylvestris",
        "use_perception_information",
        "use_worldly_plight",
        "use_abyssal_hunter",
    )

    @Test
    fun defaults_matchUpstream() {
        val json = paramsOf(InfrastConfig())
        assertEquals(listOf("清流", "可露希尔", "但书"), fiammettaTargetsOf(json))
        // 上游 v6.17.0-beta.9 起菲亚梅塔恢复默认关闭
        assertFalse(json.getValue("fiammetta_recovery_enabled").jsonPrimitive.boolean)
        // 与 WPF InfrastTask.DormTrustEnabled 默认值对齐
        assertTrue(json.getValue("dorm_trust_enabled").jsonPrimitive.boolean)
        crossFacilityKeys.forEach { key ->
            assertFalse(key, json.getValue(key).jsonPrimitive.boolean)
        }
    }

    @Test
    fun fiammettaRecoveryEnabled_isEmittedRegardlessOfMode() {
        val config = InfrastConfig(fiammettaRecoveryEnabled = true)
        com.aliothmoon.maameow.domain.enums.InfrastMode.entries.forEach { mode ->
            val json = paramsOf(config.copy(mode = mode))
            assertTrue("$mode", json.getValue("fiammetta_recovery_enabled").jsonPrimitive.boolean)
        }
    }

    @Test
    fun fiammettaTargets_stillEmittedWhenRecoveryDisabled_coreIgnoresThem() {
        // core 关闭恢复时直接把名单置空，GUI 侧不做裁剪，保留用户选择
        val json = paramsOf(
            InfrastConfig(fiammettaRecoveryEnabled = false, fiammettaTargets = listOf("巫恋"))
        )
        assertEquals(listOf("巫恋"), fiammettaTargetsOf(json))
    }

    @Test
    fun fiammettaTargets_dropBlankAndDuplicates_capAtThree() {
        val json = paramsOf(
            InfrastConfig(fiammettaTargets = listOf("清流", " ", "清流", "但书", "巫恋", "龙舌兰"))
        )
        assertEquals(listOf("清流", "但书", "巫恋"), fiammettaTargetsOf(json))
    }

    @Test
    fun fiammettaTargets_emptyIsEmittedAsEmptyArray_coreFallsBackToDefault() {
        val json = paramsOf(InfrastConfig(fiammettaTargets = emptyList()))
        assertTrue(fiammettaTargetsOf(json).isEmpty())
    }

    @Test
    fun crossFacilityFlags_areEmittedRegardlessOfMode() {
        val config = InfrastConfig(
            usePinusSylvestris = true,
            usePerceptionInformation = true,
            useWorldlyPlight = true,
            useAbyssalHunter = true,
        )
        com.aliothmoon.maameow.domain.enums.InfrastMode.entries.forEach { mode ->
            val json = paramsOf(config.copy(mode = mode))
            crossFacilityKeys.forEach { key ->
                assertTrue("$mode/$key", json.getValue(key).jsonPrimitive.boolean)
            }
        }
    }

    @Test
    fun facilities_missingRoomsAreAppendedDisabled() {
        // 老配置缺项时补齐为未启用，对齐 WPF RefreshInfrastRoomList 的补全分支
        val partial = InfrastConfig(
            facilities = listOf(
                com.aliothmoon.maameow.domain.enums.InfrastRoomType.Trade to true,
                com.aliothmoon.maameow.domain.enums.InfrastRoomType.Mfg to true,
            )
        )
        val normalized = partial.normalizedFacilities()

        assertEquals(
            com.aliothmoon.maameow.domain.enums.InfrastRoomType.values.size,
            normalized.size,
        )
        assertEquals(
            listOf(
                com.aliothmoon.maameow.domain.enums.InfrastRoomType.Trade,
                com.aliothmoon.maameow.domain.enums.InfrastRoomType.Mfg,
            ),
            normalized.take(2).map { it.first },
        )
        // 补齐项一律未启用，不会凭空多跑设施
        normalized.drop(2).forEach { assertFalse(it.first.name, it.second) }
        assertEquals(listOf("Trade", "Mfg"), facilityOf(paramsOf(partial)))
    }

    @Test
    fun assistantChange_newProfileEnabled_existingProfileBackfilledDisabled() {
        // 对齐 WPF：新配置全部启用，老配置补全时不启用
        assertEquals("AssistantChange", facilityOf(paramsOf(InfrastConfig())).last())

        val legacy = InfrastConfig(
            facilities = com.aliothmoon.maameow.domain.enums.InfrastRoomType.values
                .filter { it != com.aliothmoon.maameow.domain.enums.InfrastRoomType.AssistantChange }
                .map { it to true }
        )
        assertEquals(
            com.aliothmoon.maameow.domain.enums.InfrastRoomType.AssistantChange to false,
            legacy.normalizedFacilities().last(),
        )
        assertFalse(facilityOf(paramsOf(legacy)).contains("AssistantChange"))
    }

    @Test
    fun recycle_hiddenFromOldCore_stateKeptAcrossWrites() {
        // 旧 core 收到 Recycle 会拒掉整个基建任务，CI 默认 latest 拿到的可能还是旧 core
        val config = InfrastConfig()
        assertFalse(config.availableFacilities("v6.19.0").any { it.first == InfrastRoomType.Recycle })
        assertTrue(config.availableFacilities("v6.20.0-beta.1").contains(InfrastRoomType.Recycle to true))

        val cleared = config.withAvailableFacilities(
            config.availableFacilities("v6.19.0").map { it.first to false }
        )
        assertEquals(InfrastRoomType.values.size, cleared.facilities.size)
        assertTrue(cleared.normalizedFacilities().contains(InfrastRoomType.Recycle to true))
    }

    @Test
    fun recycle_existingProfileBackfilledDisabled() {
        val legacy = InfrastConfig(
            facilities = InfrastRoomType.values.filter { it != InfrastRoomType.Recycle }.map { it to true }
        )
        assertEquals(InfrastRoomType.Recycle to false, legacy.normalizedFacilities().last())
        assertTrue(legacy.availableFacilities("v6.20.0-beta.1").contains(InfrastRoomType.Recycle to false))
    }

    @Test
    fun facilities_duplicatesAreDropped() {
        val dup = InfrastConfig(
            facilities = InfrastConfig().facilities +
                (com.aliothmoon.maameow.domain.enums.InfrastRoomType.Mfg to false)
        )
        assertEquals(
            com.aliothmoon.maameow.domain.enums.InfrastRoomType.values.size,
            dup.normalizedFacilities().size,
        )
    }

    @Test
    fun fiammettaTargetOptions_matchUpstreamAndContainDefaults() {
        assertEquals(
            listOf(
                "清流", "可露希尔", "但书", "巫恋", "龙舌兰", "歌蕾蒂娅",
                "琴柳", "承曦格雷伊", "温蒂", "槐琥", "至简", "旅骨",
            ),
            UiUsageConstants.fiammettaTargetValues,
        )
        assertTrue(
            UiUsageConstants.fiammettaTargetValues.containsAll(UiUsageConstants.defaultFiammettaTargets)
        )
        assertEquals(3, UiUsageConstants.MAX_FIAMMETTA_TARGETS)
    }

    private fun facilityOf(json: JsonObject): List<String> =
        json.getValue("facility").jsonArray.map { it.jsonPrimitive.content }

    private fun fiammettaTargetsOf(json: JsonObject): List<String> =
        json.getValue("fiammetta_targets").jsonArray.map { it.jsonPrimitive.content }

    private fun paramsOf(config: InfrastConfig): JsonObject {
        val params = config.toTaskParams(testTaskParamContext()).single().params
        return Json.parseToJsonElement(params).jsonObject
    }
}
