package com.aliothmoon.maameow.data.api

import com.aliothmoon.maameow.data.api.model.MirrorChyanResponse
import com.aliothmoon.maameow.data.datasource.update.GitHubAppDownloadUrlResolver
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class UpdateApiThreadingTest {

    @Test
    fun mirrorSlowBodyDoesNotBlockCaller() {
        val version = assertNonBlockingBody(MIRROR_RESPONSE) { http ->
            MirrorChyanApiClient(http).getLatest(TEST_URL, emptyMap()).getOrThrow().versionName
        }
        assertEquals("0.23.1", version)
    }

    @Test
    fun githubSlowBodyDoesNotBlockCaller() {
        val json = """{"tag_name":"v0.23.1","assets":[{"name":"maameow-universal.apk","browser_download_url":"$TEST_URL"}]}"""
        val url = assertNonBlockingBody(json) { http ->
            GitHubAppDownloadUrlResolver(http).resolve("0.23.1", UpdateChannel.STABLE).getOrThrow()
        }
        assertEquals(TEST_URL, url)
    }

    @Test
    fun entitySlowBodyDoesNotBlockCaller() {
        val response = assertNonBlockingBody(MIRROR_RESPONSE) { http ->
            http.getEntity<MirrorChyanResponse>(TEST_URL)
        }
        assertEquals("0.23.1", response.data?.versionName)
    }

    @Test
    fun githubClosesErrorResponse() {
        val body = DelayedBody("{}").apply { release.countDown() }
        withResponse(body, code = 404) { http ->
            val result = runBlocking {
                GitHubAppDownloadUrlResolver(http).resolve("0.23.1", UpdateChannel.STABLE)
            }
            assertTrue(result.isFailure)
        }
        assertTrue(body.closed.get())
    }

    @Test
    fun mirrorServiceFailureKeepsCodeAndClosesResponse() {
        val body = DelayedBody("{}").apply { release.countDown() }
        withResponse(body, code = 500) { http ->
            val result = runBlocking { MirrorChyanApiClient(http).getLatest(TEST_URL, emptyMap()) }
            assertEquals(500, (result.exceptionOrNull() as MirrorChyanBizException).bizCode)
        }
        assertTrue(body.closed.get())
    }

    @Test
    fun mirrorMalformedResponseKeepsUnknownError() {
        val body = DelayedBody("invalid json").apply { release.countDown() }
        withResponse(body) { http ->
            val result = runBlocking { MirrorChyanApiClient(http).getLatest(TEST_URL, emptyMap()) }
            assertEquals(-1, (result.exceptionOrNull() as MirrorChyanBizException).bizCode)
        }
        assertTrue(body.closed.get())
    }

    @Test
    fun mirrorDoesNotConvertCancellationToFailure() {
        val http = cancelledRequest()
        assertThrows(CancellationException::class.java) {
            runBlocking { MirrorChyanApiClient(http).getLatest(TEST_URL, emptyMap()) }
        }
    }

    @Test
    fun githubDoesNotConvertCancellationToFailure() {
        val http = cancelledRequest()
        assertThrows(CancellationException::class.java) {
            runBlocking { GitHubAppDownloadUrlResolver(http).resolve("0.23.1", UpdateChannel.STABLE) }
        }
    }

    private fun cancelledRequest(): HttpClientHelper = mockk {
        coEvery { get(any(), any(), any()) } throws CancellationException("request cancelled")
    }

    private fun <T> assertNonBlockingBody(
        json: String,
        read: suspend (HttpClientHelper) -> T,
    ): T {
        val body = DelayedBody(json)
        return withResponse(body) { http ->
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { caller ->
                val callerThread = runBlocking { withContext(caller) { Thread.currentThread() } }
                val scope = CoroutineScope(SupervisorJob() + caller)
                val request = scope.async { read(http) }
                try {
                    assertTrue("Response body read did not start", body.started.await(5, TimeUnit.SECONDS))
                    assertNotSame("Response body blocked the caller thread", callerThread, body.reader.get())
                    // 正文被阻塞时，调用线程仍能处理下一条消息
                    assertTrue(runBlocking { withTimeout(5_000) { withContext(caller) { true } } })
                    body.release.countDown()
                    runBlocking { withTimeout(5_000) { request.await() } }.also {
                        assertTrue("Response body was not closed", body.closed.get())
                    }
                } finally {
                    body.release.countDown()
                    scope.cancel()
                    runBlocking { withTimeout(5_000) { request.join() } }
                }
            }
        }
    }

    private fun <T> withResponse(
        body: ResponseBody,
        code: Int = 200,
        block: (HttpClientHelper) -> T,
    ): T {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test response")
                .body(body)
                .build()
        }.build()
        return try {
            block(HttpClientHelper(client))
        } finally {
            client.dispatcher.cancelAll()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private class DelayedBody(content: String) : ResponseBody() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reader = AtomicReference<Thread>()
        val closed = AtomicBoolean(false)
        private val contentBuffer = Buffer().writeUtf8(content)
        private val length = contentBuffer.size
        private val buffered = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reader.compareAndSet(null, Thread.currentThread())
                started.countDown()
                if (!release.await(5, TimeUnit.SECONDS)) throw IOException("Response body was not released")
                return contentBuffer.read(sink, byteCount)
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {
                closed.set(true)
                contentBuffer.close()
            }
        }.buffer()

        override fun contentType() = "application/json".toMediaType()
        override fun contentLength(): Long = length
        override fun source(): BufferedSource = buffered
    }

    private companion object {
        const val TEST_URL = "https://example.invalid/latest"
        const val MIRROR_RESPONSE = """{"code":0,"data":{"version_name":"0.23.1"}}"""
    }
}
