package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.AppAliveChecker
import com.aliothmoon.maameow.remote.AppAliveStatus
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CheckGameReadinessUseCaseTest {

    private val achievementReporter = mockk<AchievementReporter>(relaxed = true)

    private fun appSettings(mode: RunMode) = mockk<AppSettingsManager> {
        every { runMode } returns MutableStateFlow(mode)
    }

    private fun useCase(
        aliveStatus: Int = AppAliveStatus.ALIVE,
        onBackgroundDisplay: Boolean? = null,
        runMode: RunMode = RunMode.BACKGROUND,
        isPackageInstalled: suspend (String) -> Boolean = { true },
        isEyeProtectionEnabled: () -> Boolean = { false },
    ) = CheckGameReadinessUseCase(
        appAliveChecker = FakeAppAliveChecker(aliveStatus, onBackgroundDisplay),
        appSettings = appSettings(runMode),
        achievementReporter = achievementReporter,
        isPackageInstalled = isPackageInstalled,
        isEyeProtectionEnabled = isEyeProtectionEnabled,
    )

    private fun context(
        mode: TaskStartMode = TaskStartMode.MANUAL,
        acks: Set<TaskStartAcknowledgement> = emptySet(),
    ) = TaskStartContext(mode, acks)

    @Test
    fun gameDead_manual_requiresConfirmation() = runBlocking {
        val result = useCase(AppAliveStatus.DEAD)("Official", false, context())

        assertEquals(
            GameReadiness.RequiresConfirmation(
                TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP
            ),
            result
        )
    }

    @Test
    fun gameDead_scheduled_blocksFast() = runBlocking {
        val result = useCase(AppAliveStatus.DEAD)("Official", false, context(TaskStartMode.SCHEDULED))

        assertEquals(
            GameReadiness.Blocked(TaskStartDecisionReason.GAME_NOT_RUNNING_WITHOUT_WAKE_UP),
            result
        )
    }

    @Test
    fun acknowledgedNotRunning_returnsReady() = runBlocking {
        val result = useCase(AppAliveStatus.DEAD)(
            "Official",
            false,
            context(acks = setOf(TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP)),
        )

        assertTrue(result is GameReadiness.Ready)
    }

    @Test
    fun launchesGame_skipsAliveGate_butRecordsAliveStatus() = runBlocking {
        val checker = FakeAppAliveChecker(AppAliveStatus.DEAD, onBackgroundDisplay = null)
        val useCase = CheckGameReadinessUseCase(checker, appSettings(RunMode.BACKGROUND), achievementReporter)

        val result = useCase("Official", launchesGame = true, context())

        assertEquals(GameReadiness.Ready(gameAliveBeforeStart = false), result)
        assertEquals(1, checker.callCount)
    }

    @Test
    fun foreground_skipsAliveGate_evenIfGameDead() = runBlocking {
        // 前台模式即使游戏未运行也直接放行（后续由横屏检查与连接处理），并如实记录存活状态
        val result = useCase(aliveStatus = AppAliveStatus.DEAD, runMode = RunMode.FOREGROUND)(
            "Official",
            launchesGame = false,
            context(),
        )

        assertEquals(GameReadiness.Ready(gameAliveBeforeStart = false), result)
    }

    @Test
    fun unknownAliveStatus_returnsReady() = runBlocking {
        val result = useCase(AppAliveStatus.UNKNOWN)("Official", false, context())

        assertEquals(GameReadiness.Ready(gameAliveBeforeStart = null), result)
    }

    @Test
    fun notInstalled_manual_requiresConfirmation() = runBlocking {
        val result = useCase(isPackageInstalled = { false })("Official", false, context())

        assertEquals(
            GameReadiness.RequiresConfirmation(TaskStartAcknowledgement.GAME_NOT_INSTALLED),
            result
        )
    }

    @Test
    fun notInstalled_scheduled_blocked() = runBlocking {
        val result = useCase(isPackageInstalled = { false })(
            "Official",
            false,
            context(TaskStartMode.SCHEDULED),
        )

        assertEquals(GameReadiness.Blocked(TaskStartDecisionReason.GAME_NOT_INSTALLED), result)
    }

    @Test
    fun acknowledgedNotInstalled_skipsInstallCheck() = runBlocking {
        val result = useCase(aliveStatus = AppAliveStatus.ALIVE, isPackageInstalled = { false })(
            "Official",
            false,
            context(acks = setOf(TaskStartAcknowledgement.GAME_NOT_INSTALLED)),
        )

        assertTrue(result is GameReadiness.Ready)
    }

    @Test
    fun gameAlive_butNotOnBackgroundDisplay_blocked() = runBlocking {
        val result = useCase(aliveStatus = AppAliveStatus.ALIVE, onBackgroundDisplay = false)(
            "Official",
            false,
            context(),
        )

        assertEquals(
            GameReadiness.Blocked(TaskStartDecisionReason.GAME_NOT_ON_BACKGROUND_DISPLAY),
            result
        )
    }

    @Test
    fun gameAlive_onBackgroundDisplay_ready() = runBlocking {
        val result = useCase(aliveStatus = AppAliveStatus.ALIVE, onBackgroundDisplay = true)(
            "Official",
            false,
            context(),
        )

        assertEquals(GameReadiness.Ready(gameAliveBeforeStart = true), result)
    }

    @Test
    fun gameAlive_backgroundDisplayUnknown_ready() = runBlocking {
        // 后台模式下 isAppOnBackgroundDisplay 返回 null（无法判定）时，按通过处理（== false 不匹配 null）
        val result = useCase(aliveStatus = AppAliveStatus.ALIVE, onBackgroundDisplay = null)(
            "Official",
            false,
            context(),
        )

        assertEquals(GameReadiness.Ready(gameAliveBeforeStart = true), result)
    }

    @Test
    fun eyeProtectionEnabled_manual_requiresConfirmation() = runBlocking {
        val result = useCase(isEyeProtectionEnabled = { true })("Official", false, context())

        assertEquals(
            GameReadiness.RequiresConfirmation(TaskStartAcknowledgement.EYE_PROTECTION_ENABLED),
            result
        )
    }

    @Test
    fun eyeProtectionEnabled_scheduled_autoReady() = runBlocking {
        val result = useCase(isEyeProtectionEnabled = { true })(
            "Official",
            false,
            context(TaskStartMode.SCHEDULED)
        )

        assertEquals(GameReadiness.Ready(gameAliveBeforeStart = true), result)
    }

    @Test
    fun eyeProtectionEnabled_acknowledged_returnsReady() = runBlocking {
        val result = useCase(isEyeProtectionEnabled = { true })(
            "Official",
            false,
            context(acks = setOf(TaskStartAcknowledgement.EYE_PROTECTION_ENABLED))
        )

        assertEquals(GameReadiness.Ready(gameAliveBeforeStart = true), result)
    }

    private class FakeAppAliveChecker(
        private val status: Int,
        private val onBackgroundDisplay: Boolean?,
    ) : AppAliveChecker {
        var callCount: Int = 0
            private set

        override suspend fun isAppAlive(packageName: String): Int {
            callCount += 1
            return status
        }

        override suspend fun isAppOnBackgroundDisplay(packageName: String): Boolean? = onBackgroundDisplay
    }
}
