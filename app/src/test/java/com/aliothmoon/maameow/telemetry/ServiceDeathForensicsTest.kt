package com.aliothmoon.maameow.telemetry

import com.aliothmoon.maameow.utils.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 死者自己读不了系统日志，下一个进程连上时替它取，只取一次 */
class ServiceDeathForensicsTest {

    private class MemoryStore : ServiceDeathForensics.Store {
        var pending: ServiceDeathForensics.Pending? = null
        override fun load() = pending
        override fun save(pending: ServiceDeathForensics.Pending) {
            this.pending = pending
        }

        override fun clear() {
            pending = null
        }
    }

    private val store = MemoryStore()
    private var now = 1_000_000L
    private val forensics = ServiceDeathForensics(store, clock = { now })

    private val death = ServiceDeathForensics.Pending(
        eventId = "e1",
        reason = "service_died",
        runId = "r1",
        attributes = mapOf("task.chain" to "Roguelike"),
        traceId = null,
        spanId = null,
        diedAtMs = 1_000_000L,
        pid = 111,
    )

    @Test
    fun anotherProcess_takesOnce() {
        forensics.remember(death)

        assertEquals(death, forensics.take(connectedPid = 222))
        assertNull(forensics.take(connectedPid = 222))
    }

    @Test
    fun sameProcess_wasNotKilled() {
        forensics.remember(death)

        assertNull(forensics.take(connectedPid = 111))
        assertNull(store.pending)
    }

    @Test
    fun unknownPid_keepsItForLater() {
        forensics.remember(death)

        assertNull(forensics.take(connectedPid = null))
        assertEquals(death, store.pending)
    }

    @Test
    fun tooOld_logsAreGoneAnyway() {
        forensics.remember(death)
        now += 25 * 60 * 60_000L

        assertNull(forensics.take(connectedPid = 222))
        assertNull(store.pending)
    }

    @Test
    fun windowAndDelay() {
        assertEquals(940_000L, death.sinceMs)
        assertEquals(1_015_000L, death.untilMs)
        val context = death.logContext(delayMs = 42_000L)
        assertEquals("e1", context.eventId)
        assertEquals("42", context.attributes["forensics.delay_s"])
        assertEquals("Roguelike", context.attributes["task.chain"])
    }

    @Test
    fun pendingSurvivesJson() {
        val json = JsonUtils.common.encodeToString(ServiceDeathForensics.Pending.serializer(), death)

        assertEquals(death, JsonUtils.common.decodeFromString(ServiceDeathForensics.Pending.serializer(), json))
    }
}
