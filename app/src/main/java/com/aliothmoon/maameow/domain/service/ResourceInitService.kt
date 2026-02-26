package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.MaaFiles.ASSET_DIR_NAME
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.datasource.AssetExtractor
import com.aliothmoon.maameow.domain.state.ResourceInitState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File


class ResourceInitService(
    private val assetExtractor: AssetExtractor,
    private val pathConfig: MaaPathConfig
) {
    private val _state = MutableStateFlow<ResourceInitState>(ResourceInitState.NotChecked)
    val state: StateFlow<ResourceInitState> = _state.asStateFlow()

    suspend fun checkAndInit() {
        _state.value = ResourceInitState.Checking

        if (pathConfig.isResourceReady) {
            _state.value = ResourceInitState.Ready
            return
        }

        doExtractFromAssets()
    }

    suspend fun reInitialize() {
        doExtractFromAssets()
    }

    suspend fun doExtractFromAssets() {
        _state.value = ResourceInitState.Extracting(0, 0, "准备中...")

        try {
            withContext(Dispatchers.IO) {
                pathConfig.ensureDirectories()
                val resourceDir = File(pathConfig.resourceDir)
                if (!resourceDir.exists()) {
                    resourceDir.mkdirs()
                }
            }

            // 执行提取
            val result = assetExtractor.extract(
                assetDir = ASSET_DIR_NAME,
                destDir = File(pathConfig.resourceDir),
                onProgress = { progress ->
                    _state.value = ResourceInitState.Extracting(
                        extractedCount = progress.extractedCount,
                        totalCount = progress.totalCount,
                        currentFile = progress.currentFile
                    )
                }
            )

            result.fold(
                onSuccess = {
                    Timber.i("资源初始化完成")
                    _state.value = ResourceInitState.Ready
                },
                onFailure = { e ->
                    _state.value = ResourceInitState.Failed(e.message ?: "复制失败")
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "资源初始化失败")
            _state.value = ResourceInitState.Failed(e.message ?: "未知错误")
        }
    }
}
