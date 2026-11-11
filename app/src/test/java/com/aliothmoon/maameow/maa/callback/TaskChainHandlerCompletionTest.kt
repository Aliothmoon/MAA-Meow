package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.LogKind
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.FightDropsRefresher
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.maa.task.TaskSlot
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/** 上游 v6.19.0-beta.1：出错任务名并入完成标题，不再另起错误清单 */
class TaskChainHandlerCompletionTest {

    private val pkg = "com.aliothmoon.maameow"
    private val resources: Resources = mockk()
    private val context: Context = mockk {
        every { resources } returns this@TaskChainHandlerCompletionTest.resources
        every { packageName } returns pkg
    }
    private val sessionLogger: MaaSessionLogger = mockk(relaxed = true)
    private val notificationCenter: MaaNotificationCenter = mockk(relaxed = true)
    private val statusTracker = TaskChainStatusTracker()
    private val subTaskHandler: SubTaskHandler = mockk(relaxed = true) {
        every { lastSanitySnapshot } returns null
    }

    private val handler = TaskChainHandler(
        applicationContext = context,
        sessionLogger = sessionLogger,
        statusTracker = statusTracker,
        notificationCenter = notificationCenter,
        subTaskHandler = subTaskHandler,
        taskChainState = mockk<TaskChainState>(relaxed = true),
        achievementRepository = mockk<AchievementRepository>(relaxed = true),
        achievementReporter = mockk<AchievementReporter>(relaxed = true),
        dropsRefresher = mockk<FightDropsRefresher>(relaxed = true),
    )

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        every { resources.getIdentifier(any(), "string", pkg) } returns 0
        every { resources.getIdentifier("maa_all_tasks_complete", "string", pkg) } returns 1
        every { resources.getIdentifier("maa_task_completed_with_errors", "string", pkg) } returns 2
        every { resources.getIdentifier("maa_task_error_summary_title", "string", pkg) } returns 3
        every { resources.getString(1) } returns "任务已全部完成！"
        every { resources.getString(2, *anyVararg()) } answers {
            "任务已结束，以下任务出现错误:\n${secondArg<Array<Any>>()[0]}"
        }
        every { resources.getString(3) } returns "本轮任务出现以下错误："
    }

    private fun verifyLogged(content: String, level: LogLevel, kind: LogKind) = verify {
        sessionLogger.append(match<LogItem> { it.content == content && it.level == level && it.kind == kind })
    }

    private fun failTasks() {
        statusTracker.register(7, "Fight", TaskSlot("n1"), UiText.Dynamic("理智作战"))
        statusTracker.register(8, "Infrast", TaskSlot("n2"), UiText.Dynamic("基建换班"))
        statusTracker.register(9, "Mall", TaskSlot("n3"), UiText.Dynamic("信用收支"))
        statusTracker.updateStatus(7, TaskRunStatus.ERROR)
        statusTracker.updateStatus(8, TaskRunStatus.ERROR)
        statusTracker.updateStatus(9, TaskRunStatus.COMPLETED)
    }

    @Test
    fun errors_goIntoTitle_withoutSeparateSummary() {
        failTasks()

        handler.onAllTasksCompleted()

        val title = "任务已结束，以下任务出现错误:\n理智作战, 基建换班"
        verify(exactly = 1) { sessionLogger.append(any<LogItem>()) }
        verifyLogged(title, LogLevel.ERROR, LogKind.SUMMARY)
        verify { notificationCenter.notifyAllTasksCompleted(title, null) }
    }

    @Test
    fun errors_keepSanityReportInSeparateLog() {
        failTasks()
        every { subTaskHandler.lastSanitySnapshot } returns SubTaskHandler.SanitySnapshot(current = 135, max = 135)
        every { resources.getIdentifier("maa_current_sanity", "string", pkg) } returns 4
        every { resources.getString(4, *anyVararg()) } returns "当前理智: 135/135"

        handler.onAllTasksCompleted()

        val title = "任务已结束，以下任务出现错误:\n理智作战, 基建换班"
        verify(exactly = 2) { sessionLogger.append(any<LogItem>()) }
        verifyLogged(title, LogLevel.ERROR, LogKind.SUMMARY)
        verifyLogged("当前理智: 135/135", LogLevel.MESSAGE, LogKind.SUMMARY)
        verify { notificationCenter.notifyAllTasksCompleted("$title\n当前理智: 135/135", null) }
    }

    @Test
    fun manualStop_keepsPlainTitle_andListsErrorsSeparately() {
        failTasks()

        handler.onAllTasksCompleted(asStopped = true)

        verifyLogged("任务已全部完成！", LogLevel.INFO, LogKind.SUMMARY)
        verifyLogged("本轮任务出现以下错误：\n理智作战\n基建换班", LogLevel.ERROR, LogKind.SUMMARY)
        verify(exactly = 0) { notificationCenter.notifyAllTasksCompleted(any(), any()) }
    }

    @Test
    fun noErrors_logsSuccessTitle() {
        statusTracker.register(7, "Fight", TaskSlot("n1"), UiText.Dynamic("理智作战"))
        statusTracker.updateStatus(7, TaskRunStatus.COMPLETED)

        handler.onAllTasksCompleted()

        verify(exactly = 1) { sessionLogger.append(any<LogItem>()) }
        verifyLogged("任务已全部完成！", LogLevel.SUCCESS, LogKind.SUMMARY)
        verify { notificationCenter.notifyAllTasksCompleted("任务已全部完成！", null) }
    }
}
