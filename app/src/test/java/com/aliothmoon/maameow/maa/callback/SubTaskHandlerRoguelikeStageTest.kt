package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/** 肉鸽节点日志，上游 v6.21.2 补上萨卡兹「已深化 / 可重建」变体，对齐 WPF AsstProxy */
class SubTaskHandlerRoguelikeStageTest {

    private val pkg = "com.aliothmoon.maameow"
    private val resources: Resources = mockk()
    private val context: Context = mockk {
        every { resources } returns this@SubTaskHandlerRoguelikeStageTest.resources
        every { packageName } returns pkg
    }
    private val sessionLogger: MaaSessionLogger = mockk(relaxed = true)

    private val handler = SubTaskHandler(
        applicationContext = context,
        statusTracker = mockk(relaxed = true),
        sessionLogger = sessionLogger,
        copilotRuntimeStateStore = mockk(relaxed = true),
        resourceDataManager = mockk<ResourceDataManager>(relaxed = true),
        toolboxResultCollector = mockk(relaxed = true),
        notificationCenter = mockk<MaaNotificationCenter>(relaxed = true),
        chainState = mockk<TaskChainState>(relaxed = true),
        activityManager = mockk<ActivityManager>(relaxed = true),
        achievementRepository = mockk<AchievementRepository>(relaxed = true),
        depotRepository = mockk<DepotRepository>(relaxed = true),
    )

    private fun stub(name: String, id: Int, text: String) {
        every { resources.getIdentifier(name, "string", pkg) } returns id
        every { resources.getString(id) } returns text
    }

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        every { resources.getIdentifier(any(), "string", pkg) } returns 0
        stub("maa_trader", 1, "节点: 诡意行商")
        stub("maa_safe_house", 2, "节点: 安全的角落")
        stub("maa_filter_truth", 3, "节点: 去伪存真")
    }

    private fun start(task: String) = handler.onSubTaskStart(
        JSONObject.of(
            "taskchain", "Roguelike",
            "subtask", "ProcessTask",
            "details", JSONObject.of("task", task, "exec_times", 1),
        )
    )

    @Test
    fun trader_allVariants() {
        listOf("StageTrader", "StageTraderHD", "StageTraderCR").forEach(::start)
        verify(exactly = 3) { sessionLogger.append("节点: 诡意行商", LogLevel.INFO) }
    }

    @Test
    fun safeHouse_allVariants() {
        listOf("StageSafeHouse", "StageSafeHouseHD", "StageSafeHouseCR").forEach(::start)
        verify(exactly = 3) { sessionLogger.append("节点: 安全的角落", LogLevel.INFO) }
    }

    @Test
    fun filterTruth_allVariants() {
        listOf("StageFilterTruth", "StageFilterTruthHD", "StageFilterTruthCR").forEach(::start)
        verify(exactly = 3) { sessionLogger.append("节点: 去伪存真", LogLevel.INFO) }
    }
}
