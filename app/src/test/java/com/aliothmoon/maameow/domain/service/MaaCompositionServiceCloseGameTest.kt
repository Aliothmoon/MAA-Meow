package com.aliothmoon.maameow.domain.service

import android.content.Context
import android.os.IBinder
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.notification.LiveSessionCoordinator
import com.aliothmoon.maameow.maa.callback.SetParamsErrorSignal
import com.aliothmoon.maameow.maa.callback.SubTaskHandler
import com.aliothmoon.maameow.maa.callback.TaskChainStatusTracker
import com.aliothmoon.maameow.maa.callback.ToolboxResultCollector
import com.aliothmoon.maameow.manager.RemoteServiceManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MaaCompositionServiceCloseGameTest {

    @After
    fun tearDown() {
        unmockkObject(RemoteServiceManager)
    }

    @Test
    fun closeGame_stopsVirtualDisplayBeforeForceStop() = runBlocking {
        val calls = mutableListOf<String>()
        val env = createEnv(calls)

        val closed = env.service.closeGame(GAME_PKG)

        assertTrue(closed)
        assertEquals(
            listOf("stopVirtualDisplay", "unmute", "forceStopGame:$GAME_PKG"),
            calls,
        )
    }

    @Test
    fun closeGame_forceStopFailureStillStopsVirtualDisplayFirst() = runBlocking {
        val calls = mutableListOf<String>()
        val env = createEnv(calls, forceStopError = IllegalStateException("force stop failed"))

        val closed = env.service.closeGame(GAME_PKG)

        assertFalse(closed)
        assertEquals(
            listOf("stopVirtualDisplay", "unmute", "forceStopGame:$GAME_PKG"),
            calls,
        )
    }

    @Test
    fun closeGame_disconnectedAtStopStillConnectsToForceStop() = runBlocking {
        val calls = mutableListOf<String>()
        val env = createEnv(calls, initiallyConnected = false)

        val closed = env.service.closeGame(GAME_PKG)

        assertTrue(closed)
        assertEquals(
            listOf("unmute", "forceStopGame:$GAME_PKG"),
            calls,
        )
    }

    @Test
    fun closeGame_rethrowsCancellationException() = runBlocking {
        val calls = mutableListOf<String>()
        val env = createEnv(calls, forceStopError = CancellationException("cancelled"))

        try {
            env.service.closeGame(GAME_PKG)
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
        assertEquals(
            listOf("stopVirtualDisplay", "unmute", "forceStopGame:$GAME_PKG"),
            calls,
        )
    }

    private data class Env(val service: MaaCompositionService)

    private fun createEnv(
        calls: MutableList<String>,
        initiallyConnected: Boolean = true,
        forceStopError: Throwable? = null,
    ): Env {
        val remote = mockk<RemoteService> {
            every { asBinder() } returns mockk<IBinder>()
            every { stopVirtualDisplay() } answers {
                calls += "stopVirtualDisplay"
            }
            every { forceStopGame(any()) } answers {
                calls += "forceStopGame:${firstArg<String>()}"
                forceStopError?.let { throw it }
            }
        }

        val serviceState = MutableStateFlow<RemoteServiceManager.ServiceState>(
            if (initiallyConnected) RemoteServiceManager.ServiceState.Connected(remote)
            else RemoteServiceManager.ServiceState.Disconnected
        )

        mockkObject(RemoteServiceManager)
        every { RemoteServiceManager.state } returns serviceState
        every { RemoteServiceManager.getInstanceOrNull() } answers {
            (serviceState.value as? RemoteServiceManager.ServiceState.Connected)?.service
        }
        coEvery { RemoteServiceManager.useRemoteService<Any?>(any(), any()) } coAnswers {
            serviceState.value = RemoteServiceManager.ServiceState.Connected(remote)
            secondArg<suspend (RemoteService) -> Any?>().invoke(remote)
        }
        coEvery {
            RemoteServiceManager.callOrRestart<Any?>(any(), any(), any(), any(), any())
        } coAnswers {
            val svc = firstArg<RemoteService>()
            val block = arg<(RemoteService) -> Any?>(4)
            block(svc)
        }

        val appContext = mockk<Context>(relaxed = true)
        val context = mockk<Context>(relaxed = true) {
            every { applicationContext } returns appContext
        }
        val unifiedStateDispatcher = mockk<UnifiedStateDispatcher>(relaxed = true) {
            every { serviceDiedEvent } returns MutableSharedFlow()
        }
        val appWatchdog = mockk<AppWatchdog>(relaxed = true) {
            every { appDiedEvent } returns MutableSharedFlow()
            every { displayDriftEvent } returns MutableSharedFlow()
        }
        val gameFpsWatcher = mockk<GameFpsWatcher>(relaxed = true)
        val gameMuteCoordinator = mockk<GameMuteCoordinator>(relaxed = true) {
            coEvery { unmute() } coAnswers {
                calls += "unmute"
                true
            }
        }

        val service = MaaCompositionService(
            context = context,
            resourceLoader = mockk<MaaResourceLoader>(relaxed = true),
            appSettings = mockk<AppSettingsManager>(relaxed = true),
            gameMuteCoordinator = gameMuteCoordinator,
            unifiedStateDispatcher = unifiedStateDispatcher,
            sessionLogger = mockk<MaaSessionLogger>(relaxed = true),
            appWatchdog = appWatchdog,
            gameFpsWatcher = gameFpsWatcher,
            taskChainState = mockk<TaskChainState>(relaxed = true),
            subTaskHandler = mockk<SubTaskHandler>(relaxed = true),
            taskChainStatusTracker = mockk<TaskChainStatusTracker>(relaxed = true),
            notificationCenter = mockk<MaaNotificationCenter>(relaxed = true),
            liveCoordinator = mockk<LiveSessionCoordinator>(relaxed = true),
            dropsRefresher = mockk<FightDropsRefresher>(relaxed = true),
            setParamsErrors = mockk<SetParamsErrorSignal>(relaxed = true),
            toolboxResultCollector = mockk<ToolboxResultCollector>(relaxed = true),
            coreDataPusher = mockk<CoreDataPusher>(relaxed = true),
            telemetry = mockk<RunTelemetry>(relaxed = true),
            screenGate = mockk<ForegroundScreenGate>(relaxed = true),
            processExitJournal = mockk<ProcessExitJournal>(relaxed = true),
        )
        return Env(service)
    }

    companion object {
        private const val GAME_PKG = "com.hypergryph.arknights"
    }
}
