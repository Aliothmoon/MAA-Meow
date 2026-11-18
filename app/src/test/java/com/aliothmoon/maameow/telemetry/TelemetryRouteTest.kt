package com.aliothmoon.maameow.telemetry

import io.sentry.SentryEvent
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryRouteTest {

    @Test
    fun `崩溃与 ANR 留下页面`() {
        listOf(
            event(exception("IllegalArgumentException", handled = false)),
            event(exception("ApplicationNotResponding", handled = null)),
        ).forEach {
            TelemetryRoute.filter(it)
            assertEquals("settings", it.getTag(TelemetryRoute.TAG))
        }
    }

    @Test
    fun `其余事件摘掉页面`() {
        listOf(
            event(),
            event(exception("IllegalStateException", handled = true)),
        ).forEach {
            TelemetryRoute.filter(it)
            assertNull(it.getTag(TelemetryRoute.TAG))
        }
    }

    private fun event(vararg exceptions: SentryException) = SentryEvent().apply {
        setTag(TelemetryRoute.TAG, "settings")
        if (exceptions.isNotEmpty()) this.exceptions = exceptions.toList()
    }

    private fun exception(type: String, handled: Boolean?) = SentryException().apply {
        this.type = type
        mechanism = Mechanism().apply { setHandled(handled) }
    }
}
