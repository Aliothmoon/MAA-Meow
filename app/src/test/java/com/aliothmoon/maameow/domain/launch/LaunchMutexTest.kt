package com.aliothmoon.maameow.domain.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchMutexTest {

    @Test
    fun tryAcquireSucceedsWhenFree() {
        val mutex = LaunchMutex()
        assertTrue(mutex.tryAcquire("a"))
        assertEquals("a", mutex.current?.requestId)
    }

    @Test
    fun tryAcquireFailsWhenHeldIncludingSameId() {
        val mutex = LaunchMutex()
        assertTrue(mutex.tryAcquire("a"))
        assertFalse(mutex.tryAcquire("b"))
        assertFalse(mutex.tryAcquire("a"))
    }

    @Test
    fun releaseOnlyByHolder() {
        val mutex = LaunchMutex()
        mutex.tryAcquire("a")
        mutex.release("b")
        assertEquals("a", mutex.current?.requestId)
        mutex.release("a")
        assertNull(mutex.current)
    }

    @Test
    fun forceAcquireReplacesHolder() {
        val mutex = LaunchMutex()
        mutex.tryAcquire("a")
        mutex.forceAcquire("b")
        assertEquals("b", mutex.current?.requestId)
    }
}
