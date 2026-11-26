package com.aliothmoon.maameow.third.wrappers

import android.os.IBinder
import android.os.IInterface
import android.os.SystemClock
import com.aliothmoon.maameow.third.Ln
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import java.lang.reflect.InvocationTargetException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PowerManagerClientErrorTest {

    @Before
    fun setUp() {
        mockkStatic(Ln::class, SystemClock::class)
        every { SystemClock.uptimeMillis() } returns 1234L
        every { Ln.w(any()) } just runs
        every { Ln.e(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkStatic(Ln::class, SystemClock::class)
    }

    @Test
    fun acquireProviderAbstractMethodErrorIsClientSide() {
        val cause = clientProviderError()
        assertTrue(PowerManager.isClientSideProviderError(cause))
        assertTrue(PowerManager.isClientSideProviderError(InvocationTargetException(cause)))
    }

    @Test
    fun otherFailuresAreNotClientSide() {
        assertFalse(PowerManager.isClientSideProviderError(null))
        assertFalse(PowerManager.isClientSideProviderError(IllegalStateException("no")))
        assertFalse(PowerManager.isClientSideProviderError(InvocationTargetException(RemoteException())))
    }

    @Test
    fun wakeUpWithDisplayIdPassesDisplayIdAndHandlesClientError() {
        val stub = FakePowerBinder()
        val pm = PowerManager(stub)

        assertTrue(pm.wakeUp(8))
        assertEquals(8, stub.lastDisplayId)

        stub.failure = clientProviderError()
        assertTrue(pm.wakeUp(9))
        assertEquals(9, stub.lastDisplayId)

        stub.failure = SecurityException("denied")
        assertFalse(pm.wakeUp(10))
    }

    private fun clientProviderError() = AbstractMethodError(
        "abstract method \"android.content.IContentProvider " +
            "android.content.ContentResolver.acquireProvider" +
            "(android.content.Context, java.lang.String)\"",
    )

    private class FakePowerBinder : IInterface {
        var lastDisplayId: Int = -1
        var failure: Throwable? = null

        override fun asBinder(): IBinder? = null

        @Suppress("unused")
        fun wakeUpWithDisplayId(
            time: Long,
            reason: Int,
            details: String,
            opPackageName: String,
            displayId: Int,
        ) {
            lastDisplayId = displayId
            failure?.let { throw it }
        }
    }

    private class RemoteException : Exception()
}
