package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.YituliuApiService
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.YituliuTokenRepository
import com.aliothmoon.maameow.domain.service.CopilotOperBoxAssist
import com.aliothmoon.maameow.presentation.viewmodel.YituliuSettingsViewModel.Companion.SHARED
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YituliuSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val shared = MutableStateFlow("old")
    private val api = mockk<YituliuApiService>()

    private val tokens = mockk<YituliuTokenRepository> {
        every { perProfile } returns MutableStateFlow(false)
        every { sharedToken } returns this@YituliuSettingsViewModelTest.shared
        every { profileTokens } returns MutableStateFlow(emptyMap())
        coEvery { setPerProfile(any()) } just runs
    }

    private fun viewModel() = YituliuSettingsViewModel(
        appSettingsManager = mockk {
            every { reportToYituliu } returns MutableStateFlow(true)
            every { operBoxUseYituliuApi } returns MutableStateFlow(false)
        },
        taskChainState = mockk {
            every { profiles } returns MutableStateFlow(emptyList())
            every { profileId } returns MutableStateFlow("a")
        },
        tokenRepository = tokens,
        api = api,
        operBoxAssist = mockk { every { state } returns flowOf(CopilotOperBoxAssist.State()) },
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun staleResultResetsVerifyingButton() = runTest(dispatcher) {
        // 全局 Token 异步落盘：点验证时还是旧值，请求期间新值才落地
        coEvery { api.fetchOperators("old") } coAnswers {
            shared.value = "new"
            YituliuApiService.Result.Success(emptyList())
        }
        val vm = viewModel()

        vm.verify(SHARED)
        advanceUntilIdle()

        assertNull(vm.verifyStates.value[SHARED])
    }

    @Test
    fun togglingPerProfileClearsVerifyMessages() = runTest(dispatcher) {
        shared.value = ""
        val vm = viewModel()
        vm.verify(SHARED)
        assertEquals(
            uiTextOf(R.string.settings_yituliu_token_empty),
            vm.verifyStates.value[SHARED]?.message?.text,
        )

        vm.setPerProfile(true)

        assertTrue(vm.verifyStates.value.isEmpty())
    }
}
