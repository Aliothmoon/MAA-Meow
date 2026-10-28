package com.aliothmoon.maameow.schedule

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.service.ScheduleFailureReporter
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException

class ScheduleFailureReporterTest {
    private val logger = mockk<ScheduleTriggerLogger> {
        every { resolveMessage(any()) } returns "failed"
    }
    private val repository = mockk<ScheduleStrategyRepository>()
    private val settings = mockk<AppSettingsManager> {
        every { runMode } returns MutableStateFlow(RunMode.BACKGROUND)
    }
    private val notificationCenter = mockk<MaaNotificationCenter>(relaxed = true)
    private val reporter = ScheduleFailureReporter(logger, repository, settings, notificationCenter)
    private val message = uiTextOf(R.string.schedule_log_app_init_failed, "timeout")

    @Test
    fun logWriteFailureStillRecordsResult() = runBlocking {
        every { logger.writeClosed(any(), any(), any(), any(), any(), any()) } throws IOException("no dir")
        coEvery { repository.recordExecutionResult(any(), any(), any(), any()) } returns Unit

        reporter.report("daily", "Daily", 123L, ExecutionResult.FAILED_START, message)

        coVerify(exactly = 1) {
            repository.recordExecutionResult("daily", ExecutionResult.FAILED_START, "failed", any())
        }
    }

    @Test
    fun recordFailureDoesNotEscape() = runBlocking {
        every { logger.writeClosed(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { repository.recordExecutionResult(any(), any(), any(), any()) } throws IOException("disk full")

        reporter.report("daily", "Daily", 123L, ExecutionResult.FAILED_START, message)
    }

    // 进不了流水线时界面多半不在，只能靠通知；前面写盘失败也要发
    @Test
    fun notifiesEvenWhenRecordingFails() = runBlocking {
        every { logger.writeClosed(any(), any(), any(), any(), any(), any()) } throws IOException("no dir")
        coEvery { repository.recordExecutionResult(any(), any(), any(), any()) } throws IOException("disk full")

        reporter.report("daily", "Daily", 123L, ExecutionResult.FAILED_UI_LAUNCH, message)

        coVerify(exactly = 1) {
            notificationCenter.notifyLaunchNotStarted("Daily", ExecutionResult.FAILED_UI_LAUNCH, message)
        }
    }

    @Test
    fun retryScheduled_recordsWithoutNotifying() = runBlocking {
        every { logger.writeClosed(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { repository.recordExecutionResult(any(), any(), any(), any()) } returns Unit

        reporter.report("daily", "daily", 123L, ExecutionResult.FAILED_START, message, notify = false)

        coVerify(exactly = 1) {
            repository.recordExecutionResult("daily", ExecutionResult.FAILED_START, "failed", any())
        }
        coVerify(exactly = 0) { notificationCenter.notifyLaunchNotStarted(any(), any(), any()) }
    }
}
