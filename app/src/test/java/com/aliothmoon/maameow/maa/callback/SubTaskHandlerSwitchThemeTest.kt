package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.domain.service.FightDropsRefresher
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class SubTaskHandlerSwitchThemeTest {
    private val pkg = "com.aliothmoon.maameow"
    private val resources = mockk<Resources>()
    private val context = mockk<Context> {
        every { resources } returns this@SubTaskHandlerSwitchThemeTest.resources
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
        stubFormat("maa_switch_theme_succeeded", 1, "已更换游戏主题：")
        stubFormat("maa_switch_theme_already_set", 2, "当前已是主题：")
        stubFormat("maa_switch_theme_locked", 3, "主题尚未解锁：")
        stubFormat("maa_switch_theme_not_found", 4, "未找到主题：")
        every { resources.getIdentifier("maa_switch_theme_skipped", "string", pkg) } returns 5
        every { resources.getString(5) } returns "没有候选主题，跳过"
    }

    @After
    fun tearDown() = MaaStringRes.clearCacheForTest()

    private fun stubFormat(name: String, id: Int, prefix: String) {
        every { resources.getIdentifier(name, "string", pkg) } returns id
        every { resources.getString(id, *anyVararg()) } answers {
            prefix + secondArg<Array<Any>>()[0]
        }
    }

    private fun completed(taskId: Int, task: String, text: String? = null) {
        handler.onSubTaskCompleted(
            JSONObject.of(
                "taskid", taskId,
                "taskchain", "SwitchTheme",
                "subtask", "ProcessTask",
                "details", JSONObject.of("task", task, "result", JSONObject.of("text", text)),
            )
        )
    }

    @Test
    fun selectedTheme_isReportedOnlyAfterConfirmation() {
        completed(1, "SwitchThemeByNameSelectTheme", "银凇")
        verify(exactly = 0) { logger.append(any(), any()) }

        completed(1, "SwitchThemeByNameConfirmTheme")
        verify(exactly = 1) { logger.append("已更换游戏主题：银凇", LogLevel.SUCCESS) }
    }

    @Test
    fun alreadySet_andLockedHaveDistinctResults() {
        completed(1, "SwitchThemeByNameSelectTheme", "银凇")
        completed(1, "SwitchThemeByNameAlreadySet")
        completed(2, "SwitchThemeByNameSelectTheme", "夜间")
        completed(2, "SwitchThemeByNameLockedTheme")

        verify { logger.append("当前已是主题：银凇", LogLevel.SUCCESS) }
        verify { logger.append("主题尚未解锁：夜间", LogLevel.ERROR) }
        verify(exactly = 0) { logger.append(match { it.startsWith("已更换游戏主题：") }, any()) }
    }

    @Test
    fun extraInfo_reportsSkippedAndNotFound_andClearsSelection() {
        completed(1, "SwitchThemeByNameSelectTheme", "旧主题")
        handler.onSubTaskExtraInfo(JSONObject.of("taskid", 1, "what", "SwitchThemeSkipped"))
        handler.onSubTaskExtraInfo(JSONObject.of(
            "taskid", 2, "what", "SwitchThemeNotFound", "details", JSONObject.of("theme", "梦乡"),
        ))
        completed(1, "SwitchThemeByNameConfirmTheme")

        verify { logger.append("没有候选主题，跳过", LogLevel.INFO) }
        verify { logger.append("未找到主题：梦乡", LogLevel.ERROR) }
        verify(exactly = 0) { logger.append("已更换游戏主题：旧主题", any()) }
    }

    @Test
    fun taskIds_keepTheirOwnSelection_andResultsConsumeIt() {
        completed(1, "SwitchThemeByNameSelectTheme", "银凇")
        completed(2, "SwitchThemeByNameSelectTheme", "夜间")
        completed(1, "SwitchThemeByNameConfirmTheme")
        completed(2, "SwitchThemeByNameAlreadySet")
        completed(1, "SwitchThemeByNameConfirmTheme")
        completed(2, "SwitchThemeByNameLockedTheme")

        verify(exactly = 1) { logger.append("已更换游戏主题：银凇", LogLevel.SUCCESS) }
        verify { logger.append("当前已是主题：夜间", LogLevel.SUCCESS) }
        verify { logger.append("已更换游戏主题：", LogLevel.SUCCESS) }
        verify { logger.append("主题尚未解锁：", LogLevel.ERROR) }
    }

    @Test
    fun newSession_discardsUnfinishedSelections() {
        completed(1, "SwitchThemeByNameSelectTheme", "旧主题")
        handler.resetSessionState()
        completed(1, "SwitchThemeByNameConfirmTheme")

        verify { logger.append("已更换游戏主题：", LogLevel.SUCCESS) }
    }

    @Test
    fun newTaskStart_discardsAnUnfinishedSelectionWithTheSameId() {
        val chainHandler = TaskChainHandler(
            applicationContext = context,
            sessionLogger = logger,
            statusTracker = TaskChainStatusTracker(),
            notificationCenter = mockk(relaxed = true),
            subTaskHandler = handler,
            taskChainState = mockk(relaxed = true),
            achievementRepository = mockk(relaxed = true),
            achievementReporter = mockk(relaxed = true),
            dropsRefresher = mockk {
                every { onTaskStarted(any()) } returns FightDropsRefresher.RefreshOutcome.Skipped
            },
        )
        completed(1, "SwitchThemeByNameSelectTheme", "旧主题")
        chainHandler.onTaskChainStart(JSONObject.of("taskid", 1, "taskchain", "SwitchTheme"))
        completed(1, "SwitchThemeByNameConfirmTheme")

        verify { logger.append("已更换游戏主题：", LogLevel.SUCCESS) }
    }

    @Test
    fun missingDetails_doNotReuseAPreviousNameOrCrash() {
        completed(1, "SwitchThemeByNameSelectTheme", "旧主题")
        handler.onSubTaskCompleted(JSONObject.of(
            "taskid", 1, "subtask", "ProcessTask", "taskchain", "SwitchTheme",
            "details", JSONObject.of("task", "SwitchThemeByNameSelectTheme"),
        ))
        completed(1, "SwitchThemeByNameConfirmTheme")
        handler.onSubTaskExtraInfo(JSONObject.of("what", "SwitchThemeNotFound"))

        verify { logger.append("已更换游戏主题：", LogLevel.SUCCESS) }
        verify { logger.append("未找到主题：", LogLevel.ERROR) }
    }
}
