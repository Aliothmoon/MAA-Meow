package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/** 卫戍协议极速模拟刷角标 SubTaskExtraInfo 回调日志，对齐 WPF AsstProxy */
class SubTaskHandlerStrongholdProtocolTest {

    private val pkg = "com.aliothmoon.maameow"
    private val resources: Resources = mockk()
    private val context: Context = mockk {
        every { resources } returns this@SubTaskHandlerStrongholdProtocolTest.resources
        every { packageName } returns pkg
    }
    private val sessionLogger: com.aliothmoon.maameow.domain.service.MaaSessionLogger = mockk(relaxed = true)

    private val handler = SubTaskHandler(
        applicationContext = context,
        sessionLogger = sessionLogger,
        copilotRuntimeStateStore = mockk(relaxed = true),
        resourceDataManager = mockk(relaxed = true),
        toolboxResultCollector = mockk(relaxed = true),
        notificationCenter = mockk(relaxed = true),
        chainState = mockk(relaxed = true),
        activityManager = mockk(relaxed = true),
        achievementRepository = mockk(relaxed = true),
        depotRepository = mockk(relaxed = true),
        statusTracker = mockk(relaxed = true),
    )

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        every { resources.getIdentifier("maa_stronghold_strategy_log", "string", pkg) } returns R.string.maa_stronghold_strategy_log
        every { resources.getIdentifier("maa_stronghold_settlement_log", "string", pkg) } returns R.string.maa_stronghold_settlement_log
        every { resources.getIdentifier("maa_stronghold_cleared", "string", pkg) } returns R.string.maa_stronghold_cleared
        every { resources.getIdentifier("maa_stronghold_failed", "string", pkg) } returns R.string.maa_stronghold_failed
        every { resources.getIdentifier("maa_stronghold_strategies_completed", "string", pkg) } returns R.string.maa_stronghold_strategies_completed

        every { resources.getString(R.string.maa_stronghold_strategy_log, *anyVararg()) } answers {
            val a = secondArg<Array<Any>>()
            "${a[0]}：通过 ${a[1]}/${a[2]}"
        }
        every { resources.getString(R.string.maa_stronghold_settlement_log, *anyVararg()) } answers {
            val a = secondArg<Array<Any>>()
            "${a[0]}，已通关 ${a[1]}/${a[2]}"
        }
        every { resources.getString(R.string.maa_stronghold_cleared) } returns "本局通关"
        every { resources.getString(R.string.maa_stronghold_failed) } returns "本局失败"
        every { resources.getString(R.string.maa_stronghold_strategies_completed, *anyVararg()) } answers {
            "所有策略均已通过 ${secondArg<Array<Any>>()[0]} 次，任务结束"
        }
    }

    @After
    fun tearDown() = MaaStringRes.clearCacheForTest()

    private fun extra(what: String, details: JSONObject) = handler.onSubTaskExtraInfo(
        JSONObject.of("taskchain", "StrongholdProtocol", "what", what, "details", details)
    )

    @Test
    fun strategy_logsInfo() {
        extra("StrongholdStrategy", JSONObject.of("name", "歌利亚", "passed", 2, "target", 3))
        verify { sessionLogger.append("歌利亚：通过 2/3", LogLevel.INFO) }
    }

    @Test
    fun settlement_cleared_logsSuccess() {
        extra("StrongholdSettlement", JSONObject.of("cleared", true, "progress", 5, "total", 60))
        verify { sessionLogger.append("本局通关，已通关 5/60", LogLevel.SUCCESS) }
    }

    @Test
    fun settlement_failed_logsWarning() {
        extra("StrongholdSettlement", JSONObject.of("cleared", false, "progress", 5, "total", 60))
        verify { sessionLogger.append("本局失败，已通关 5/60", LogLevel.WARNING) }
    }

    @Test
    fun strategiesCompleted_logsSuccess() {
        extra("StrongholdStrategiesCompleted", JSONObject.of("target", 6))
        verify { sessionLogger.append("所有策略均已通过 6 次，任务结束", LogLevel.SUCCESS) }
    }
}
