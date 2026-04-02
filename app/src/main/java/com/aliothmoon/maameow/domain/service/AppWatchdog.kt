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

    private var watchJob: Job? = null

    fun startWatching() {
        stopWatching()

        val clientType = chainState.getClientTypeOrNull()
        val packageName = clientType?.let { Packages[it] }
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
                    AppAliveStatus.ALIVE -> Unit
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
}
