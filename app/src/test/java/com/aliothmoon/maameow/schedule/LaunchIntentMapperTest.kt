package com.aliothmoon.maameow.schedule

import com.aliothmoon.maameow.domain.launch.LaunchSource
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** requestId 由 (策略, 计划时刻) 唯一决定，重复投递才能命中管线幂等 */
class LaunchIntentMapperTest {

    private fun strategy(id: String = "strat-1") = ScheduleStrategy(
        id = id,
        name = "定时任务-1",
        profileId = "profile-1",
    )

    @Test
    fun sameOccurrence_producesSameRequestId() {
        val s = strategy()
        val a = LaunchIntentMapper.fromStrategy(s, scheduledTimeMs = 1_786_364_760_000)
        val b = LaunchIntentMapper.fromStrategy(s, scheduledTimeMs = 1_786_364_760_000)
        assertEquals(a.requestId, b.requestId)
        assertEquals(LaunchSource.Schedule, a.source)
    }

    @Test
    fun laterOccurrence_producesDifferentRequestId() {
        val s = strategy()
        val a = LaunchIntentMapper.fromStrategy(s, scheduledTimeMs = 1_786_364_760_000)
        val b = LaunchIntentMapper.fromStrategy(s, scheduledTimeMs = 1_786_364_820_000)
        assertNotEquals(a.requestId, b.requestId)
    }

    @Test
    fun differentStrategies_producesDifferentRequestId() {
        val a = LaunchIntentMapper.fromStrategy(strategy("a"), scheduledTimeMs = 1_786_364_760_000)
        val b = LaunchIntentMapper.fromStrategy(strategy("b"), scheduledTimeMs = 1_786_364_760_000)
        assertNotEquals(a.requestId, b.requestId)
    }

    @Test
    fun strategyFlags_areCarriedIntoRequest() {
        val s = strategy().copy(
            forceStart = true,
            autoScreenSaver = true,
            autoSleepAfterTask = true,
            skipAutoSleepIfAwake = true,
            closeGameAfterTask = true,
        )
        val request = LaunchIntentMapper.fromStrategy(s, scheduledTimeMs = 1L)
        assertEquals(true, request.forceStart)
        assertEquals(true, request.autoScreenSaver)
        assertEquals(true, request.autoSleepAfterTask)
        assertEquals(true, request.skipAutoSleepIfAwake)
        assertEquals(true, request.closeGameAfterTask)
    }
}
