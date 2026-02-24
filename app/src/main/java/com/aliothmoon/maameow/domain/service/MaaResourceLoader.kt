package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskConfigState
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class MaaResourceLoader(
    private val pathConfig: MaaPathConfig,
    private val appSettings: AppSettingsManager,
    private val taskConfigState: TaskConfigState,
) {

    sealed class State {
        /** 未加载 */
        data object NotLoaded : State()

        /** 加载中 */
        data class Loading(val message: String = "正在加载MAA资源, 请稍等 ...") : State()

        /** 重新加载中（资源更新后） */
        data class Reloading(val message: String = "正在重新加载MAA资源, 请稍等 ...") : State()

        /** 已就绪 */
        data object Ready : State()

        /** 加载失败 */
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.NotLoaded)
    val state: StateFlow<State> = _state.asStateFlow()


    suspend fun load(): Result<Unit> {
        _state.value = State.Loading()
        Timber.i("MaaCore Resources Loading")
        return try {
            withContext(Dispatchers.IO) {
                useRemoteService {
                    val rootDir = pathConfig.rootDir
                    val cacheDir = pathConfig.cacheDir
                    it.setup(rootDir, appSettings.debugMode.value)
                    val maa = it.maaCoreService

                    // 复制 tasks.json 到 tasks/ 子目录（兼容新目录结构）
                    copyTasksJson(pathConfig.resourceDir)
                    copyTasksJson(pathConfig.cacheResourceDir)

                    // 先加载主资源，再加载缓存资源（后者覆盖前者），和 WPF 逻辑一致
                    if (!maa.LoadResource(rootDir)) {
                        _state.value = State.Failed("加载资源失败")
                        Timber.e("LoadResource 失败: $rootDir")
                        return@useRemoteService Result.failure(Exception("加载资源失败"))
                    }
                    // 缓存目录可能不存在资源，加载失败不影响整体
                    if (File(pathConfig.cacheResourceDir).exists()) {
                        if (!maa.LoadResource(cacheDir)) {
                            Timber.w("LoadResource 缓存资源失败（非致命）: $cacheDir")
                        } else {
                            Timber.i("MaaCore 缓存资源加载成功: $cacheDir")
                        }
                    }

                    _state.value = State.Ready
                    Timber.i("MaaCore 资源加载成功")
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            val message = e.message ?: "资源加载异常"
            _state.value = State.Failed(message)
            Timber.e(e, "资源加载异常")
            Result.failure(e)
        }
    }

    suspend fun ensureLoaded(): Result<Unit> {
        return when (_state.value) {
            is State.Ready -> Result.success(Unit)
            is State.Loading, is State.Reloading -> {
                withContext(Dispatchers.IO) {
                    if (_state.value is State.Ready) {
                        Result.success(Unit)
                    } else {
                        Result.failure(
                            Exception(
                                (_state.value as? State.Failed)?.message ?: "资源未加载"
                            )
                        )
                    }
                }
            }

            else -> load()
        }
    }

    fun reset() {
        _state.value = State.NotLoaded
    }


    /**
     * 复制 tasks.json 到 tasks/tasks.json（兼容新目录结构）
     * 和 WPF CopyTasksJson 逻辑一致
     */
    private fun copyTasksJson(resourcePath: String) {
        try {
            val src = File(resourcePath, "tasks.json")
            if (!src.exists()) return
            val destDir = File(resourcePath, "tasks")
            destDir.mkdirs()
            val dest = File(destDir, "tasks.json")
            src.copyTo(dest, overwrite = true)
            Timber.d("copyTasksJson: ${src.absolutePath} -> ${dest.absolutePath}")
        } catch (e: Exception) {
            Timber.w(e, "copyTasksJson 失败: $resourcePath")
        }
    }

}
