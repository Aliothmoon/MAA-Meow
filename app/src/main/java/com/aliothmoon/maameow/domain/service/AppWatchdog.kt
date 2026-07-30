package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.remote.AppAliveStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class AppWatchdog(
    private val chainState: TaskChainState,
    private val appAliveChecker: AppAliveChecker,
) {
    enum class WatchdogState {
        IDLE,
        WATCHING,
        APP_DIED,
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(WatchdogState.IDLE)
    val state: StateFlow<WatchdogState> = _state.asStateFlow()

    private val _appDiedEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val appDiedEvent: SharedFlow<String> = _appDiedEvent.asSharedFlow()

    // 游戏窗口离开虚拟显示器且自动拉回失败（每次漂移只上报一次）
    private val _displayDriftEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val displayDriftEvent: SharedFlow<String> = _displayDriftEvent.asSharedFlow()

    private var watchJob: Job? = null
    private var driftNotified = false

    fun startWatching() {
        stopWatching()
        driftNotified = false

        val clientType = chainState.clientType
        val packageName = Packages[clientType]
        if (packageName == null) {
            Timber.w(
                "AppWatchdog: cannot resolve package name for clientType=%s, skipping",
                clientType
            )
            return
        }

        Timber.i("AppWatchdog: start watching %s", packageName)
        _state.value = WatchdogState.WATCHING

        watchJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val appAliveStatus = checkAppAliveStatus(packageName)
                if (!isActive) {
                    return@launch
                }
                when (appAliveStatus) {
                    AppAliveStatus.ALIVE -> checkDisplayPinned(packageName)
                    AppAliveStatus.UNKNOWN -> {
                        Timber.w(
                            "AppWatchdog: unable to determine whether %s is alive",
                            packageName
                        )
                    }

                    AppAliveStatus.DEAD -> {
                        Timber.w("AppWatchdog: app %s is no longer alive", packageName)
                        _state.value = WatchdogState.APP_DIED
                        _appDiedEvent.tryEmit(packageName)
                        return@launch
                    }

                    else -> {
                        Timber.w(
                            "AppWatchdog: unexpected app status %s for %s",
                            appAliveStatus,
                            packageName
                        )
                    }
                }
            }
        }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        _state.value = WatchdogState.IDLE
    }

    private suspend fun checkAppAliveStatus(packageName: String): Int {
        return appAliveChecker.isAppAlive(packageName)
    }

    /**
     * 后台模式下部分 ROM（如 One UI）会把游戏从虚拟屏挪回主屏，导致识别与真实画面分离。
     * 运行中持续检测漂移，发现后先尝试拉回，拉不回再上报事件提醒用户。
     */
    private suspend fun checkDisplayPinned(packageName: String) {
        val onDisplay = appAliveChecker.isAppOnBackgroundDisplay(packageName) ?: return
        if (onDisplay) {
            driftNotified = false
            return
        }
        Timber.w("AppWatchdog: app %s left the virtual display, trying to move it back", packageName)
        if (appAliveChecker.moveAppToBackgroundDisplay(packageName) == true) {
            Timber.i("AppWatchdog: app %s moved back to the virtual display", packageName)
            return
        }
        if (!driftNotified) {
            driftNotified = true
            _displayDriftEvent.tryEmit(packageName)
        }
    }
}
