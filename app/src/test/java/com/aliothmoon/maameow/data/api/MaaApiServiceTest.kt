package com.aliothmoon.maameow.data.api

import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.config.MaaPathConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * 锁住两条不变量：
 * 1. 条件请求头与正文缓存同键（api 路径），换源不会读到别人写的正文
 * 2. 304 但正文缺失时当场无条件重取，本轮就要有数据，且不打转
 */
class MaaApiServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val api = MaaApi.STAGE_ACTIVITY_API
    private val primary = MaaApi.MAA_API
    private val backup = MaaApi.MAA_API_BACKUP

    /** 模拟持久化的 validator，跨 service 实例存活 */
    private val storedETags = mutableMapOf<String, String>()

    /** 逐次记录 (url, 条件请求头) */
    private val calls = mutableListOf<Pair<String, Map<String, String>>>()

    private lateinit var cacheRoot: File
    private lateinit var httpClient: HttpClientHelper
    private lateinit var eTagCache: ETagCacheManager
    private lateinit var pathConfig: MaaPathConfig

    private var responder: (String, Map<String, String>) -> Response = { url, _ -> ok(url, "") }

    @Before
    fun setUp() {
        cacheRoot = tempFolder.newFolder("cache")
        storedETags.clear()
        calls.clear()

        eTagCache = mockk()
        every { eTagCache.getConditionalHeader(any()) } answers {
            storedETags[firstArg<String>()]
                ?.let { mapOf(ETagCacheManager.IF_NONE_MATCH to it) }
                ?: emptyMap()
        }
        every { eTagCache.updateConditionalHeaders(any(), any()) } answers {
            val key = firstArg<String>()
            secondArg<Headers>()[ETagCacheManager.ETAG]?.let { storedETags[key] = it }
            Unit
        }
        every { eTagCache.invalidateKey(any()) } answers {
            storedETags.remove(firstArg<String>())
            Unit
        }

        httpClient = mockk()
        coEvery { httpClient.get(any(), any(), any()) } answers {
            val url = firstArg<String>()
            val headers = thirdArg<Map<String, String>>()
            calls += url to headers
            responder(url, headers)
        }

        pathConfig = mockk()
        every { pathConfig.cacheDir } returns cacheRoot.absolutePath
    }

    /** 每次调用当作一个新进程：内存缓存清空，validator 与磁盘正文保留 */
    private fun newService() = MaaApiService(
        context = mockk(relaxed = true),
        httpClient = httpClient,
        eTagCache = eTagCache,
        pathConfig = pathConfig,
    )

    @Test
    fun validatorIsKeyedByApiPathNotUrl() = runBlocking {
        responder = { url, _ -> ok(url, "V1", etag = "\"e1\"") }

        assertEquals("V1", newService().getStageActivity())
        assertEquals(setOf(api), storedETags.keys)
    }

    @Test
    fun sourceSwitchDoesNotServeOtherSourcesBody() = runBlocking {
        val service = newService()

        // 主源写入 V1
        responder = { url, _ -> ok(url, "V1", etag = "\"e1\"") }
        assertEquals("V1", service.getStageActivity())

        // 主源不可达，回落备源，备源滞后一版
        responder = { url, _ ->
            if (url.startsWith(primary)) throw IOException("primary down")
            ok(url, "V0", etag = "\"e0\"")
        }
        assertEquals("V0", service.getStageActivity())

        // 主源恢复：内容仍是 V1，只认自己的 e1
        calls.clear()
        responder = { url, headers ->
            when {
                !url.startsWith(primary) -> ok(url, "V0", etag = "\"e0\"")
                headers[ETagCacheManager.IF_NONE_MATCH] == "\"e1\"" -> notModified(url)
                else -> ok(url, "V1", etag = "\"e1\"")
            }
        }

        // 带出去的必须是备源写入时留下的 e0，否则就是一个路径存了两份 validator
        assertEquals("V1", service.getStageActivity())
        assertEquals("\"e0\"", calls.first().second[ETagCacheManager.IF_NONE_MATCH])
    }

    @Test
    fun missingBodyOn304RefetchesImmediately() = runBlocking {
        responder = { url, _ -> ok(url, "V1", etag = "\"e1\"") }
        assertEquals("V1", newService().getStageActivity())

        // 清理软件删掉外部缓存目录，validator 在 SharedPreferences 里活着
        cacheRoot.deleteRecursively()
        calls.clear()
        responder = { url, headers ->
            if (headers.containsKey(ETagCacheManager.IF_NONE_MATCH)) {
                notModified(url)
            } else {
                ok(url, "V2", etag = "\"e2\"")
            }
        }

        assertEquals("V2", newService().getStageActivity())
        // 重取必须由同一个源当场发出，而不是靠备源兜底
        assertEquals(listOf(primary, primary), calls.map { it.first.removeSuffix(api) })
        assertEquals(emptyMap<String, String>(), calls[1].second)
        assertEquals("\"e2\"", storedETags[api])
    }

    @Test
    fun unconditional304DoesNotLoop() = runBlocking {
        // validator 在但正文不在，且服务端一律回 304
        storedETags[api] = "\"stale\""
        responder = { url, _ -> notModified(url) }

        assertNull(newService().getStageActivity())
        // 两个源各一次条件请求加一次无条件重取，到此为止
        assertEquals(4, calls.size)
        assertEquals(listOf(primary, primary, backup, backup), calls.map { it.first.removeSuffix(api) })
    }

    private fun ok(url: String, body: String, etag: String? = null): Response =
        response(url, 200, "OK", body, etag)

    private fun notModified(url: String): Response = response(url, 304, "Not Modified", "", null)

    private fun response(
        url: String,
        code: Int,
        message: String,
        body: String,
        etag: String?,
    ): Response = Response.Builder()
        .request(Request.Builder().url(url).build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(message)
        .body(body.toResponseBody("application/json".toMediaType()))
        .apply { etag?.let { header(ETagCacheManager.ETAG, it) } }
        .build()
}
