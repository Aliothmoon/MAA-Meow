package com.aliothmoon.maameow.manager

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeoutException

/** 定时启动先连提权进程：连不上要拿到原因，不能抛出去把管线当成被取消 */
class RemoteServiceManagerConnectTest {

    @Before
    fun setUp() {
        // RemoteAccessCoordinator 初始化就会取快照，两个后端得先于它桩掉
        mockkObject(ShizukuManager, RootManager)
        every { ShizukuManager.isAvailable() } returns true
        every { ShizukuManager.isGranted() } returns true
        every { RootManager.isAvailable() } returns false
        every { RootManager.isGranted() } returns false
        mockkObject(RemoteServiceManager)
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun connected_returnsNull() = runBlocking {
        coEvery { RemoteServiceManager.getInstance(any()) } returns mockk()

        assertNull(RemoteServiceManager.awaitConnected())
    }

    @Test
    fun spawnFailure_isReturned() = runBlocking {
        val failure = TimeoutException("binder not attached within 10000ms; launcher log tail: x")
        coEvery { RemoteServiceManager.getInstance(any()) } throws failure

        // 跨 withContext 抛出时协程栈恢复会复制一份，比内容不比实例
        val returned = RemoteServiceManager.awaitConnected()
        assertTrue(returned is TimeoutException)
        assertEquals(failure.message, returned?.message)
    }

    @Test
    fun callerWaitTimeout_isReturnedNotRethrown() = runBlocking {
        coEvery { RemoteServiceManager.getInstance(any()) } coAnswers {
            withTimeout(1) { delay(1_000) }
            error("unreachable")
        }

        assertTrue(RemoteServiceManager.awaitConnected() is TimeoutCancellationException)
    }

    @Test
    fun callerCancellation_propagates() = runBlocking {
        coEvery { RemoteServiceManager.getInstance(any()) } coAnswers { awaitCancellation() }
        var returned = false

        val job = launch {
            RemoteServiceManager.awaitConnected()
            returned = true
        }
        yield()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(false, returned)
    }

    @Test
    fun shortCause_dropsLauncherLogTail() {
        val wrapped = IllegalStateException("launcher exited early code=1; launcher log tail: a | b")

        assertEquals("launcher exited early code=1", RemoteServiceManager.shortCause(wrapped))
    }

    @Test
    fun shortCause_fallsBackToClassName() {
        assertEquals("IllegalStateException", RemoteServiceManager.shortCause(IllegalStateException()))
    }
}
