package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/** 上游 v6.20.0-beta.1 自定义排班保持原样/候选未命中回调，对齐 WPF AsstProxy */
class SubTaskHandlerInfrastCustomPlanTest {
    private val pkg = "com.aliothmoon.maameow"
    private val resources = mockk<Resources>()
    private val context = mockk<Context> {
        every { resources } returns this@SubTaskHandlerInfrastCustomPlanTest.resources
        every { packageName } returns pkg
    }
    private val logger = mockk<MaaSessionLogger>(relaxed = true)
    private val manager = mockk<ResourceDataManager> {
        every { getLocalizedCharacterName(any<String>(), any()) } answers {
            if (firstArg<String>() == "Amiya") "阿米娅" else firstArg()
        }
    }
    private val handler = SubTaskHandler(
        applicationContext = context,
        statusTracker = mockk(relaxed = true),
        sessionLogger = logger,
        copilotRuntimeStateStore = mockk(relaxed = true),
        resourceDataManager = manager,
        toolboxResultCollector = mockk(relaxed = true),
        notificationCenter = mockk(relaxed = true),
        chainState = mockk(relaxed = true),
        activityManager = mockk(relaxed = true),
        achievementRepository = mockk(relaxed = true),
        depotRepository = mockk(relaxed = true),
    )

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        val settings = mockk<AppSettingsManager> { every { displayLanguage } returns "zh-cn" }
        startKoin { modules(module { single { settings } }) }
        every { resources.getIdentifier(any(), "string", pkg) } returns 0
        stubPlain("maa_this_facility", 1, "当前设施: ")
        stubPlain("maa_mfg", 2, "制造站")
        stubPlain("maa_recycle", 3, "回收站")
        stubPlain("maa_infrast_candidates_working", 4, "候选在班")
        stubPlain("maa_infrast_room_kept_unchanged", 5, "未配置选人")
        stubPlain("maa_infrast_facility_kept_unchanged", 6, "整设施原样")
        stubFormat("maa_infrast_rooms_not_configured", 7, "间起未配置：")
        stubFormat("maa_infrast_assistant_working_excluded", 8, "协助者在班：")
        stubFormat("maa_infrast_assistant_not_found", 9, "协助者未命中：")
        stubPlain("maa_room_groups_match_recog_failed", 10, "编组识别失败")
    }

    @After
    fun tearDown() {
        stopKoin()
        MaaStringRes.clearCacheForTest()
    }

    private fun stubFormat(name: String, id: Int, prefix: String) {
        every { resources.getIdentifier(name, "string", pkg) } returns id
        every { resources.getString(id, *anyVararg()) } answers {
            prefix + secondArg<Array<Any>>()[0]
        }
    }

    private fun stubPlain(name: String, id: Int, text: String) {
        every { resources.getIdentifier(name, "string", pkg) } returns id
        every { resources.getString(id) } returns text
    }

    private fun extra(what: String, vararg kv: Any) = handler.onSubTaskExtraInfo(
        JSONObject.of(
            "taskchain", "Infrast", "what", what,
            "details", JSONObject().apply { kv.toList().chunked(2).forEach { (k, v) -> put(k as String, v) } },
        )
    )

    @Test
    fun keptUnchanged_afterEnterFacility_appendsReasonOnly() {
        extra("EnterFacility", "facility", "Mfg", "index", 0)
        extra("CustomInfrastRoomKeptUnchanged", "facility", "Mfg", "index", 0, "reason", "candidates_working")

        verify(exactly = 1) { logger.append("当前设施: 制造站 01", LogLevel.TRACE) }
        verify { logger.append("候选在班", LogLevel.TRACE) }
    }

    @Test
    fun keptUnchanged_beforeEnterFacility_prependsFacilityLine() {
        extra("CustomInfrastRoomKeptUnchanged", "facility", "Recycle", "index", 1, "reason", "room_no_intent")

        verifyOrder {
            logger.append("当前设施: 回收站 02", LogLevel.TRACE)
            logger.append("未配置选人", LogLevel.TRACE)
        }
    }

    @Test
    fun facilityEmpty_facilityLineWithoutIndex() {
        extra("CustomInfrastRoomKeptUnchanged", "facility", "Recycle", "index", 0, "reason", "facility_empty")

        verifyOrder {
            logger.append("当前设施: 回收站", LogLevel.TRACE)
            logger.append("整设施原样", LogLevel.TRACE)
        }
    }

    @Test
    fun roomsNotConfigured_otherRoomGetsOwnFacilityLine() {
        extra("EnterFacility", "facility", "Mfg", "index", 1)
        extra("CustomInfrastRoomsNotConfigured", "facility", "Mfg", "index", 2)

        verifyOrder {
            logger.append("当前设施: 制造站 02", LogLevel.TRACE)
            logger.append("当前设施: 制造站 03", LogLevel.TRACE)
            logger.append("间起未配置：03", LogLevel.TRACE)
        }
    }

    @Test
    fun taskChainStart_forgetsLastFacility() {
        extra("EnterFacility", "facility", "Mfg", "index", 0)
        handler.onTaskChainStart(1)
        extra("CustomInfrastRoomKeptUnchanged", "facility", "Mfg", "index", 0, "reason", "room_no_intent")

        verify(exactly = 2) { logger.append("当前设施: 制造站 01", LogLevel.TRACE) }
    }

    @Test
    fun assistantNotFound_prefersExcludedList() {
        extra(
            "CustomInfrastAssistantNotFound",
            "candidates", JSONArray.of("Amiya", "杜宾"),
            "working_excluded", JSONArray.of("Amiya"),
        )
        verify { logger.append("协助者在班：阿米娅", LogLevel.WARNING) }
    }

    @Test
    fun assistantNotFound_fallsBackToCandidates() {
        extra("CustomInfrastAssistantNotFound", "candidates", JSONArray.of("Amiya", "杜宾"))
        verify { logger.append("协助者未命中：阿米娅, 杜宾", LogLevel.WARNING) }
    }

    @Test
    fun groupsMatchFailed_recognitionFailedHasNoGroupList() {
        extra("CustomInfrastRoomGroupsMatchFailed", "reason", "recognition_failed")
        verify { logger.append("编组识别失败", LogLevel.TRACE) }
    }
}
