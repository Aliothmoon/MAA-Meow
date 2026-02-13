package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

class MaaResourceLoader(
    private val pathConfig: MaaPathConfig,
    private val appSettings: AppSettingsManager
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

    val isReady: Boolean get() = _state.value is State.Ready

    suspend fun load(): Result<Unit> {
        if (_state.value is State.Ready) {
            Timber.d("资源已加载，跳过")
            return Result.success(Unit)
        }

        _state.value = State.Loading()
        Timber.i("MaaCore Resources Loading")

        return try {
            withContext(Dispatchers.IO) {
                useRemoteService {
                    val dir = pathConfig.rootDir
                    it.setup(dir, appSettings.debugMode.value)
                    val maa = it.maaCoreService
                    if (!maa.LoadResource(dir)) {
                        _state.value = State.Failed("加载资源失败")
                        Timber.e("LoadResource 失败: $dir")
                        return@useRemoteService Result.failure(Exception("加载资源失败"))
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

}
