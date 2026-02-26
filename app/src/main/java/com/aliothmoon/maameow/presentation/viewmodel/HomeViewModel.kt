package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.constant.DisplayMode
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.service.update.UpdateService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import com.aliothmoon.maameow.manager.ShizukuManager
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.presentation.state.HomeUiState
import com.aliothmoon.maameow.presentation.state.StatusColorType
import com.aliothmoon.maameow.utils.Misc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs

class HomeViewModel(
    private val application: Context,
    private val appSettingsManager: AppSettingsManager,
    private val overlayController: OverlayController,
    private val updateService: UpdateService,
    private val permissionManager: PermissionManager,
    private val resourceLoader: MaaResourceLoader,
    private val compositionService: MaaCompositionService,
    private val resourceInitService: ResourceInitService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeResourceUpdateState()
        observeServiceStatus()
        observeResourceInitState()
        observeRunMode()
        observeFloatWindowMode()
        observeIsGranting()
        observeOverlayActive()
    }

    private fun observeResourceUpdateState() {
        viewModelScope.launch {
            updateService.resourceUpdateState.collect { state ->
                Timber.i("ResourceUpdateState collect $state")
                _uiState.update { it.copy(resourceUpdateState = state) }
            }
        }
    }

    private fun observeServiceStatus() {
        viewModelScope.launch {
            combine(
                RemoteServiceManager.state,
                resourceLoader.state,
                compositionService.state
            ) { serviceState, resourceState, executionState ->
                when {
                    serviceState is RemoteServiceManager.ServiceState.Died ||
                            serviceState is RemoteServiceManager.ServiceState.Error ->
                        Triple("服务异常", StatusColorType.ERROR, false)

                    serviceState is RemoteServiceManager.ServiceState.Connecting ->
                        Triple("服务连接中...", StatusColorType.WARNING, true)

                    serviceState is RemoteServiceManager.ServiceState.Disconnected ->
                        Triple("未连接", StatusColorType.NEUTRAL, false)

                    resourceState is MaaResourceLoader.State.Loading ||
                            resourceState is MaaResourceLoader.State.Reloading ->
                        Triple("资源加载中...", StatusColorType.WARNING, true)

                    resourceState is MaaResourceLoader.State.Failed ->
                        Triple("资源加载失败", StatusColorType.ERROR, false)

                    resourceState is MaaResourceLoader.State.NotLoaded ->
                        Triple("资源未加载", StatusColorType.NEUTRAL, false)

                    executionState == MaaExecutionState.ERROR ->
                        Triple("任务异常", StatusColorType.ERROR, false)

                    executionState == MaaExecutionState.STARTING ->
                        Triple("任务启动中...", StatusColorType.WARNING, true)

                    executionState == MaaExecutionState.RUNNING ->
                        Triple("运行中", StatusColorType.PRIMARY, true)

                    else ->
                        Triple("就绪", StatusColorType.PRIMARY, false)
                }
            }.collect { (text, color, loading) ->
                _uiState.update {
                    it.copy(
                        serviceStatusText = text,
                        serviceStatusColor = color,
                        serviceStatusLoading = loading
                    )
                }
            }
        }
    }

    private fun observeFloatWindowMode() {
        viewModelScope.launch {
            appSettingsManager.overlayControlMode.collect { mode ->
                _uiState.update { it.copy(overlayControlMode = mode) }
            }
        }
    }

    private fun observeIsGranting() {
        viewModelScope.launch {
            permissionManager.isGranting.collect { granting ->
                _uiState.update { it.copy(isGranting = granting) }
            }
        }
    }

    private fun observeOverlayActive() {
        viewModelScope.launch {
            overlayController.isActive.collect { active ->
                _uiState.update { it.copy(isShowControlOverlay = active) }
            }
        }
    }

    private fun observeResourceInitState() {
        viewModelScope.launch {
            resourceInitService.state.collect { state ->
                _uiState.update { it.copy(resourceInitState = state) }
            }
        }
    }

    private fun observeRunMode() {
        viewModelScope.launch {
            appSettingsManager.runMode.collect { mode ->
                _uiState.update { it.copy(runMode = mode) }
            }
        }
    }

    fun checkAndInitResource() {
        viewModelScope.launch {
            resourceInitService.checkAndInit()
        }
    }

    fun onTryResourceInit() {
        viewModelScope.launch {
            resourceInitService.doExtractFromAssets()
        }
    }

    fun onRequestShizuku(context: Context) {
        viewModelScope.launch {
            if (!ShizukuManager.isShizukuAvailable()) {
                Toast.makeText(context, "Shizuku服务不存在，请检查服务是否启动", Toast.LENGTH_SHORT)
                    .show()
                return@launch
            }
            val granted = permissionManager.requestShizuku()
            if (!granted) {
                Toast.makeText(context, "Shizuku授权失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onRequestOverlay(context: Context) {
        viewModelScope.launch {
            permissionManager.requestOverlay(context)
        }
    }

    fun onRequestStorage(context: Context) {
        viewModelScope.launch {
            permissionManager.requestStorage(context)
        }
    }

    fun onRequestBatteryWhitelist(context: Context) {
        viewModelScope.launch {
            permissionManager.requestBatteryWhitelist(context)
        }
    }

    fun onRequestNotification(context: Context) {
        viewModelScope.launch {
            permissionManager.requestNotification(context)
        }
    }

    fun onRequestAccessibility(context: Context) {
        viewModelScope.launch {
            permissionManager.requestAccessibility(context)
        }
    }

    fun onControlOverlayModeChanged(mode: OverlayControlMode) {
        viewModelScope.launch {
            appSettingsManager.setFloatWindowMode(mode)
            if (_uiState.value.isShowControlOverlay) {
                overlayController.applyMode(mode)
            }
        }
    }

    fun onStartControlOverlay() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                // 刷新权限状态
                permissionManager.refresh()
                val state = permissionManager.permissions

                // 检查必要权限
                val currentMode = appSettingsManager.overlayControlMode.value
                // 先检查shizuku
                if (!state.shizuku) {
                    _uiState.update { it.copy(isLoading = false) }
                    Toast.makeText(
                        application,
                        "请先授予Shizuku权限",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val missingPermissions = buildList {
                    if (!state.overlay) add("悬浮窗权限")
                    if (!state.storage) add("外部存储权限")
                    if (currentMode == OverlayControlMode.ACCESSIBILITY && !state.accessibility) {
                        add("无障碍权限")
                    }
                }

                if (missingPermissions.isNotEmpty()) {
                    _uiState.update { it.copy(isLoading = false) }
                    Toast.makeText(
                        application,
                        "请先授予以下权限: ${missingPermissions.joinToString("、")}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // 检查分辨率是否为 16:9
                if (!checkResolution()) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                overlayController.show(currentMode)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error starting floating window")
                _uiState.update { it.copy(isLoading = false) }
                Toast.makeText(application, "启动悬浮窗失败: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun onStopControlOverlay() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                overlayController.hideAll()
                Timber.i("onStopFloatingWindow: Floating window hidden")
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error stopping floating window")
                _uiState.update { it.copy(isLoading = false) }
                Toast.makeText(application, "停止悬浮窗失败: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun onStopAllServices() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                overlayController.hideAll()
                RemoteServiceManager.unbind()

                Timber.i("onStopAllServices: All services stopped")
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error stopping all services")
                _uiState.update { it.copy(isLoading = false) }
                Toast.makeText(application, "关闭服务失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onChangeTo16x9Resolution(ctx: Context) {
        viewModelScope.launch {
            try {

                if (!permissionManager.permissions.shizuku) {
                    val ret = permissionManager.requestShizuku()
                    if (!ret) {
                        Toast.makeText(ctx, "Shizuku权限未获取", Toast.LENGTH_SHORT)
                            .show()
                        return@launch
                    }
                }
                _uiState.update { it.copy(isLoading = true) }
                val (width, height) = Misc.getPhysicalSize(ctx)

                val (targetWidth, targetHeight) = Misc.calculate16x9Resolution(
                    width,
                    height
                )
                val ret = withContext(Dispatchers.IO) {
                    useRemoteService { service ->
                        service.setForcedDisplaySize(targetWidth, targetHeight)
                    }
                }
                Timber.i("onChangeTo16x9Resolution: setForcedDisplaySize result: %s", ret)

                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "onChangeTo16x9Resolution: Error changing resolution")
                _uiState.update { it.copy(isLoading = false) }
                Toast.makeText(ctx, "修改分辨率失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onResetResolution(ctx: Context) {
        Timber.i("onResetResolution: Resetting resolution to default")
        viewModelScope.launch {
            try {

                if (!permissionManager.permissions.shizuku) {
                    val ret = permissionManager.requestShizuku()
                    if (!ret) {
                        Toast.makeText(ctx, "Shizuku权限未获取", Toast.LENGTH_SHORT)
                            .show()
                        return@launch
                    }
                }
                _uiState.update { it.copy(isLoading = true) }
                val ret = withContext(Dispatchers.IO) {
                    useRemoteService { it.clearForcedDisplaySize() }
                }
                Timber.i("onResetResolution: %s", ret)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error resetting resolution")
                _uiState.update { it.copy(isLoading = false) }
                Toast.makeText(application, "重置分辨率失败: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun checkResolution(): Boolean {
        val displayMetrics = application.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val longSide = maxOf(width, height)
        val shortSide = minOf(width, height)

        val ratio = longSide.toFloat() / shortSide.toFloat()
        val targetRatio = 16f / 9f
        val tolerance = 0.05f
        val isValid = abs(ratio - targetRatio) <= targetRatio * tolerance

        if (!isValid) {
            Toast.makeText(
                application,
                "当前分辨率不是 16:9，请使用后台任务或调整分辨率",
                Toast.LENGTH_LONG
            ).show()
            Timber.w("分辨率检查失败: ${longSide}x${shortSide}, 需要16:9")
        }

        return isValid
    }

    fun onRunModeChange(isBackground: Boolean) {
        viewModelScope.launch {
            if (isBackground && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                _uiState.update {
                    it.copy(
                        showRunModeUnsupportedDialog = true,
                        runModeUnsupportedMessage = "Android 10 及以下系统不支持后台模式，请使用前台模式。"
                    )
                }
                return@launch
            }

            appSettingsManager.setRunMode(
                if (isBackground) RunMode.BACKGROUND
                else RunMode.FOREGROUND
            )
            val mode = if (isBackground) {
                DisplayMode.BACKGROUND
            } else {
                DisplayMode.PRIMARY
            }
            if (permissionManager.permissions.shizuku) {
                useRemoteService {
                    it.setVirtualDisplayMode(mode)
                }
            }
        }
    }

    fun onDismissRunModeUnsupportedDialog() {
        _uiState.update { it.copy(showRunModeUnsupportedDialog = false) }
    }

    fun checkRunModeChangeEnabled(): Boolean {
        val value = compositionService.state.value
        return !(value == MaaExecutionState.RUNNING || value == MaaExecutionState.STARTING)
    }


}
