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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** 上游 #18433：任务链完成日志带用时 */
class TaskChainHandlerTaskTimeTest {

    private val pkg = "com.aliothmoon.maameow"
    private val resources: Resources = mockk()
    private val context: Context = mockk {
        every { resources } returns this@TaskChainHandlerTaskTimeTest.resources
        every { packageName } returns pkg
    }
    private val sessionLogger: MaaSessionLogger = mockk(relaxed = true)
    private val handler = TaskChainHandler(
        applicationContext = context,
        sessionLogger = sessionLogger,
        statusTracker = TaskChainStatusTracker(),
        notificationCenter = mockk(relaxed = true),
        subTaskHandler = mockk(relaxed = true),
        taskChainState = mockk(relaxed = true),
        achievementRepository = mockk(relaxed = true),
        achievementReporter = mockk(relaxed = true),
        dropsRefresher = mockk(relaxed = true) {
            every { onTaskStarted(any()) } returns FightDropsRefresher.RefreshOutcome.Skipped
        },
    )

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
        every { resources.getIdentifier(any(), "string", pkg) } returns 0
        every { resources.getIdentifier("maa_complete_task", "string", pkg) } returns 1
        every { resources.getString(1) } returns "完成任务: "
        every { resources.getIdentifier("maa_task_time", "string", pkg) } returns 2
        every { resources.getString(2, *anyVararg()) } answers { "\n(用时 ${secondArg<Array<Any>>()[0]})" }
    }

    @After
    fun tearDown() = MaaStringRes.clearCacheForTest()

    private fun chain(taskId: Int) = JSONObject.of("taskid", taskId, "taskchain", "Award")

    @Test
    fun completedChain_appendsElapsedTime() {
        handler.onTaskChainStart(chain(3))
        handler.onTaskChainCompleted(chain(3))

        verify { sessionLogger.append("完成任务: Award\n(用时 0h 0m 0s)", LogLevel.SUCCESS) }
    }

    @Test
    fun completedWithoutStart_hasNoTime() {
        handler.onTaskChainCompleted(chain(4))

        verify { sessionLogger.append("完成任务: Award", LogLevel.SUCCESS) }
    }

    @Test
    fun durationFormat_keepsHoursPastADay() {
        assertEquals("0h 3m 25s", formatTaskDuration(TimeUnit.SECONDS.toNanos(205)))
        assertEquals("26h 0m 1s", formatTaskDuration(TimeUnit.SECONDS.toNanos(26 * 3600 + 1)))
        assertEquals("0h 0m 0s", formatTaskDuration(-1))
    }
}
