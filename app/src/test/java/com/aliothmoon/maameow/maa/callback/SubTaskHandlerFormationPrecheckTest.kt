package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/** 一图流数据辅助编队的预检回调，形状同 OperBoxDataConfig::precheck */
class SubTaskHandlerFormationPrecheckTest {
    private val pkg = "com.aliothmoon.maameow"
    private val resources = mockk<Resources>()
    private val context = mockk<Context> {
        every { resources } returns this@SubTaskHandlerFormationPrecheckTest.resources
        every { packageName } returns pkg
    }
    private val logger = mockk<MaaSessionLogger>(relaxed = true)
    private val handler = SubTaskHandler(
        applicationContext = context,
        statusTracker = mockk(relaxed = true),
        sessionLogger = logger,
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
        MaaStringRes.clearCacheForTest()
        every { resources.getIdentifier(any(), "string", pkg) } returns 0
        every { resources.getIdentifier("maa_battle_formation_operbox_matched", "string", pkg) } returns 1
        every { resources.getString(1) } returns "预匹配："
        every { resources.getIdentifier("maa_battle_formation_operbox1_unmatched", "string", pkg) } returns 2
        every { resources.getString(2, *anyVararg()) } answers {
            val args = secondArg<Array<Any>>()
            "${args[0]} 借 ${args[1]}"
        }
    }

    @After
    fun tearDown() = MaaStringRes.clearCacheForTest()

    private fun extraInfo(what: String, details: JSONObject) {
        handler.onSubTaskExtraInfo(
            JSONObject.of(
                "taskchain", "Copilot",
                "taskid", 0,
                "subtask", "BattleFormationTask",
                "what", what,
                "details", details,
            )
        )
    }

    @Test
    fun matchedGroups_areListedOnePerLine() {
        extraInfo(
            "BattleFormationOperboxMatched",
            JSONObject.of(
                "matched_groups", JSONArray.of(
                    JSONObject.of("group_name", "近卫", "oper_name", "棘刺"),
                    JSONObject.of("group_name", "阿米娅", "oper_name", "阿米娅"),
                )
            ),
        )
        verify { logger.append("预匹配：\n近卫 => 棘刺\n阿米娅 => 阿米娅", LogLevel.INFO) }
    }

    @Test
    fun singleUnmatchedGroup_suggestsSupportUnit() {
        extraInfo(
            "BattleFormationOperbox1Unmatched",
            JSONObject.of("group_name", "术师", "may_borrow_oper", "艾雅法拉"),
        )
        verify { logger.append("术师 借 艾雅法拉", LogLevel.WARNING) }
    }
}
