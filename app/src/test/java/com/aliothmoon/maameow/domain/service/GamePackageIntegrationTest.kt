package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.config.GamePackageConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.usecase.CheckGameReadinessUseCase
import com.aliothmoon.maameow.domain.usecase.GameReadiness
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.remote.AppAliveStatus
import com.aliothmoon.maameow.remote.internal.GameAudioMuteController
import com.aliothmoon.maameow.remote.internal.RemoteUtils
import java.io.File
import com.aliothmoon.maameow.third.Ln
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GamePackageIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()
    @After fun resetResolver() = Packages.initialize(null)

    private fun writeConfig(packageName: String) {
        File(temporary.root, "resource/config.json").apply {
            parentFile!!.mkdirs()
            writeText("""{"packageName":{"YoStarKR":"$packageName"}}""")
        }
    }

    @Test fun installCheckAndWatchdogUseSameCustomPackageAndWatchTargetIsFrozen() = runTest {
        writeConfig(SAMSUNG)
        Packages.initialize(GamePackageConfig(temporary.root))
        val calls = mutableListOf<String>()
        val checker = mockk<AppAliveChecker> {
            coEvery { isAppAlive(any()) } answers { calls += firstArg<String>(); AppAliveStatus.ALIVE }
            coEvery { isAppOnBackgroundDisplay(any()) } returns true
        }
        val settings = mockk<AppSettingsManager> {
            every { runMode } returns MutableStateFlow(RunMode.BACKGROUND)
        }
        val remote = mockk<RemoteService> {
            every { isPackageInstalled(SAMSUNG) } returns true
        }
        val readiness = CheckGameReadinessUseCase(
            checker, settings, mockk(relaxed = true),
            isPackageInstalled = { remote.isPackageInstalled(it) },
        )
        assertTrue(readiness("YoStarKR", false, TaskStartContext(TaskStartMode.MANUAL)) is GameReadiness.Ready)
        verify(exactly = 1) { remote.isPackageInstalled(SAMSUNG) }
        assertEquals(listOf(SAMSUNG), calls)
        calls.clear()

        // Task profile differs from the explicit client's run (e.g. a Copilot request).
        val chain = mockk<TaskChainState> { every { clientType } returns "Official" }
        val watchdog = AppWatchdog(chain, checker, backgroundScope)
        watchdog.startWatching("YoStarKR")
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(listOf(SAMSUNG), calls)

        writeConfig("next.game")
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(SAMSUNG, SAMSUNG), calls)
        assertEquals(AppWatchdog.WatchdogState.WATCHING, watchdog.state.value)

        watchdog.stopWatching()
        Packages.onResourcesLoaded("YoStarKR", GamePackageConfig(temporary.root).snapshot("YoStarKR"))
        watchdog.startWatching("YoStarKR")
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals("next.game", calls.last())
        watchdog.stopWatching()
    }

    @Test fun customPackageReachesRemoteUidLookup() {
        writeConfig(SAMSUNG)
        Packages.initialize(GamePackageConfig(temporary.root))
        mockkObject(RemoteUtils)
        mockkStatic(Ln::class)
        try {
            every { RemoteUtils.getAppUid(SAMSUNG) } returns -1
            every { Ln.w(any()) } just Runs
            assertFalse(GameAudioMuteController.setMuted(Packages["YoStarKR"]!!, true))
            verify(exactly = 1) { RemoteUtils.getAppUid(SAMSUNG) }
        } finally {
            unmockkObject(RemoteUtils)
            unmockkStatic(Ln::class)
        }
    }

    private companion object { const val SAMSUNG = "com.YoStarKR.Samsung.Arknights" }
}
