package com.aliothmoon.maameow.data.api

import com.aliothmoon.maameow.data.api.MirrorChyanCdkStatus.State
import com.aliothmoon.maameow.data.model.update.UpdateError
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorChyanCdkStatusTest {

    private val now = 1_800_000_000L
    private val cdk = MutableStateFlow("A".repeat(24))
    private val expiresAt = MutableStateFlow(0L)
    private val settings = mockk<AppSettingsManager> {
        every { mirrorChyanCdk } returns cdk
        every { mirrorChyanCdkExpiresAt } returns expiresAt
        coEvery { setMirrorChyanCdkExpiresAt(any()) } answers { expiresAt.value = firstArg() }
    }
    private val status = MirrorChyanCdkStatus(settings) { now }

    @Test
    fun `有效且返回到期时间时落盘`() = runTest {
        status.onResponse(cdk.value, 0, now + 86_400)
        assertEquals(State.Valid, status.state.value)
        assertEquals(now + 86_400, expiresAt.value)
    }

    @Test
    fun `有效但没返回到期时间沿用上次`() = runTest {
        expiresAt.value = now + 100
        status.onResponse(cdk.value, 0, null)
        assertEquals(State.Valid, status.state.value)
        assertEquals(now + 100, expiresAt.value)
    }

    @Test
    fun `沿用的到期时间已过算过期`() = runTest {
        expiresAt.value = now - 1
        status.onResponse(cdk.value, 0, null)
        assertEquals(State.Expired, status.state.value)
    }

    @Test
    fun `过期标记 1 不当作时间`() = runTest {
        expiresAt.value = 1
        status.onResponse(cdk.value, 0, null)
        assertEquals(State.Valid, status.state.value)
        assertEquals(0L, expiresAt.value)
    }

    @Test
    fun `过期但没给时间记为 1`() = runTest {
        expiresAt.value = now + 100
        status.onResponse(cdk.value, 7001, null)
        assertEquals(State.Expired, status.state.value)
        assertEquals(1L, expiresAt.value)
    }

    @Test
    fun `无效清掉到期时间`() = runTest {
        expiresAt.value = now + 100
        status.onResponse(cdk.value, 7002, null)
        assertEquals(State.Invalid, status.state.value)
        assertEquals(0L, expiresAt.value)
    }

    @Test
    fun `配额等业务错误保留到期时间`() = runTest {
        expiresAt.value = now + 100
        status.onResponse(cdk.value, 7003, null)
        assertEquals(
            State.Failed(UpdateError.MirrorchyanBizError.ResourceQuotaExhausted),
            status.state.value
        )
        assertEquals(now + 100, expiresAt.value)
    }

    @Test
    fun `网络失败不降级已验证的 CDK`() = runTest {
        status.onResponse(cdk.value, 0, null)
        status.onNetworkFailure(cdk.value)
        assertEquals(State.Valid, status.state.value)

        status.reset()
        status.onNetworkFailure(cdk.value)
        assertEquals(State.Failed(), status.state.value)
    }

    @Test
    fun `非当前 CDK 的请求不回写`() = runTest {
        val stale = "B".repeat(24)
        assertFalse(status.begin(stale))
        status.onResponse(stale, 7002, null)
        assertEquals(State.Unknown, status.state.value)
        assertFalse(status.checking.value)
    }

    @Test
    fun `并发请求全部结束才退出验证中`() {
        assertTrue(status.begin(cdk.value))
        assertTrue(status.begin(cdk.value))
        status.end()
        assertTrue(status.checking.value)
        status.end()
        assertFalse(status.checking.value)
    }
}
