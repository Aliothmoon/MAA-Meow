package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.data.notification.provider.NotificationProvider
import com.aliothmoon.maameow.data.notification.provider.NotificationSendResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

class ExternalNotificationServiceTest {

    private val gate = CompletableDeferred<Unit>()
    private val sent = AtomicInteger(0)

    private val provider = object : NotificationProvider {
        override val id = "slow"

        override suspend fun send(title: String, content: String): NotificationSendResult {
            gate.await()
            if (content == SLOW) delay(300)
            sent.incrementAndGet()
            return NotificationSendResult.Success
        }
    }

    private fun service(): ExternalNotificationService {
        val settings = mockk<NotificationSettingsManager> {
            every { enabledProviderIds } returns MutableStateFlow(listOf("slow"))
            every { includeLogDetails } returns MutableStateFlow(false)
        }
        return ExternalNotificationService(settings, mockk(relaxed = true), listOf(provider))
    }

    /** imageChannelEnabled 的 stateIn 常驻，不能算成在途发送 */
    @Test
    fun awaitPendingSends_idle_returnsImmediately() = runBlocking<Unit> {
        val svc = service()
        val elapsed = measureTimeMillis { svc.awaitPendingSends(5_000) }
        assertTrue("waited ${elapsed}ms", elapsed < 1_000)
    }

    @Test
    fun awaitPendingSends_waitsForInFlightSend() = runBlocking<Unit> {
        val svc = service()
        svc.sendWithLogs("t", "c")
        launch {
            delay(200)
            gate.complete(Unit)
        }
        svc.awaitPendingSends(5_000)
        assertEquals(1, sent.get())
    }

    /** 等的过程中排上队的也要等，不能只等调用那一刻的快照 */
    @Test
    fun awaitPendingSends_alsoWaitsForSendsQueuedMeanwhile() = runBlocking<Unit> {
        val svc = service()
        svc.send("t", "c")
        launch {
            delay(100)
            svc.send("t", SLOW)
            delay(100)
            gate.complete(Unit)
        }
        svc.awaitPendingSends(5_000)
        assertEquals(2, sent.get())
    }

    @Test
    fun awaitPendingSends_givesUpAfterTimeout() = runBlocking<Unit> {
        val svc = service()
        svc.send("t", "c")
        svc.awaitPendingSends(200)
        assertEquals(0, sent.get())
        gate.complete(Unit)
    }

    private companion object {
        const val SLOW = "slow"
    }
}
