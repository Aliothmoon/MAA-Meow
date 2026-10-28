package com.aliothmoon.maameow.schedule

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.domain.service.RunTelemetry
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import java.io.File

/** 存储没就绪时打不开触发日志，这一次触发也不能崩 */
class ScheduleTriggerLoggerTest {

    @Test
    fun unwritableLogDir_stillRunsSessionToTelemetry() {
        // 拿普通文件当 debug 目录：建目录与打开文件都必然失败
        val notADir = File.createTempFile("trigger", ".blocker").apply { deleteOnExit() }
        val pathConfig = mockk<MaaPathConfig> { every { debugDir } returns notADir.absolutePath }
        val telemetry = mockk<RunTelemetry>(relaxed = true)
        val logger = ScheduleTriggerLogger(pathConfig, mockk(relaxed = true), telemetry)

        val session = logger.open("strategy", "定时任务", scheduledTimeMs = 0L)
        session.end(ExecutionResult.SKIPPED_LOCKED)

        verify { telemetry.onLaunchFinished(match { it.result == ExecutionResult.SKIPPED_LOCKED }) }
    }
}
