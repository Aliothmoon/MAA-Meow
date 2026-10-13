package com.aliothmoon.maameow.maa.callback

import android.content.Context
import android.content.res.Resources
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.FightDropsRefresher
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TaskChainError 的文案分支：Core 在 details.details.error 里带 TaskExceptionKind，
 * OutOfMemory 走专门提示，其余沿用「任务出错: 」前缀。
 */
class TaskChainHandlerErrorTest {

    private val pkg = "com.aliothmoon.maameow"
    private val resources: Resources = mockk()
    private val context: Context = mockk {
        every { resources } returns this@TaskChainHandlerErrorTest.resources
        every { packageName } returns pkg
    }
    private val sessionLogger: MaaSessionLogger = mockk(relaxed = true)
    private val notificationCenter: MaaNotificationCenter = mockk(relaxed = true)
    private val statusTracker = TaskChainStatusTracker()
    private val dropsRefresher: FightDropsRefresher = mockk(relaxed = true) {
        every { onTaskStarted(any()) } returns FightDropsRefresher.RefreshOutcome.Skipped
    }

    private val handler = TaskChainHandler(
        applicationContext = context,
        sessionLogger = sessionLogger,
        statusTracker = statusTracker,
        notificationCenter = notificationCenter,
        subTaskHandler = mockk(relaxed = true),
        taskChainState = mockk<TaskChainState>(relaxed = true),
        achievementRepository = mockk<AchievementRepository>(relaxed = true),
        achievementReporter = mockk<AchievementReporter>(relaxed = true),
        dropsRefresher = dropsRefresher,
    )

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        every { resources.getIdentifier(any(), "string", pkg) } returns 0
        every { resources.getIdentifier("maa_task_error", "string", pkg) } returns 1
        every { resources.getIdentifier("maa_out_of_memory_error", "string", pkg) } returns 2
        every { resources.getString(1) } returns "任务出错: "
        every { resources.getString(2, *anyVararg()) } answers {
            "${secondArg<Array<Any>>()[0]}任务因内存不足停止"
        }
        every { resources.getIdentifier("maa_task_time", "string", pkg) } returns 3
        every { resources.getString(3, *anyVararg()) } answers { "(${secondArg<Array<Any>>()[0]})" }
    }

    @Test
    fun registeredPlanName_isUsedForStartCompletionAndError() {
        val messages = mutableListOf<String>()
        every { sessionLogger.append(capture(messages), any()) } returns Unit
        statusTracker.register(7, "Fight", logName = UiText.Dynamic("材料补货 #3"))
        val details = JSONObject.of("taskchain", "Fight", "taskid", 7)

        handler.onTaskChainStart(details)
        handler.onTaskChainCompleted(details)
        handler.onTaskChainError(details)

        assertEquals(
            listOf("StartTask材料补货 #3", "CompleteTask材料补货 #3(0h 0m 0s)", "任务出错: 材料补货 #3"),
            messages,
        )
    }

    @Test
    fun outOfMemory_usesDedicatedMessage() {
        handler.onTaskChainError(
            JSONObject.of(
                "taskchain", "Infrast",
                "taskid", 7,
                "details", JSONObject.of("error", "OutOfMemory"),
            )
        )

        verify { sessionLogger.append("Infrast任务因内存不足停止", LogLevel.ERROR) }
        verify { notificationCenter.notifyTaskError("Infrast") }
    }

    @Test
    fun otherExceptionKinds_keepGenericPrefix() {
        handler.onTaskChainError(
            JSONObject.of(
                "taskchain", "Fight",
                "taskid", 8,
                "details", JSONObject.of("error", "OpenCV"),
            )
        )

        verify { sessionLogger.append("任务出错: Fight", LogLevel.ERROR) }
    }

    @Test
    fun plainRecognitionError_hasNoErrorField() {
        handler.onTaskChainError(JSONObject.of("taskchain", "Recruit", "taskid", 9))

        verify { sessionLogger.append("任务出错: Recruit", LogLevel.ERROR) }
    }
}
