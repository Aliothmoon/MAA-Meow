package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors

class UnifiedStateDispatcher(
    private val appSettingsManager: AppSettingsManager,
    private val resourceLoader: MaaResourceLoader,
    private val permissionManager: PermissionManager,
    private val activityManager: ActivityManager,
) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _serviceDiedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val serviceDiedEvent: SharedFlow<Unit> = _serviceDiedEvent.asSharedFlow()

    fun start() {
        startObserving()
        if (permissionManager.permissions.shizuku) {
            RemoteServiceManager.bind()
        }
    }


    fun startObserving() {
        scope.launch {
            RemoteServiceManager.state.collect { state ->
                when (state) {
                    is RemoteServiceManager.ServiceState.Connected -> {
                        Timber.d("Service connected")
                        onServiceConnected(state.service)
                    }

                    is RemoteServiceManager.ServiceState.Died -> {
                        Timber.e("Service died unexpectedly")
                        onServiceDied()
                    }

                    is RemoteServiceManager.ServiceState.Error -> {
                        Timber.e(state.exception, "Service error")
                        onServiceError(state.exception)
                    }

                    is RemoteServiceManager.ServiceState.Connecting -> {
                        Timber.d("Service connecting")
                    }

                    is RemoteServiceManager.ServiceState.Disconnected -> {
                        Timber.d("Service disconnected")
                        onServiceDisconnected()
                    }
                }
            }
        }
        Timber.i("Started observing unified state")
    }

    suspend fun onServiceConnected(srv: RemoteService) {
        withContext(Dispatchers.IO) {
            val mode = appSettingsManager.runMode.value
            srv.setVirtualDisplayMode(mode.displayMode)
            resourceLoader.load()
        }
        activityManager.preload()
    }

    fun onServiceDied() {
        resourceLoader.reset()
        _serviceDiedEvent.tryEmit(Unit)
    }

    fun onServiceDisconnected() {
        resourceLoader.reset()
    }


    fun onServiceError(exception: Throwable) {
        _serviceDiedEvent.tryEmit(Unit)
    }

}
