package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.fastjson2.JSON
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.constant.MaaFiles.VERSION_FILE
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.datasource.ResourceDownloader
import com.aliothmoon.maameow.data.model.update.StartupUpdateResult
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateInfo
import com.aliothmoon.maameow.data.model.update.UpdateProcessState
import com.aliothmoon.maameow.data.model.update.UpdateSource
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.update.UpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import timber.log.Timber
import java.io.File

/**
 * 更新 ViewModel
 */
class UpdateViewModel(
    private val updateService: UpdateService,
    private val appSettingsManager: AppSettingsManager,
    private val maaResourceLoader: MaaResourceLoader,
    private val pathConfig: MaaPathConfig,
) : ViewModel() {

    // ==================== 资源更新 ====================

    val resourceUpdateState = updateService.resourceProcessState

    private val _currentResourceVersion = MutableStateFlow("")
    val currentResourceVersion: StateFlow<String> = _currentResourceVersion.asStateFlow()

    val updateSource: StateFlow<UpdateSource> = appSettingsManager.updateSource

    val mirrorChyanCdk: StateFlow<String> = appSettingsManager.mirrorChyanCdk

    // 检查状态
    private val _appChecking = MutableStateFlow(false)
    val appChecking: StateFlow<Boolean> = _appChecking.asStateFlow()

    private val _resourceChecking = MutableStateFlow(false)
    val resourceChecking: StateFlow<Boolean> = _resourceChecking.asStateFlow()

    // 检查结果（仅手动触发时写入）
    private val _appCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val appCheckResult: StateFlow<UpdateCheckResult?> = _appCheckResult.asStateFlow()

    private val _resourceCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val resourceCheckResult: StateFlow<UpdateCheckResult?> = _resourceCheckResult.asStateFlow()

    init {
        viewModelScope.launch {
            refreshResourceVersion()
        }
    }


    suspend fun refreshResourceVersion() {
        val raw = loadResourceVersion()
        _currentResourceVersion.value = ResourceDownloader.formatVersionForDisplay(raw)
    }

    private suspend fun loadResourceVersion(): String {
        val dir = pathConfig.resourceDir
        return runCatching {
            withContext(Dispatchers.IO) {
                File(dir, VERSION_FILE).takeIf { it.exists() }?.source()?.buffer()
                    ?.readUtf8()
                    ?.let { JSON.parseObject(it).getString("last_updated") }.orEmpty()
            }
        }.onFailure {
            Timber.w(it, "读取资源版本失败")
        }.getOrDefault("")

    }

    fun setUpdateSource(source: UpdateSource) {
        viewModelScope.launch {
            appSettingsManager.setUpdateSource(source)
        }
    }

    fun setMirrorChyanCdk(cdk: String) {
        viewModelScope.launch {
            appSettingsManager.setMirrorChyanCdk(cdk)
        }
    }


    fun checkResourceUpdate() {
        val currentState = resourceUpdateState.value
        if (_resourceChecking.value || currentState is UpdateProcessState.Downloading || currentState is UpdateProcessState.Extracting || currentState is UpdateProcessState.Installing) {
            return
        }
        viewModelScope.launch {
            _resourceChecking.value = true
            val currentVersion = loadResourceVersion()
            Timber.d("当前资源版本: $currentVersion, 下载源: ${updateSource.value}")
            _resourceCheckResult.value = updateService.checkResourceUpdate(currentVersion)
            _resourceChecking.value = false
        }
    }

    fun dismissResourceCheckResult() {
        _resourceCheckResult.value = null
    }

    fun confirmResourceDownload() {
        viewModelScope.launch {
            val resourcesDir = pathConfig.resourceDir
            val file = File(resourcesDir)
            if (!file.exists()) {
                file.mkdirs()
            }

            val currentVersion = loadResourceVersion()
            val result = updateService.confirmAndDownloadResource(
                source = updateSource.value,
                cdk = mirrorChyanCdk.value,
                currentVersion = currentVersion,
                dir = file
            )
            if (result.isSuccess) {
                refreshResourceVersion()
                maaResourceLoader.reset()
                maaResourceLoader.load()
            }
        }
    }


    // ==================== 启动检查 ====================

    private var hasCheckedOnStartup = false

    private val _startupUpdateDialog = MutableStateFlow<StartupUpdateResult?>(null)
    val startupUpdateDialog: StateFlow<StartupUpdateResult?> = _startupUpdateDialog.asStateFlow()

    fun checkUpdatesOnStartup() {
        if (hasCheckedOnStartup) return
        hasCheckedOnStartup = true

        viewModelScope.launch {
            if (!appSettingsManager.autoCheckUpdate.value) return@launch

            val currentVersion = loadResourceVersion()

            // 并行检查
            val appResultDeferred = async {
                updateService.checkAppUpdate(mirrorChyanCdk.value)
            }
            val resResult =
                updateService.checkResourceUpdate(currentVersion, mirrorChyanCdk.value)

            val appResult = appResultDeferred.await()

            // 聚合结果
            val appAvailable = (appResult as? UpdateCheckResult.Available)?.info
            val resAvailable = (resResult as? UpdateCheckResult.Available)?.info

            if (appAvailable != null || resAvailable != null) {
                _startupUpdateDialog.value = StartupUpdateResult(
                    appUpdate = appAvailable,
                    resourceUpdate = resAvailable
                )
            }
        }
    }

    fun dismissStartupDialog() {
        _startupUpdateDialog.value = null
    }

    fun reset() {
        updateService.resetResourceProcess()
    }

    // ==================== App 更新 ====================

    val appUpdateState = updateService.appProcessState

    val currentAppVersion: String = BuildConfig.VERSION_NAME

    fun checkAppUpdate() {
        val currentState = appUpdateState.value
        if (_appChecking.value || currentState is UpdateProcessState.Downloading || currentState is UpdateProcessState.Installing) {
            return
        }
        viewModelScope.launch {
            _appChecking.value = true
            Timber.i("检查 App 更新 (MirrorChyan)")
            _appCheckResult.value = updateService.checkAppUpdate()
            _appChecking.value = false
        }
    }

    fun dismissAppCheckResult() {
        _appCheckResult.value = null
    }

    fun confirmAppDownload() {
        viewModelScope.launch {
            updateService.confirmAndDownloadApp(
                source = updateSource.value,
                cdk = mirrorChyanCdk.value
            )
        }
    }

    fun resetAppUpdate() {
        updateService.resetAppProcess()
    }
}
