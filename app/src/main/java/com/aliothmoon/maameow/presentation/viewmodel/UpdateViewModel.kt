package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.fastjson2.JSON
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.constant.MaaFiles.VERSION_FILE
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.update.UpdateProcessState
import com.aliothmoon.maameow.data.model.update.UpdateSource
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.update.UpdateService
import kotlinx.coroutines.Dispatchers
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

    val resourceUpdateState = updateService.resourceUpdateState

    private val _currentResourceVersion = MutableStateFlow("")
    val currentResourceVersion: StateFlow<String> = _currentResourceVersion.asStateFlow()

    val updateSource: StateFlow<UpdateSource> = appSettingsManager.updateSource

    val mirrorChyanCdk: StateFlow<String> = appSettingsManager.mirrorChyanCdk

    init {
        viewModelScope.launch {
            refreshResourceVersion()
        }
    }


    suspend fun refreshResourceVersion() {
        _currentResourceVersion.value = loadResourceVersion()
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
        if (currentState is UpdateProcessState.Downloading || currentState is UpdateProcessState.Extracting || currentState is UpdateProcessState.Installing || currentState is UpdateProcessState.Checking) {
            return
        }
        viewModelScope.launch {
            val currentVersion = loadResourceVersion()
            Timber.d("当前资源版本: $currentVersion, 下载源: ${updateSource.value}")
            updateService.checkResourceUpdate(currentVersion)
        }
    }


    fun confirmResourceDownload() {
        val state = resourceUpdateState.value
        if (state !is UpdateProcessState.Available) return

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


    fun reset() {
        updateService.reset()
    }

    // ==================== App 更新 ====================

    val appUpdateState = updateService.appUpdateState

    val currentAppVersion: String = BuildConfig.VERSION_NAME

    fun checkAppUpdate() {
        val currentState = appUpdateState.value
        if (currentState is UpdateProcessState.Downloading || currentState is UpdateProcessState.Installing || currentState is UpdateProcessState.Checking) {
            return
        }
        viewModelScope.launch {
            Timber.i("检查 App 更新 (MirrorChyan)")
            updateService.checkAppUpdate()
        }
    }

    fun confirmAppDownload() {
        val state = appUpdateState.value
        if (state !is UpdateProcessState.Available) return

        viewModelScope.launch {
            updateService.confirmAndDownloadApp(
                source = updateSource.value,
                cdk = mirrorChyanCdk.value
            )
        }
    }

    fun resetAppUpdate() {
        updateService.resetAppUpdate()
    }
}
