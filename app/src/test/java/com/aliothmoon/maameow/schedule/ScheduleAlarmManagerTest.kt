package com.aliothmoon.maameow.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.aliothmoon.maameow.MainActivity
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.model.ScheduleType
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import io.mockk.EqMatcher
import io.mockk.OfTypeMatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

private const val RESYNC_CODE = 0x5C4E

class ScheduleAlarmManagerTest {
    private val platformAlarms = mockk<AlarmManager>(relaxed = true)
    private val context = mockk<Context> {
        every { getSystemService(Context.ALARM_SERVICE) } returns platformAlarms
    }
    private val alarms = spyk(ScheduleAlarmManager(context))
    private val strategy = ScheduleStrategy(
        id = "daily", name = "Daily", profileId = "profile-1",
        scheduleType = ScheduleType.INTERVAL,
        startTimeMs = System.currentTimeMillis() + 3_600_000L,
        intervalMinutes = 60,
    )

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun fixedTimeInNextMinuteRegistersAtScheduledTime() {
        val now = ZonedDateTime.of(2026, 9, 9, 12, 0, 45, 0, ZoneId.systemDefault())
        val scheduled = now.plusMinutes(1).withSecond(0)
        mockkStatic(ZonedDateTime::class)
        every { ZonedDateTime.now(any<ZoneId>()) } returns now
        val scheduledTime = scheduled.toInstant().toEpochMilli()
        prepareRegistration(scheduledTime)

        assertTrue(alarms.scheduleNext(strategy.copy(
            scheduleType = ScheduleType.FIXED_TIME,
            daysOfWeek = setOf(scheduled.dayOfWeek),
            executionTimes = listOf(scheduled.toLocalTime()),
        )))

        verifyAlarmClockAt(scheduledTime)
        verify(exactly = 1) {
            anyConstructed<Intent>().putExtra(ScheduleAlarmManager.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
    }

    @Test
    fun intervalStartingSoonRegistersAtScheduledTime() {
        val scheduledTime = System.currentTimeMillis() + 15_000L
        prepareRegistration(scheduledTime)

        assertTrue(alarms.scheduleNext(strategy.copy(startTimeMs = scheduledTime)))

        verifyAlarmClockAt(scheduledTime)
        verify(exactly = 1) {
            anyConstructed<Intent>().putExtra(ScheduleAlarmManager.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
    }

    @Test
    fun exactModeRegistersWithoutAlarmClock() {
        val scheduledTime = System.currentTimeMillis() + 15_000L
        prepareRegistration()
        every { alarms.useAlarmClock } returns false

        assertTrue(alarms.scheduleNext(strategy.copy(startTimeMs = scheduledTime)))

        verify(exactly = 1) {
            platformAlarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledTime, any())
        }
        verify(exactly = 0) { platformAlarms.setAlarmClock(any(), any()) }
    }

    @Test
    fun missingPermissionSkipsRegistration() {
        every { alarms.canScheduleExact() } returns false
        assertFalse(alarms.scheduleNext(strategy))
        assertFalse(alarms.scheduleRetry(strategy.id, 123L))
        verify(exactly = 0) { platformAlarms.setAlarmClock(any(), any()) }
    }

    @Test
    fun revokedPermissionDuringRegistrationDoesNotCrash_andGrantAllowsRescheduling() {
        prepareRegistration()
        every { platformAlarms.setAlarmClock(any(), any()) } throws SecurityException("revoked")
        assertFalse(alarms.scheduleNext(strategy))
        every { platformAlarms.setAlarmClock(any(), any()) } returns Unit
        assertTrue(alarms.scheduleNext(strategy))
    }

    @Test
    fun retryIsCappedAndCarriesIncrementedCount() {
        prepareRegistration()

        assertTrue(alarms.scheduleRetry(strategy.id, 123L, retryCount = 0))
        verify(exactly = 1) {
            anyConstructed<Intent>().putExtra(ScheduleAlarmManager.EXTRA_RETRY_COUNT, 1)
        }
        assertTrue(alarms.scheduleRetry(strategy.id, 123L, retryCount = ScheduleAlarmManager.MAX_RETRY_COUNT - 1))
        assertFalse(alarms.scheduleRetry(strategy.id, 123L, retryCount = ScheduleAlarmManager.MAX_RETRY_COUNT))
        // 用尽不再拉 FGS，但要挂一个重排闹钟把定时链接上
        verify(exactly = 3) { platformAlarms.setAlarmClock(any(), any()) }
        verify(exactly = 1) { platformAlarms.setAlarmClock(any(), slotFor(RESYNC_CODE)) }
    }

    @Test
    fun rescheduleKeepsPendingRetry_butDisableDropsIt() {
        prepareRegistration()
        mockFiredPrefs(emptyMap())

        alarms.rescheduleAll(listOf(strategy))
        verify { platformAlarms.cancel(slotFor(nextCode("daily"))) }
        verify(exactly = 0) { platformAlarms.cancel(slotFor(retryCode("daily"))) }

        alarms.rescheduleAll(listOf(strategy.copy(enabled = false)))
        verify { platformAlarms.cancel(slotFor(retryCode("daily"))) }
    }

    @Test
    fun rescheduleAfterClockRollbackSkipsAlreadyFiredSlot() {
        // 时钟回拨后，已跑过的时段仍在「未来」，应排到明天而不是今天再跑一次
        val fired = ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(30).withSecond(0).withNano(0)
        val firedMs = fired.toInstant().toEpochMilli()
        val tomorrow = fired.plusDays(1).toInstant().toEpochMilli()
        prepareRegistration(tomorrow)
        val daily = strategy.copy(
            scheduleType = ScheduleType.FIXED_TIME,
            daysOfWeek = DayOfWeek.entries.toSet(),
            executionTimes = listOf(fired.toLocalTime()),
        )

        val after = ScheduleAlarmManager.resumeAfter(daily, firedMs, System.currentTimeMillis())
        assertEquals(firedMs, after)
        assertTrue(alarms.scheduleNext(daily, after))
        verifyAlarmClockAt(tomorrow)
    }

    @Test
    fun resumeAfterOnlyHonorsRollbackWithinOneCycle() {
        val now = 1_000_000_000L
        val hourly = strategy.copy(intervalMinutes = 60)
        val daily = strategy.copy(scheduleType = ScheduleType.FIXED_TIME)
        assertEquals(0L, ScheduleAlarmManager.resumeAfter(hourly, null, now))
        assertEquals(0L, ScheduleAlarmManager.resumeAfter(hourly, now - 1, now))
        assertEquals(now + 3_600_000L, ScheduleAlarmManager.resumeAfter(hourly, now + 3_600_000L, now))
        assertEquals(0L, ScheduleAlarmManager.resumeAfter(hourly, now + 3_600_001L, now))
        val dayMs = ScheduleAlarmManager.FIXED_RESUME_WINDOW_MS
        assertEquals(now + dayMs, ScheduleAlarmManager.resumeAfter(daily, now + dayMs, now))
        assertEquals(0L, ScheduleAlarmManager.resumeAfter(daily, now + dayMs + 1, now))
    }

    private fun prepareRegistration(triggerTime: Long? = null) {
        every { alarms.canScheduleExact() } returns true
        every { alarms.useAlarmClock } returns true
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setClassName(any<Context>(), any()) } answers { self as Intent }
        every { anyConstructed<Intent>().setClass(any<Context>(), MainActivity::class.java) } answers { self as Intent }
        every { anyConstructed<Intent>().setFlags(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Int>()) } answers { self as Intent }
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } answers {
            slots.getOrPut(secondArg()) { mockk(relaxed = true) }
        }
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()
        mockkConstructor(AlarmManager.AlarmClockInfo::class)
        if (triggerTime != null) {
            every {
                constructedWith<AlarmManager.AlarmClockInfo>(
                    EqMatcher(triggerTime),
                    OfTypeMatcher<PendingIntent>(PendingIntent::class),
                ).triggerTime
            } returns triggerTime
        }
    }

    private val slots = mutableMapOf<Int, PendingIntent>()

    private fun slotFor(requestCode: Int): PendingIntent =
        slots.getOrPut(requestCode) { mockk(relaxed = true) }

    private fun nextCode(id: String) = id.hashCode() and 0x7FFFFFFF
    private fun retryCode(id: String) = "$id#retry".hashCode() and 0x7FFFFFFF

    private fun mockFiredPrefs(fired: Map<String, Long>) {
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { all } returns fired
        }
        every { context.getSharedPreferences(any(), any()) } returns prefs
    }

    private fun verifyAlarmClockAt(triggerTime: Long) {
        val info = slot<AlarmManager.AlarmClockInfo>()
        verify(exactly = 1) { platformAlarms.setAlarmClock(capture(info), any()) }
        assertEquals(triggerTime, info.captured.triggerTime)
    }
}
