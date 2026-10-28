package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.data.model.EventShopPreset
import com.aliothmoon.maameow.data.model.ReclamationConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.maa.task.MaaTaskType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 上游 v6.19.0-beta.1 牛杂「活动商店」黑名单：params.event_shop.blacklist，对齐 WPF StartEventShop */
class MiniGameEventShopBlacklistTest {

    private val presets = MutableStateFlow(emptySet<EventShopPreset>())
    private val custom = MutableStateFlow("")
    private val reclamation = MutableStateFlow(ReclamationConfig())
    private var clientType = "Official"

    private val delegate = MiniGameDelegate(
        appContext = mockk(relaxed = true),
        activityManager = mockk<ActivityManager> {
            every { miniGames } returns MutableStateFlow(emptyList())
        },
        compositionService = mockk(relaxed = true),
        scope = TestScope(),
        achievementRepository = mockk(relaxed = true),
        pixelArt = mockk {
            every { state } returns MutableStateFlow(PixelArtUiState())
        },
        sessionLogger = mockk(relaxed = true),
        appSettingsManager = mockk<AppSettingsManager> {
            every { eventShopBlacklistPresets } returns presets
            every { eventShopBlacklistCustom } returns custom
            every { reclamationConfig } returns reclamation
        },
        clientType = { clientType },
    )

    private fun params() = Json.parseToJsonElement(delegate.buildTaskParams().params).jsonObject

    private fun blacklist() = params().getValue("params").jsonObject
        .getValue("event_shop").jsonObject
        .getValue("blacklist").jsonArray
        .map { it.jsonPrimitive.content }

    @Test
    fun reclamation_isSubmittedAsOwnTaskType_withClientDefaultTool() {
        reclamation.value = ReclamationConfig(theme = "Tales", mode = ReclamationConfig.MODE_PROSPERITY_IN_SAVE)
        clientType = "YoStarEN"
        delegate.onTaskSelected("Reclamation")

        val task = delegate.buildTaskParams()

        assertEquals(MaaTaskType.RECLAMATION, task.type)
        val json = Json.parseToJsonElement(task.params).jsonObject
        assertEquals("Tales", json.getValue("theme").jsonPrimitive.content)
        assertEquals(listOf("Glow Stick"), json.getValue("tools_to_craft").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun emptyBlacklist_sendsNoParams() {
        val task = delegate.buildTaskParams()

        assertEquals(MaaTaskType.CUSTOM, task.type)
        assertEquals(listOf("SS@Store@Begin"), params().getValue("task_names").jsonArray.map { it.jsonPrimitive.content })
        assertFalse("params" in params())
    }

    @Test
    fun customFirst_thenPresetsInFixedOrder_deduplicated() {
        presets.value = setOf(EventShopPreset.FURNITURE_PART, EventShopPreset.LMD)
        custom.value = " 技巧概要 ;; 龙门币 ;碳"

        assertEquals(listOf("技巧概要", "龙门币", "碳", "家具"), blacklist())
    }

    @Test
    fun presetKeywords_followGameClientLanguage() {
        presets.value = setOf(EventShopPreset.DATA_SUPPLEMENT_INSTRUMENT, EventShopPreset.DATA_SUPPLEMENT_STICK)

        clientType = "YoStarEN"
        assertEquals(listOf("Data Supplement Instrument", "Data Supplement Stick"), blacklist())
        clientType = "txwy"
        assertEquals(listOf("數據增補儀", "數據增補條"), blacklist())
        clientType = "Bilibili"
        assertEquals(listOf("数据增补仪", "数据增补条"), blacklist())
    }

    @Test
    fun otherMiniGames_ignoreBlacklist() {
        presets.value = setOf(EventShopPreset.LMD)
        delegate.onTaskSelected("GreenTicket@Store@Begin")

        assertFalse("params" in params())
    }
}
