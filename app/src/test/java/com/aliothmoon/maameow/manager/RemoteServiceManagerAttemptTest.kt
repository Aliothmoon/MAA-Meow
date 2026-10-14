package com.aliothmoon.maameow.manager

import android.os.IBinder
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.domain.models.RemoteBackend
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class RemoteServiceManagerAttemptTest {

    private val callbacks = CopyOnWriteArrayList<RemoteServiceConnectorBackend.Callbacks>()
    private val rootGranted = AtomicBoolean(true)

    @Before
    fun setUp() {
        mockkObject(ShizukuManager, RootManager)
        every { ShizukuManager.isAvailable() } returns false
        every { ShizukuManager.isGranted() } returns false
        every { RootManager.isAvailable() } returns true
        every { RootManager.isGranted() } returns true
        mockkObject(RemoteAccessCoordinator, RootRemoteServiceConnector)
        val access = mockk<RemoteAccessState>()
        every { access.configuredBackend } returns RemoteBackend.ROOT
        every { RemoteAccessCoordinator.refresh() } returns access
        every { RemoteAccessCoordinator.isGranted(RemoteBackend.ROOT) } answers { rootGranted.get() }
        every { RootRemoteServiceConnector.worstCaseConnectMs } returns 60_000L
        justRun { RootRemoteServiceConnector.connect(capture(callbacks)) }
        justRun { RootRemoteServiceConnector.disconnect(any()) }
        RemoteServiceManager.unbind()
    }

    @After
    fun tearDown() {
        RemoteServiceManager.unbind()
        unmockkAll()
    }

    @Test
    fun callbacksFromPreviousAttempt_doNotReplaceOrDropCurrentConnection() {
        RemoteServiceManager.bind()
        val staleCallbacks = callbacks.single()
        RemoteServiceManager.unbind()

        RemoteServiceManager.bind()
        val currentCallbacks = callbacks.last()
        val (currentBinder, currentService) = serviceBinder()
        currentCallbacks.onConnected(RemoteBackend.ROOT, currentBinder)
        assertSame(currentService, RemoteServiceManager.getInstanceOrNull())

        val (staleBinder, _) = serviceBinder()
        staleCallbacks.onConnected(RemoteBackend.ROOT, staleBinder)
        staleCallbacks.onError(RemoteBackend.ROOT, IllegalStateException("stale"))
        staleCallbacks.onDisconnected(RemoteBackend.ROOT)

        assertSame(currentService, RemoteServiceManager.getInstanceOrNull())
        verify(exactly = 0) { staleBinder.linkToDeath(any(), any()) }
    }

    @Test
    fun permissionRevokedDuringLaunch_disconnectsAndRejectsLateCallbacks() {
        RemoteServiceManager.bind()
        val staleCallbacks = callbacks.single()

        rootGranted.set(false)
        RemoteServiceManager.bind()

        val (staleBinder, _) = serviceBinder()
        staleCallbacks.onConnected(RemoteBackend.ROOT, staleBinder)

        assertNull(RemoteServiceManager.getInstanceOrNull())
        assertTrue(RemoteServiceManager.state.value is RemoteServiceManager.ServiceState.Error)
        verify(atLeast = 1) { RootRemoteServiceConnector.disconnect(any()) }
        verify(exactly = 0) { staleBinder.linkToDeath(any(), any()) }
    }

    private fun serviceBinder(): Pair<IBinder, RemoteService> {
        val binder = mockk<IBinder>(relaxed = true)
        val service = mockk<RemoteService>(relaxed = true)
        every { binder.queryLocalInterface(any()) } returns service
        every { binder.isBinderAlive } returns true
        justRun { binder.linkToDeath(any(), any()) }
        every { binder.unlinkToDeath(any(), any()) } returns true
        return binder to service
    }
}
