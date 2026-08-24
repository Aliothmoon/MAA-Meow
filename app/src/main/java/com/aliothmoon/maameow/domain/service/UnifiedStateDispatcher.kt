package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.util.concurrent.Executors

class UnifiedStateDispatcher(
    private val appSettingsManager: AppSettingsManager,
    private val resourceLoader: MaaResourceLoader,
    private val permissionManager: PermissionManager,
    private val chainState: TaskChainState,
    private val resourceInitService: ResourceInitService,
    private val activityManager: ActivityManager,
) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // MaaCompositionService 反向依赖本类，只能懒取
    private val compositionService: MaaCompositionService by inject(MaaCompositionService::class.java)

    private val _serviceDiedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val serviceDiedEvent: SharedFlow<Unit> = _serviceDiedEvent.asSharedFlow()

    fun start() {
        scope.launch {
            RemoteServiceManager.state
                .drop(1)
                .collect { state ->
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

        // 资源初始化检查不依赖首页组合，进程启动即执行 —
        // 定时任务/开机自启/锁屏拉起的进程同样能走通资源加载门
        scope.launch {
            resourceInitService.checkAndInit()
        }

        scope.launch {
            chainState.isLoaded.first { it }
            runCatching { activityManager.load(chainState.clientType) }
                .onFailure { Timber.w(it, "Startup activity data load failed") }
            activityManager.startPeriodicCheck()
        }

        // 服务已连接 + 资源已初始化 → 触发资源加载
        scope.launch {
            combine(
                RemoteServiceManager.state,
                resourceInitService.state
            ) { serviceState, initState ->
                serviceState to initState
            }
                .distinctUntilChanged()
                .collect { (serviceState, initState) ->
                    if (serviceState is RemoteServiceManager.ServiceState.Connected
                        && initState is ResourceInitState.Ready
                    ) {
                        val loaderState = resourceLoader.state.value
                        val shouldLoad = loaderState is MaaResourceLoader.State.NotLoaded
                            || (loaderState is MaaResourceLoader.State.Failed && !loaderState.permanent)
                        if (shouldLoad) {
                            Timber.i("Service connected and resource initialized, loading resources")
                            withContext(Dispatchers.IO) {
                                resourceLoader.load()
                            }
                        }
                    }
                }
        }

        // 资源加载成功后启动热更定时检查
        scope.launch {
            resourceLoader.state
                .filter { it is MaaResourceLoader.State.Ready }
                .collect {
                    activityManager.startPeriodicCheck()
                }
        }

        // 切换客户端时就要重新加载资源
        scope.launch {
            chainState.firstEnabledConfigFlow<WakeUpConfig>()
                .map { it?.clientType }
                .distinctUntilChanged()
                .drop(1)
                .collect { newClientType ->
                    if (newClientType == null || resourceLoader.state.value !is MaaResourceLoader.State.Ready) {
                        return@collect
                    }
                    Timber.i("Client type changed to $newClientType, preparing resources")
                    // prepareResources 持 startMutex 并复查执行状态，任务在跑时自动推迟
                    withContext(Dispatchers.IO) {
                        compositionService.prepareResources(newClientType)
                    }
                }
        }
    }

    suspend fun onServiceConnected(srv: RemoteService) {
        withContext(Dispatchers.IO) {
            permissionManager.grantRequiredPermissions(srv)
            val mode = appSettingsManager.runMode.value
            srv.setVirtualDisplayMode(mode.displayMode)
        }
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
