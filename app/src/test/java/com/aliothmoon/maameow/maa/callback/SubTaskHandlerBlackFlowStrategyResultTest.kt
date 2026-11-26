package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/** 上游 v6.23.2 秘境行商交易中止改走策略结果，对齐 WPF RoguelikeTaskUserControlModel */
class SubTaskHandlerBlackFlowStrategyResultTest {

    private val pkg = "com.aliothmoon.maameow"
    private val resources: Resources = mockk()
    private val context: Context = mockk {
        every { resources } returns this@SubTaskHandlerBlackFlowStrategyResultTest.resources
        every { packageName } returns pkg
    }
    private val sessionLogger: MaaSessionLogger = mockk(relaxed = true)

    private val handler = SubTaskHandler(
        applicationContext = context,
        statusTracker = mockk(relaxed = true),
        sessionLogger = sessionLogger,
        copilotRuntimeStateStore = mockk(relaxed = true),
        resourceDataManager = mockk(relaxed = true),
        toolboxResultCollector = mockk(relaxed = true),
        notificationCenter = mockk(relaxed = true),
        chainState = mockk(relaxed = true),
        activityManager = mockk(relaxed = true),
        achievementRepository = mockk(relaxed = true),
        depotRepository = mockk(relaxed = true),
    )

    @Before
    fun setUp() {
        every { resources.getString(R.string.blackflow_outcome_scrap_trade_aborted) } returns "秘境行商交易中止"
        every {
            resources.getString(R.string.blackflow_termination_scrap_trade_purchase_failed)
        } returns "购买确认框没有出现"
        every { resources.getString(R.string.blackflow_strategy_result, *anyVararg()) } answers {
            val args = secondArg<Array<Any>>()
            "肉鸽策略结束：${args[0]}（${args[1]}）"
        }
    }

    @Test
    fun scrapTradeAborted_logsOutcomeAndReasonAsWarning() {
        handler.onSubTaskExtraInfo(
            JSONObject.of(
                "taskchain", "Roguelike", "what", "BlackFlowStrategyResult",
                "details", JSONObject.of(
                    "succeeded", false,
                    "outcome", "scrap_trade_aborted",
                    "termination_reason", "scrap_trade_purchase_failed",
                ),
            )
        )
        verify { sessionLogger.append("肉鸽策略结束：秘境行商交易中止（购买确认框没有出现）", LogLevel.WARNING) }
    }
}
