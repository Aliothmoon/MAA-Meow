package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DisplayMode
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.models.ShizukuLaunchMode
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.service.update.UpdateService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import com.aliothmoon.maameow.manager.ShizukuInstallHelper
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.presentation.state.HomeUiState
import com.aliothmoon.maameow.presentation.state.StatusColorType
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.remoteBackendPermissionLabel
import com.aliothmoon.maameow.utils.i18n.uiTextOf
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

    private val _uiState = MutableStateFlow(
        HomeUiState(serviceStatusText = uiTextOf(R.string.home_status_disconnected))
    )
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
            updateService.resourceProcessState.collect { state ->
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
                Timber.i("ServiceState collect $serviceState $resourceState $executionState")
                val remoteServiceActive = serviceState is RemoteServiceManager.ServiceState.Connected ||
                        serviceState is RemoteServiceManager.ServiceState.Connecting
                val status = when {
                    serviceState is RemoteServiceManager.ServiceState.Died ||
                            serviceState is RemoteServiceManager.ServiceState.Error ->
                        Triple(uiTextOf(R.string.home_status_service_error), StatusColorType.ERROR, false)

                    serviceState is RemoteServiceManager.ServiceState.Connecting ->
                        Triple(uiTextOf(R.string.home_status_service_connecting), StatusColorType.WARNING, true)

                    serviceState is RemoteServiceManager.ServiceState.Disconnected ->
                        Triple(uiTextOf(R.string.home_status_disconnected), StatusColorType.NEUTRAL, false)

                    resourceState is MaaResourceLoader.State.Loading ||
                            resourceState is MaaResourceLoader.State.Reloading ->
                        Triple(uiTextOf(R.string.home_status_resource_loading), StatusColorType.WARNING, true)

                    resourceState is MaaResourceLoader.State.Failed ->
                        Triple(uiTextOf(R.string.home_status_resource_failed), StatusColorType.ERROR, false)

                    resourceState is MaaResourceLoader.State.NotLoaded ->
                        Triple(uiTextOf(R.string.home_status_resource_not_loaded), StatusColorType.NEUTRAL, false)

                    executionState == MaaExecutionState.ERROR ->
                        Triple(uiTextOf(R.string.home_status_task_error), StatusColorType.ERROR, false)

                    executionState == MaaExecutionState.STARTING ->
                        Triple(uiTextOf(R.string.home_status_task_starting), StatusColorType.WARNING, true)

                    executionState == MaaExecutionState.RUNNING ->
                        Triple(uiTextOf(R.string.home_status_task_running), StatusColorType.PRIMARY, true)

                    else ->
                        Triple(uiTextOf(R.string.home_status_ready), StatusColorType.PRIMARY, false)
                }
                Pair(status, remoteServiceActive)
            }.collect { (status, remoteServiceActive) ->
                val (text, color, loading) = status
                _uiState.update {
                    it.copy(
                        serviceStatusText = text,
                        serviceStatusColor = color,
                        serviceStatusLoading = loading,
                        remoteServiceActive = remoteServiceActive
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

    fun onRequestRemoteAccess(context: Context) {
        viewModelScope.launch {
            val backend = permissionManager.permissions.startupBackend
            if (!permissionManager.permissions.isStartupBackendAvailable(backend)) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.home_toast_backend_unavailable, backend.display),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val granted = permissionManager.requestRemoteAccess()
            if (!granted) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.home_toast_backend_auth_failed, backend.display),
                    Toast.LENGTH_SHORT
                ).show()
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
            if (permissionManager.permissions.remoteAccessGranted) {
                val success = permissionManager.quickGrantAccessibility()
                if (success) return@launch
            }
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
                if (!state.remoteAccessGranted) {
                    _uiState.update { it.copy(isLoading = false) }
                    Toast.makeText(
                        application,
                        application.getString(
                            R.string.home_toast_grant_permission,
                            application.remoteBackendPermissionLabel(state.startupBackend)
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val missingPermissions = buildList {
                    if (!state.overlay) add(application.getString(R.string.home_permission_overlay))
                    if (!state.storage) add(application.getString(R.string.home_permission_storage))
                    if (currentMode == OverlayControlMode.ACCESSIBILITY && !state.accessibility) {
                        add(application.getString(R.string.home_permission_accessibility))
                    }
                }

                if (missingPermissions.isNotEmpty()) {
                    _uiState.update { it.copy(isLoading = false) }
                    val separator = application.getString(R.string.home_toast_missing_permissions_separator)
                    Toast.makeText(
                        application,
                        application.getString(
                            R.string.home_toast_missing_permissions,
                            missingPermissions.joinToString(separator)
                        ),
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
                Toast.makeText(
                    application,
                    application.getString(R.string.home_toast_start_overlay_failed, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
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
                Toast.makeText(
                    application,
                    application.getString(R.string.home_toast_stop_overlay_failed, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun onOpenShizuku() {
        val opened = if (appSettingsManager.shizukuLaunchMode.value == ShizukuLaunchMode.OFF) {
            false
        } else {
            ShizukuInstallHelper.openShizuku(
                application,
                appSettingsManager.shizukuLaunchPackage.value
            )
        }
        if (!opened) {
            Toast.makeText(
                application,
                application.getString(R.string.home_toast_open_shizuku_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun onToggleRemoteService() {
        if (_uiState.value.remoteServiceActive) {
            onCloseRemoteService()
        } else {
            onOpenRemoteService()
        }
    }

    private fun onOpenRemoteService() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                permissionManager.refresh()
                val state = permissionManager.permissions
                val backend = state.startupBackend
                if (!state.isStartupBackendAvailable(backend)) {
                    _uiState.update { it.copy(isLoading = false) }
                    Toast.makeText(
                        application,
                        application.getString(R.string.home_toast_backend_unavailable, backend.display),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                if (!state.remoteAccessGranted) {
                    val granted = permissionManager.requestRemoteAccess()
                    if (!granted) {
                        _uiState.update { it.copy(isLoading = false) }
                        Toast.makeText(
                            application,
                            application.getString(R.string.home_toast_backend_auth_failed, backend.display),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                }

                RemoteServiceManager.bind()

                Timber.i("onOpenRemoteService: Service binding started")
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error opening remote service")
                _uiState.update { it.copy(isLoading = false) }
                Toast.makeText(
                    application,
                    application.getString(R.string.home_toast_open_service_failed, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun onCloseRemoteService() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                // 先关闭依赖远程服务的入口，避免继续操作已断开的 Binder。
                overlayController.hideAll()
                RemoteServiceManager.unbind()

                Timber.i("onCloseRemoteService: Service unbound")
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error closing remote service")
                _uiState.update { it.copy(isLoading = false) }
                Toast.makeText(
                    application,
                    application.getString(R.string.home_toast_close_service_failed, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun onChangeTo16x9Resolution(ctx: Context) {
        viewModelScope.launch {
            try {
                val label =
                    application.remoteBackendPermissionLabel(permissionManager.permissions.startupBackend)
                if (!permissionManager.permissions.remoteAccessGranted) {
                    val ret = permissionManager.requestRemoteAccess()
                    if (!ret) {
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.home_toast_backend_not_acquired, label),
                            Toast.LENGTH_SHORT
                        ).show()
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
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.home_toast_change_resolution_failed, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun onResetResolution(ctx: Context) {
        Timber.i("onResetResolution: Resetting resolution to default")
        viewModelScope.launch {
            try {
                val label =
                    application.remoteBackendPermissionLabel(permissionManager.permissions.startupBackend)
                if (!permissionManager.permissions.remoteAccessGranted) {
                    val ret = permissionManager.requestRemoteAccess()
                    if (!ret) {
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.home_toast_backend_not_acquired, label),
                            Toast.LENGTH_SHORT
                        ).show()
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
                Toast.makeText(
                    application,
                    application.getString(R.string.home_toast_reset_resolution_failed, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun checkResolution(): Boolean {
        // 不能用 application.resources.displayMetrics：application context 的 DisplayMetrics
        // 不反映 setForcedDisplaySize 修改后的 forced size（在 Android 9 上持续返回原生物理分辨率
        // 减去系统栏的值）。改用 Misc.getScreenSize，其底层走 Display.getRealMetrics /
        // WindowMetrics.getBounds，能正确读到 IWindowManager 层的 forced size。
        val (width, height) = Misc.getScreenSize(application)

        val longSide = maxOf(width, height)
        val shortSide = minOf(width, height)

        val ratio = longSide.toFloat() / shortSide.toFloat()
        val targetRatio = 16f / 9f
        val tolerance = 0.05f
        val isValid = abs(ratio - targetRatio) <= targetRatio * tolerance

        if (!isValid) {
            Toast.makeText(
                application,
                application.getString(R.string.home_toast_not_16_9_resolution),
                Toast.LENGTH_LONG
            ).show()
            Timber.w("resolution check failed: ${longSide}x${shortSide}, required 16:9")
        }

        return isValid
    }

    fun onRunModeChange(isBackground: Boolean) {
        viewModelScope.launch {
            if (isBackground && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                _uiState.update {
                    it.copy(
                        showRunModeUnsupportedDialog = true,
                        runModeUnsupportedMessage = uiTextOf(R.string.dialog_run_mode_unsupported_message_pre_q)
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
            if (permissionManager.permissions.remoteAccessGranted) {
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
        return !(value == MaaExecutionState.RUNNING || value == MaaExecutionState.STARTING || value == MaaExecutionState.STOPPING)
    }


}
