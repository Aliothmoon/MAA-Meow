package com.aliothmoon.maameow.schedule

import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.RunTelemetry
import com.aliothmoon.maameow.manager.RemoteAccessCoordinator
import com.aliothmoon.maameow.manager.RootManager
import com.aliothmoon.maameow.manager.ShizukuManager
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.service.ShizukuDownMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/** 到点必失败的才提醒：后端是 Shizuku、有启用的定时、等过 binder 仍不在 */
class ShizukuDownMonitorTest {

    private val strategies = MutableStateFlow<List<ScheduleStrategy>>(emptyList())
    private val repository = mockk<ScheduleStrategyRepository> {
        every { isLoaded } returns MutableStateFlow(true)
        every { strategies } returns this@ShizukuDownMonitorTest.strategies
    }
    private val notifications = mockk<MaaNotificationCenter>(relaxed = true)
    private val telemetry = mockk<RunTelemetry>(relaxed = true)
    private val monitor = ShizukuDownMonitor(mockk(relaxed = true), repository, notifications, telemetry)

    private val daily = ScheduleStrategy(id = "daily", name = "Daily", profileId = "profile-1")

    @Before
    fun setUp() {
        // RemoteAccessCoordinator 初始化就会取快照，两个后端得先于它桩掉
        mockkObject(ShizukuManager, RootManager)
        every { ShizukuManager.isAvailable() } returns false
        every { ShizukuManager.isGranted() } returns false
        every { RootManager.isAvailable() } returns false
        every { RootManager.isGranted() } returns false
        mockkObject(RemoteAccessCoordinator)
        every { RemoteAccessCoordinator.configuredBackend() } returns RemoteBackend.SHIZUKU
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun down_withEnabledSchedule_remindsAndReports() = runBlocking {
        strategies.value = listOf(daily, daily.copy(id = "off", enabled = false))

        monitor.check(afterBoot = true)

        verify(exactly = 1) { notifications.notifyShizukuDown(true) }
        verify(exactly = 1) { telemetry.onShizukuDown(true, 1) }
    }

    @Test
    fun running_staysQuiet() = runBlocking {
        strategies.value = listOf(daily)
        every { ShizukuManager.isAvailable() } returns true

        monitor.check(afterBoot = false)

        verify(exactly = 0) { notifications.notifyShizukuDown(any()) }
        verify(exactly = 0) { telemetry.onShizukuDown(any(), any()) }
    }

    @Test
    fun noEnabledSchedule_staysQuiet() = runBlocking {
        strategies.value = listOf(daily.copy(enabled = false))

        monitor.check(afterBoot = true)

        verify(exactly = 0) { notifications.notifyShizukuDown(any()) }
    }

    @Test
    fun rootBackend_staysQuiet() = runBlocking {
        strategies.value = listOf(daily)
        every { RemoteAccessCoordinator.configuredBackend() } returns RemoteBackend.ROOT

        monitor.check(afterBoot = true)

        verify(exactly = 0) { notifications.notifyShizukuDown(any()) }
    }
}
