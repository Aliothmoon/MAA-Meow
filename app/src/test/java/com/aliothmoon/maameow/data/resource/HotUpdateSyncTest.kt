package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.MaaApiService
import com.aliothmoon.maameow.data.api.MaaApiService.FetchResult
import com.aliothmoon.maameow.domain.service.CoreFilesGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class HotUpdateSyncTest {

    private class FakeGate : CoreFilesGate {
        var idle = CompletableDeferred(Unit)
        var calls = 0
        override suspend fun <T> whenIdle(block: suspend () -> T): T {
            idle.await()
            calls++
            return block()
        }
    }

    private val gate = FakeGate()
    private val changed = FetchResult.Changed("{}", Headers.headersOf())
    private val api = mockk<MaaApiService> {
        coEvery { fetch(any()) } returns FetchResult.NotModified
        every { commit(any(), any()) } returns true
    }

    private fun TestScope.sync() = HotUpdateSync(api, mockk(relaxed = true), lazyOf(gate), backgroundScope) { currentTime }

    @Test
    fun notModified_keepsRevision() = runTest {
        val sync = sync()

        sync.refreshAsync("Official").await()

        assertEquals(0, sync.revision.value)
        assertEquals(0, gate.calls)
    }

    @Test
    fun changedFiles_committedTogetherOnce() = runTest {
        coEvery { api.fetch(MaaApi.STAGE_ACTIVITY_API) } returns changed
        coEvery { api.fetch(MaaApi.TASKS_API) } returns changed
        val sync = sync()

        sync.refreshAsync("Official").await()

        assertEquals(1, gate.calls)
        assertEquals(1, sync.revision.value)
        verify { api.commit(MaaApi.STAGE_ACTIVITY_API, changed) }
        verify { api.commit(MaaApi.TASKS_API, changed) }
    }

    @Test
    fun globalClient_alsoFetchesItsTasks() = runTest {
        sync().refreshAsync("YoStarJP").await()

        coVerify { api.fetch(MaaApi.getGlobalTasksApi("YoStarJP")) }
    }

    @Test
    fun commitWaitsForIdle() = runTest {
        coEvery { api.fetch(MaaApi.TASKS_API) } returns changed
        gate.idle = CompletableDeferred()
        val sync = sync()

        val job = sync.refreshAsync("Official")
        runCurrent()
        assertEquals(0, sync.revision.value)
        verify(exactly = 0) { api.commit(any(), any()) }

        gate.idle.complete(Unit)
        job.await()
        assertEquals(1, sync.revision.value)
    }

    @Test
    fun awaitFresh_givesUpAfterBudgetButRequestKeepsGoing() = runTest {
        val response = CompletableDeferred<FetchResult?>()
        coEvery { api.fetch(MaaApi.TASKS_API) } coAnswers { response.await() }
        val sync = sync()

        sync.awaitFresh("Official", budget = 5.seconds)
        assertEquals(5_000L, currentTime)

        response.complete(changed)
        runCurrent()
        assertEquals(1, sync.revision.value)
    }

    @Test
    fun recentCheck_skipsNetwork() = runTest {
        val sync = sync()

        sync.awaitFresh("Official")
        sync.awaitFresh("Bilibili")

        coVerify(exactly = 1) { api.fetch(MaaApi.STAGE_ACTIVITY_API) }
    }

    @Test
    fun recentCheck_expiresWithClock() = runTest {
        val sync = sync()

        sync.awaitFresh("Official")
        advanceTimeBy(61_000)
        sync.awaitFresh("Official")

        coVerify(exactly = 2) { api.fetch(MaaApi.STAGE_ACTIVITY_API) }
    }

    @Test
    fun failedCommit_isNotMarkedChecked() = runTest {
        coEvery { api.fetch(MaaApi.TASKS_API) } returns changed
        every { api.commit(any(), any()) } returns false
        val sync = sync()

        sync.awaitFresh("Official")
        sync.awaitFresh("Official")

        // 磁盘可能已改了一部分，照样递增；但下次要重拉
        assertEquals(2, sync.revision.value)
        coVerify(exactly = 2) { api.fetch(MaaApi.TASKS_API) }
    }

    @Test
    fun otherClient_waitsForPendingCommitBeforeFetching() = runTest {
        coEvery { api.fetch(MaaApi.TASKS_API) } returns changed
        gate.idle = CompletableDeferred()
        val sync = sync()

        val official = sync.refreshAsync("Official")
        val jp = sync.refreshAsync("YoStarJP")
        runCurrent()
        coVerify(exactly = 0) { api.fetch(MaaApi.getGlobalTasksApi("YoStarJP")) }

        gate.idle.complete(Unit)
        official.await()
        jp.await()
        coVerify(exactly = 1) { api.fetch(MaaApi.getGlobalTasksApi("YoStarJP")) }
    }

    @Test
    fun concurrentCallers_shareOneRequest() = runTest {
        val response = CompletableDeferred<FetchResult?>()
        coEvery { api.fetch(MaaApi.STAGE_ACTIVITY_API) } coAnswers { response.await() }
        val sync = sync()

        val a = sync.refreshAsync("Official")
        val b = sync.refreshAsync("Official")
        assertTrue(a === b)
        response.complete(FetchResult.NotModified)
        a.await()

        coVerify(exactly = 1) { api.fetch(MaaApi.STAGE_ACTIVITY_API) }
    }
}
