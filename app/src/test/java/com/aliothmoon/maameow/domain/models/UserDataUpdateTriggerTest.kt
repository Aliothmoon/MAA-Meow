package com.aliothmoon.maameow.domain.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class UserDataUpdateTriggerTest {

    private val zone = ZoneOffset.UTC

    private fun epochAtYjLocal(date: LocalDate, hour: Int = 12): Long =
        date.atTime(hour, 0).atZone(zone).plusHours(4).toInstant().toEpochMilli()

    @Test
    fun everyTime_alwaysDue() {
        assertTrue(
            isUserDataUpdateDue(
                lastSyncMillis = epochAtYjLocal(LocalDate.of(2026, 7, 27)),
                interval = UserDataUpdateTriggerInterval.EVERY_TIME,
                yjToday = LocalDate.of(2026, 7, 27),
                yjZone = zone,
            )
        )
    }

    @Test
    fun neverSynced_isDue() {
        assertTrue(
            isUserDataUpdateDue(
                lastSyncMillis = 0L,
                interval = UserDataUpdateTriggerInterval.DAILY,
                yjToday = LocalDate.of(2026, 7, 27),
                yjZone = zone,
            )
        )
    }

    @Test
    fun daily_sameYjDay_notDue() {
        val day = LocalDate.of(2026, 7, 27)
        assertFalse(
            isUserDataUpdateDue(
                lastSyncMillis = epochAtYjLocal(day, hour = 10),
                interval = UserDataUpdateTriggerInterval.DAILY,
                yjToday = day,
                yjZone = zone,
            )
        )
    }

    @Test
    fun daily_nextYjDay_isDue() {
        assertTrue(
            isUserDataUpdateDue(
                lastSyncMillis = epochAtYjLocal(LocalDate.of(2026, 7, 26)),
                interval = UserDataUpdateTriggerInterval.DAILY,
                yjToday = LocalDate.of(2026, 7, 27),
                yjZone = zone,
            )
        )
    }

    @Test
    fun weekly_sameIsoWeek_notDue() {
        // 2026-07-27 is Monday of ISO week
        val monday = LocalDate.of(2026, 7, 27)
        val wednesday = LocalDate.of(2026, 7, 29)
        assertFalse(
            isUserDataUpdateDue(
                lastSyncMillis = epochAtYjLocal(monday),
                interval = UserDataUpdateTriggerInterval.WEEKLY,
                yjToday = wednesday,
                yjZone = zone,
            )
        )
    }

    @Test
    fun weekly_nextIsoWeek_isDue() {
        val monday = LocalDate.of(2026, 7, 27)
        val nextMonday = LocalDate.of(2026, 8, 3)
        assertTrue(
            isUserDataUpdateDue(
                lastSyncMillis = epochAtYjLocal(monday),
                interval = UserDataUpdateTriggerInterval.WEEKLY,
                yjToday = nextMonday,
                yjZone = zone,
            )
        )
    }
}
