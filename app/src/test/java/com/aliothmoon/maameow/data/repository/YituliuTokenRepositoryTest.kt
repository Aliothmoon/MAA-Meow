package com.aliothmoon.maameow.data.repository

import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class YituliuTokenRepositoryTest {

    private val profiles = MutableStateFlow(
        listOf(
            TaskProfile(id = "a", name = "配置-1", chain = emptyList()),
            TaskProfile(id = "b", name = "配置-2", chain = emptyList()),
        ),
    )

    private val chainState = mockk<TaskChainState> {
        every { profileId } returns MutableStateFlow("a")
        every { isLoaded } returns MutableStateFlow(true)
        every { profileDeleted } returns MutableSharedFlow()
        every { this@mockk.profiles } returns this@YituliuTokenRepositoryTest.profiles
    }

    private val shared = MutableStateFlow("shared-token")
    private val perProfile = MutableStateFlow(false)

    private val settings = mockk<AppSettingsManager> {
        coEvery { awaitLoaded() } just runs
        every { yituliuOpenApiToken } returns this@YituliuTokenRepositoryTest.shared
        every { yituliuTokenPerProfile } returns this@YituliuTokenRepositoryTest.perProfile
        coEvery { setYituliuTokenPerProfile(any()) } answers {
            this@YituliuTokenRepositoryTest.perProfile.value = firstArg()
        }
    }

    private val repo = YituliuTokenRepository(FakePreferencesDataStore(), chainState, settings)

    private suspend fun awaitProfileTokens(count: Int) =
        withTimeout(5_000) { repo.profileTokens.first { it.size == count } }

    @Test
    fun sharedModeUsesGlobalTokenForEveryProfile() = runBlocking {
        repo.setForProfile("b", "token-b")
        awaitProfileTokens(1)

        assertEquals(YituliuTokenRepository.Resolved("shared-token", perProfile = false), repo.resolve("a"))
        assertEquals(YituliuTokenRepository.Resolved("shared-token", perProfile = false), repo.resolve("b"))
    }

    @Test
    fun enablingCarriesGlobalTokenToActiveProfileOnly() = runBlocking {
        repo.setPerProfile(true)

        assertEquals(YituliuTokenRepository.Resolved("shared-token", perProfile = true), repo.resolve("a"))
        assertEquals(YituliuTokenRepository.Resolved("", perProfile = true), repo.resolve("b"))
    }

    @Test
    fun enablingKeepsExistingProfileToken() = runBlocking {
        repo.setForProfile("a", "token-a")
        awaitProfileTokens(1)

        repo.setPerProfile(true)

        assertEquals("token-a", repo.resolve("a").token)
    }

    @Test
    fun profileTokensAreTrimmedAndKeptApart() = runBlocking {
        perProfile.value = true
        repo.setForProfile("a", " token-a ")
        repo.setForProfile("b", "token-b")
        awaitProfileTokens(2)

        assertEquals("token-a", repo.resolve("a").token)
        assertEquals("token-b", repo.resolve("b").token)
    }

    @Test
    fun deletedProfileIsNotWritten() = runBlocking {
        withTimeout(5_000) { repo.profileTokens.first { true } }
        repo.setForProfile("gone", "token")
        repo.setForProfile("a", "token-a")
        awaitProfileTokens(1)

        assertEquals(mapOf("a" to "token-a"), repo.profileTokens.value)
    }
}
