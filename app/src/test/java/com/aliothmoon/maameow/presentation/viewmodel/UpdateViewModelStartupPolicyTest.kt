package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import com.aliothmoon.maameow.data.api.MirrorChyanCdkStatus
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateInfo
import com.aliothmoon.maameow.data.model.update.UpdateProcessState
import com.aliothmoon.maameow.data.model.update.UpdateSource
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.AutoDownloadPolicy
import com.aliothmoon.maameow.domain.service.update.UnmeteredNetworkChecker
import com.aliothmoon.maameow.domain.service.update.UpdateService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelStartupPolicyTest {

    private val dispatcher = StandardTestDispatcher()

    private val appInfo = UpdateInfo(version = "1.2.0")
    private val resInfo = UpdateInfo(version = "2026-11-25")

    private lateinit var updateService: UpdateService
    private lateinit var settings: AppSettingsManager
    private lateinit var policyFlow: MutableStateFlow<AutoDownloadPolicy>
    private var networkCheckedCount = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        networkCheckedCount = 0
        policyFlow = MutableStateFlow(AutoDownloadPolicy.OFF)

        updateService = mockk {
            every { resourceProcessState } returns MutableStateFlow(UpdateProcessState.Idle)
            every { appProcessState } returns MutableStateFlow(UpdateProcessState.Idle)
            coEvery { checkAppUpdate(any()) } returns UpdateCheckResult.Available(appInfo)
            coEvery { checkResourceUpdate(any()) } returns UpdateCheckResult.Available(resInfo)
            coEvery { downloadApp(any(), any(), any()) } returns Result.success(Unit)
            coEvery { downloadResource(any(), any(), any()) } returns Result.success(Unit)
        }

        settings = mockk {
            every { updateSource } returns MutableStateFlow(UpdateSource.GITHUB)
            every { mirrorChyanCdk } returns MutableStateFlow("")
            every { mirrorChyanCdkExpiresAt } returns MutableStateFlow(0L)
            every { updateChannel } returns MutableStateFlow(UpdateChannel.STABLE)
            every { autoCheckUpdate } returns MutableStateFlow(true)
            every { autoDownloadPolicy } returns policyFlow
            coEvery { savePendingChangelog(any(), any()) } just runs
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(isUnmetered: Boolean): UpdateViewModel {
        val context = mockk<Context>(relaxed = true)
        val pathConfig = mockk<MaaPathConfig> {
            every { resourceDir } returns File(System.getProperty("java.io.tmpdir"), "maameow-test-nonexistent").path
        }
        val cdkStatus = mockk<MirrorChyanCdkStatus> {
            every { state } returns MutableStateFlow(MirrorChyanCdkStatus.State.Unknown)
            every { checking } returns MutableStateFlow(false)
        }
        val checker = UnmeteredNetworkChecker {
            networkCheckedCount++
            isUnmetered
        }
        return UpdateViewModel(context, updateService, settings, pathConfig, cdkStatus, checker, ioDispatcher = dispatcher)
    }

    @Test
    fun `off policy falls back to dialog without querying network`() = runTest(dispatcher) {
        policyFlow.value = AutoDownloadPolicy.OFF
        val vm = createViewModel(isUnmetered = true)

        vm.checkUpdatesOnStartup()
        advanceUntilIdle()

        assertNotNull(vm.startupUpdateDialog.value)
        assertEquals(0, networkCheckedCount)
        coVerify(exactly = 0) { updateService.downloadApp(any(), any(), any()) }
        coVerify(exactly = 0) { updateService.downloadResource(any(), any(), any()) }
    }

    @Test
    fun `wifi only auto downloads app on unmetered network`() = runTest(dispatcher) {
        policyFlow.value = AutoDownloadPolicy.WIFI_ONLY
        val vm = createViewModel(isUnmetered = true)

        vm.checkUpdatesOnStartup()
        advanceUntilIdle()

        assertNull(vm.startupUpdateDialog.value)
        assertEquals(1, networkCheckedCount)
        coVerify(exactly = 1) { updateService.downloadApp(UpdateSource.GITHUB, appInfo.version, UpdateChannel.STABLE) }
        coVerify(exactly = 0) { updateService.downloadResource(any(), any(), any()) }
    }

    @Test
    fun `wifi only auto downloads resource when only resource update exists on unmetered network`() = runTest(dispatcher) {
        policyFlow.value = AutoDownloadPolicy.WIFI_ONLY
        coEvery { updateService.checkAppUpdate(any()) } returns UpdateCheckResult.UpToDate("1.0.0")
        val vm = createViewModel(isUnmetered = true)

        vm.checkUpdatesOnStartup()
        advanceUntilIdle()

        assertNull(vm.startupUpdateDialog.value)
        assertEquals(1, networkCheckedCount)
        coVerify(exactly = 0) { updateService.downloadApp(any(), any(), any()) }
        coVerify(exactly = 1) { updateService.downloadResource(UpdateSource.GITHUB, any(), any()) }
    }

    @Test
    fun `wifi only falls back to confirmation dialog on metered network`() = runTest(dispatcher) {
        policyFlow.value = AutoDownloadPolicy.WIFI_ONLY
        val vm = createViewModel(isUnmetered = false)

        vm.checkUpdatesOnStartup()
        advanceUntilIdle()

        val dialog = vm.startupUpdateDialog.value
        assertNotNull(dialog)
        assertEquals(appInfo, dialog?.appUpdate)
        assertEquals(resInfo, dialog?.resourceUpdate)
        assertEquals(1, networkCheckedCount)
        coVerify(exactly = 0) { updateService.downloadApp(any(), any(), any()) }
        coVerify(exactly = 0) { updateService.downloadResource(any(), any(), any()) }
    }

    @Test
    fun `always policy auto downloads even on metered network without querying checker`() = runTest(dispatcher) {
        policyFlow.value = AutoDownloadPolicy.ALWAYS
        val vm = createViewModel(isUnmetered = false)

        vm.checkUpdatesOnStartup()
        advanceUntilIdle()

        assertNull(vm.startupUpdateDialog.value)
        assertEquals(0, networkCheckedCount)
        coVerify(exactly = 1) { updateService.downloadApp(UpdateSource.GITHUB, appInfo.version, UpdateChannel.STABLE) }
    }

    @Test
    fun `manual download still proceeds on metered network under wifi only policy`() = runTest(dispatcher) {
        policyFlow.value = AutoDownloadPolicy.WIFI_ONLY
        val vm = createViewModel(isUnmetered = false)

        vm.confirmAppDownload(appInfo.version)
        vm.confirmResourceDownload()
        advanceUntilIdle()

        coVerify(exactly = 1) { updateService.downloadApp(UpdateSource.GITHUB, appInfo.version, UpdateChannel.STABLE) }
        coVerify(exactly = 1) { updateService.downloadResource(UpdateSource.GITHUB, any(), any()) }
    }
}
