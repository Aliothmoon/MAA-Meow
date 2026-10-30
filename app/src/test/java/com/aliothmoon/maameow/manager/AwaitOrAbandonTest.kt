package com.aliothmoon.maameow.manager

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/** 提权侧调用卡死时只放弃等待：不能陪着卡住的线程一起等 */
class AwaitOrAbandonTest {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun returnsResultWhenCallFinishesInTime() = runBlocking {
        val result = awaitOrAbandon<Int, Exception>(scope, 1_000, { 42 }, {}) {
            error("not stuck")
        }
        assertEquals(42, result)
    }

    @Test
    fun stuckCallIsAbandonedOnceWithoutWaitingForIt() = runBlocking {
        val release = CountDownLatch(1)
        var stuck = 0
        try {
            // 要是等那条卡住的线程，这里会一直挂到外层超时
            withTimeout(5_000) {
                awaitOrAbandon(scope, 200, { release.await() }, {}) {
                    stuck++
                    IllegalStateException("stuck")
                }
            }
            fail("expected the stuck verdict to be thrown")
        } catch (e: IllegalStateException) {
            assertEquals("stuck", e.message)
        } finally {
            release.countDown()
        }
        assertEquals(1, stuck)
    }

    @Test
    fun stuckCallIsStillHandledAfterCallerIsCancelled() = runBlocking {
        val release = CountDownLatch(1)
        val stuck = AtomicInteger()
        val abandoned = CompletableDeferred<IllegalStateException>()
        val verdict = IllegalStateException("stuck")
        try {
            // 不派发：同步跑到 awaitOrAbandon 挂起，取消一定落在它里面
            val caller = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                awaitOrAbandon(scope, 300, { release.await() }, { abandoned.complete(it) }) {
                    stuck.incrementAndGet()
                    verdict
                }
            }
            caller.cancelAndJoin()
            assertSame(verdict, withTimeout(5_000) { abandoned.await() })
        } finally {
            release.countDown()
        }
        assertEquals(1, stuck.get())
    }

    @Test
    fun failureOfTheCallPropagates() = runBlocking {
        try {
            awaitOrAbandon<Unit, Exception>(
                scope, 1_000, { throw IllegalStateException("dead") }, {},
            ) { error("not stuck") }
            fail("expected the call failure to propagate")
        } catch (e: IllegalStateException) {
            assertEquals("dead", e.message)
        }
    }
}
