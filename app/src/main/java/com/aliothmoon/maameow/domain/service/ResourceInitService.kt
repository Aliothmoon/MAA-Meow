package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.MaaFiles.ASSET_DIR_NAME
import com.aliothmoon.maameow.constant.MaaFiles.OVERRIDES_ASSET_TASKS
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
    private val context: Context,
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
        _state.value = ResourceInitState.Extracting(0, 0, context.getString(R.string.resource_init_preparing))

        try {
            withContext(Dispatchers.IO) {
                pathConfig.ensureDirectories()
                val resourceDir = File(pathConfig.resourceDir)
                if (resourceDir.exists() && !resourceDir.deleteRecursively()) {
                    Timber.w("清理旧资源目录失败: ${resourceDir.absolutePath}")
                }
                resourceDir.mkdirs()
            }

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
                    pathConfig.markAppVersion()
                    doForceSyncOverridesTemplate()
                    Timber.i("资源初始化完成")
                    _state.value = ResourceInitState.Ready
                },
                onFailure = { e ->
                    _state.value = ResourceInitState.Failed(e.message ?: context.getString(R.string.resource_init_error_copy_failed))
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "资源初始化失败")
            _state.value = ResourceInitState.Failed(e.message ?: context.getString(R.string.resource_init_error_unknown))
        }
    }

    private fun doForceSyncOverridesTemplate() {
        val dest = pathConfig.overrideTasksFile
        runCatching {
            dest.parentFile?.mkdirs()
            context.assets.open(OVERRIDES_ASSET_TASKS).use { src ->
                dest.outputStream().use { src.copyTo(it) }
            }
            Timber.d("overrides 模板已同步: ${dest.absolutePath}")
        }.onFailure {
            Timber.w(it, "overrides 模板同步失败，跳过")
        }
    }
}
