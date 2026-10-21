package com.aliothmoon.maameow.presentation.viewmodel

import android.os.IBinder
import android.view.Surface
import com.aliothmoon.maameow.RemoteService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PreviewSurfaceHandoverTest {

    /** 一个特权进程：binder 身份各不相同 */
    private fun service(): RemoteService = mockk(relaxed = true) {
        every { asBinder() } returns mockk<IBinder>()
    }

    @Test
    fun `a surface that arrives before the service is sent once it connects`() {
        val handover = PreviewSurfaceHandover()
        val surface = mockk<Surface>()
        val service = service()

        handover.attach(surface, service = null)
        handover.onServiceConnected(service)

        verify(exactly = 1) { service.setMonitorSurface(surface) }
        assertEquals(0, handover.surfaceEpoch.value)
    }

    @Test
    fun `a surface already handed to one service is not sent to the next`() {
        val handover = PreviewSurfaceHandover()
        val surface = mockk<Surface>()
        val second = service()
        handover.attach(surface, service())

        handover.onServiceConnected(second)

        verify(exactly = 0) { second.setMonitorSurface(any()) }
        assertEquals(1, handover.surfaceEpoch.value)
    }

    @Test
    fun `the replacement surface goes to the new service`() {
        val handover = PreviewSurfaceHandover()
        val old = mockk<Surface>()
        val second = service()
        handover.attach(old, service())
        handover.onServiceConnected(second)

        // UI 按 surfaceEpoch 重建 SurfaceView：旧的先销毁，新的再上报
        val replacement = mockk<Surface>()
        assertSame(old, handover.detach(second))
        handover.attach(replacement, second)

        verify(exactly = 1) { second.setMonitorSurface(null) }
        verify(exactly = 1) { second.setMonitorSurface(replacement) }
        assertEquals(1, handover.surfaceEpoch.value)
    }

    @Test
    fun `reconnecting to the same service keeps the surface`() {
        val handover = PreviewSurfaceHandover()
        val surface = mockk<Surface>()
        val service = service()
        handover.attach(surface, service)

        handover.onServiceConnected(service)

        verify(exactly = 1) { service.setMonitorSurface(surface) }
        assertEquals(0, handover.surfaceEpoch.value)
    }

    @Test
    fun `reconnecting without a surface asks for nothing`() {
        val handover = PreviewSurfaceHandover()
        val service = service()

        handover.onServiceConnected(service)

        verify(exactly = 0) { service.setMonitorSurface(any()) }
        assertEquals(0, handover.surfaceEpoch.value)
    }

    private fun recordingService(sent: MutableList<Surface?>): RemoteService = service().also {
        every { it.setMonitorSurface(any()) } answers { sent += firstArg<Surface?>() }
    }

    @Test
    fun `rapid create and destroy reach the service in call order`() {
        val handover = PreviewSurfaceHandover()
        val sent = mutableListOf<Surface?>()
        val service = recordingService(sent)
        val surfaces = List(50) { mockk<Surface>() }

        surfaces.forEach { surface ->
            handover.attach(surface, service)
            assertSame(surface, handover.detach(service))
        }

        assertEquals(surfaces.flatMap { listOf(it, null) }, sent)
        assertEquals(0, handover.surfaceEpoch.value)
    }

    @Test
    fun `a surface destroyed while disconnected is not sent on reconnect`() {
        val handover = PreviewSurfaceHandover()
        val sent = mutableListOf<Surface?>()
        val service = recordingService(sent)
        val surface = mockk<Surface>()

        handover.attach(surface, service = null)
        assertSame(surface, handover.detach(service = null))
        handover.onServiceConnected(service)

        assertEquals(emptyList<Surface?>(), sent)
    }

    @Test
    fun `a service swap during rapid switching sends later surfaces only to the new service`() {
        val handover = PreviewSurfaceHandover()
        val oldSent = mutableListOf<Surface?>()
        val newSent = mutableListOf<Surface?>()
        val old = recordingService(oldSent)
        val new = recordingService(newSent)
        val first = mockk<Surface>()
        val second = mockk<Surface>()
        val replacement = mockk<Surface>()

        handover.attach(first, old)
        handover.detach(old)
        handover.attach(second, old)
        handover.onServiceConnected(new)
        assertSame(second, handover.detach(new))
        handover.attach(replacement, new)

        assertEquals(listOf(first, null, second), oldSent)
        assertEquals(listOf(null, replacement), newSent)
        assertEquals(1, handover.surfaceEpoch.value)
    }

    @Test
    fun `a failed handover is retried on the next connection`() {
        val handover = PreviewSurfaceHandover()
        val surface = mockk<Surface>()
        val dead = service().also { every { it.setMonitorSurface(any()) } throws RuntimeException("dead") }
        val next = service()
        handover.attach(surface, dead)

        handover.onServiceConnected(next)

        verify(exactly = 1) { next.setMonitorSurface(surface) }
        assertEquals(0, handover.surfaceEpoch.value)
    }
}
