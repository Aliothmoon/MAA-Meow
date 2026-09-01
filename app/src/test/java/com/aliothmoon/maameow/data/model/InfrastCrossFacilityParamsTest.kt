package com.aliothmoon.maameow.data.model

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
    fun fiammettaTargetOptions_matchUpstreamAndContainDefaults() {
        assertEquals(
            listOf("清流", "可露希尔", "但书", "巫恋", "龙舌兰", "歌蕾蒂娅"),
            UiUsageConstants.fiammettaTargetValues,
        )
        assertTrue(
            UiUsageConstants.fiammettaTargetValues.containsAll(UiUsageConstants.defaultFiammettaTargets)
        )
        assertEquals(3, UiUsageConstants.MAX_FIAMMETTA_TARGETS)
    }

    private fun fiammettaTargetsOf(json: JsonObject): List<String> =
        json.getValue("fiammetta_targets").jsonArray.map { it.jsonPrimitive.content }

    private fun paramsOf(config: InfrastConfig): JsonObject {
        val params = config.toTaskParams(testTaskParamContext()).single().params
        return Json.parseToJsonElement(params).jsonObject
    }
}
