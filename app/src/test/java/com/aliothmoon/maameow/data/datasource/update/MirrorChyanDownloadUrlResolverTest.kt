package com.aliothmoon.maameow.data.datasource.update

import com.aliothmoon.maameow.data.api.MirrorChyanApiClient
import com.aliothmoon.maameow.data.api.model.MirrorChyanData
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorChyanDownloadUrlResolverTest {

    private val appSettings = mockk<AppSettingsManager> {
        every { mirrorChyanCdk } returns MutableStateFlow("cdk")
    }

    private fun apiReturning(url: String?): MirrorChyanApiClient = mockk {
        coEvery { getLatest(any(), any(), any()) } returns
                Result.success(MirrorChyanData(versionName = "0.23.1", url = url))
    }

    @Test
    fun appEmptyUrlIsFailure() {
        val result = runBlocking {
            MirrorChyanAppDownloadUrlResolver(apiReturning(null), appSettings)
                .resolve("0.23.1", UpdateChannel.STABLE)
        }
        assertTrue(result.exceptionOrNull() is LocalizedException)
    }

    @Test
    fun resourceEmptyUrlIsFailure() {
        val result = runBlocking {
            MirrorChyanResourceDownloadUrlResolver(apiReturning(null), appSettings)
                .resolve("2026-10-01")
        }
        assertTrue(result.exceptionOrNull() is LocalizedException)
    }

    @Test
    fun appUrlPassesThrough() {
        val result = runBlocking {
            MirrorChyanAppDownloadUrlResolver(apiReturning(URL), appSettings)
                .resolve("0.23.1", UpdateChannel.STABLE)
        }
        assertEquals(URL, result.getOrThrow())
    }

    private companion object {
        const val URL = "https://example.invalid/app.apk"
    }
}
