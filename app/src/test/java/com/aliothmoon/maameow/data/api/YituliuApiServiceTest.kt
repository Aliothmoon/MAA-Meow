package com.aliothmoon.maameow.data.api

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class YituliuApiServiceTest {

    private val helper = mockk<HttpClientHelper>()
    private val service = YituliuApiService(helper)
    private val headers = slot<Map<String, String>>()

    private fun respond(body: String, code: Int = 200) {
        coEvery { helper.get(any(), any(), capture(headers)) } returns Response.Builder()
            .request(Request.Builder().url("https://backend.yituliu.cn/open-api/operator/info").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("ok")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    @Test
    fun successParsesOperators() = runTest {
        respond(
            """
            {"code":200,"msg":"ok","data":[
              {"id":"char_002_amiya","level":80,"evolvePhase":2,"mainSkillLevel":7,"potentialRank":3,
               "skills":[{"id":"skchr_amiya_1","level":3}],
               "equips":[{"id":"uniequip_002_amiya","type":"X","level":2}]}
            ]}
            """.trimIndent()
        )
        val result = service.fetchOperators("t")
        val operator = (result as YituliuApiService.Result.Success).operators.single()
        assertEquals("char_002_amiya", operator.id)
        assertEquals(80, operator.level)
        assertEquals(2, operator.evolvePhase)
        assertEquals(3, operator.potentialRank)
        assertEquals(3, operator.skills?.single()?.level)
        assertEquals("X", operator.equips?.single()?.type)
    }

    @Test
    fun tokenGoesIntoAuthorizationHeader() = runTest {
        respond("""{"code":200,"data":[]}""")
        service.fetchOperators("secret-token")
        assertEquals(mapOf("Authorization" to "secret-token"), headers.captured)
    }

    @Test
    fun emptyDataIsEmptySuccess() = runTest {
        respond("""{"code":200,"msg":"ok","data":[]}""")
        val result = service.fetchOperators("t")
        assertTrue((result as YituliuApiService.Result.Success).operators.isEmpty())
    }

    @Test
    fun writeOnlyTokenIsReported() = runTest {
        respond("""{"code":20010,"msg":"no read permission"}""")
        assertEquals(YituliuApiService.Result.Failure.WriteOnly, service.fetchOperators("t"))
    }

    @Test
    fun invalidTokenIsReported() = runTest {
        respond("""{"code":20027,"msg":"invalid"}""")
        assertEquals(YituliuApiService.Result.Failure.Invalid, service.fetchOperators("t"))
    }

    @Test
    fun malformedBodyIsInvalid() = runTest {
        respond("not json")
        assertEquals(YituliuApiService.Result.Failure.Invalid, service.fetchOperators("t"))
    }

    @Test
    fun httpErrorIsNetworkError() = runTest {
        respond("""{"code":200,"data":[]}""", code = 500)
        assertEquals(YituliuApiService.Result.Failure.NetworkError, service.fetchOperators("t"))
    }

    @Test
    fun ioFailureIsNetworkError() = runTest {
        coEvery { helper.get(any(), any(), any()) } throws IOException("boom")
        assertEquals(YituliuApiService.Result.Failure.NetworkError, service.fetchOperators("t"))
    }

    @Test
    fun headerRejectionIsNetworkError() = runTest {
        // OkHttp 校验请求头值失败时异常消息带 Token 原文，不能原样往外抛
        coEvery { helper.get(any(), any(), any()) } throws
                IllegalArgumentException("Unexpected char in value: secret-token")
        assertEquals(YituliuApiService.Result.Failure.NetworkError, service.fetchOperators("secret-token"))
    }
}
