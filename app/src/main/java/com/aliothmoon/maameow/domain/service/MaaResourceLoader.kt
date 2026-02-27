package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskConfigState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val itemHelper: ItemHelper,
    private val resourceDataManager: ResourceDataManager,
    private val activityManager: ActivityManager
) {

    sealed class State {
        data object NotLoaded : State()
        data class Loading(val message: String = "正在加载MAA资源, 请稍等 ...") : State()
        data class Reloading(val message: String = "正在重新加载MAA资源, 请稍等 ...") : State()
        data object Ready : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.NotLoaded)
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun load(clientType: String = taskConfigState.wakeUpConfig.value.clientType): Result<Unit> {
        _state.value = State.Loading()
        Timber.i("MaaCore resources loading, clientType=$clientType")
        loadDepsInfo(clientType)

        return try {
            withContext(Dispatchers.IO) {
                useRemoteService { remote ->
                    remote.setup(pathConfig.rootDir, appSettings.debugMode.value)
                    val maa = remote.maaCoreService
                    val isGlobal = clientType !in listOf("", "Official", "Bilibili")

                    copyTasksJson(pathConfig.cacheResourceDir)

                    if (!loadResIfExists(maa, pathConfig.rootDir)) {
                        _state.value = State.Failed("Failed to load main resource")
                        Timber.e("LoadResource failed: ${pathConfig.rootDir}")
                        return@useRemoteService Result.failure(Exception("Failed to load main resource"))
                    }

                    val followUps = buildList {
                        add(pathConfig.cacheDir)
                        if (isGlobal) {
                            pathConfig.globalResourceDir(clientType).parent?.let(::add)
                            pathConfig.globalCacheResourceDir(clientType).parent?.let(::add)
                        }
                    }

                    if (isGlobal) {
                        copyTasksJson(pathConfig.globalCacheResourceDir(clientType).absolutePath)
                    }

                    followUps.forEach { loadResIfExists(maa, it) }

                    _state.value = State.Ready
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "Resource loading exception")
            Result.failure(e)
        }
    }

    private suspend fun loadDepsInfo(clientType: String) {
        withContext(Dispatchers.IO) {
            listOf(
                async { resourceDataManager.load(clientType) },
                async { itemHelper.load() },
                async { activityManager.load(clientType) }
            )
        }.awaitAll()
    }

    private fun loadResIfExists(maa: MaaCoreService, parentDir: String): Boolean {
        val resDir = File(parentDir, "resource")
        if (!resDir.exists()) {
            Timber.d("Resource directory not found, skipping: ${resDir.absolutePath}")
            return true
        }
        return maa.LoadResource(parentDir).also { ok ->
            if (ok) Timber.i("LoadResource succeeded: $parentDir")
            else Timber.w("LoadResource failed: $parentDir")
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
                                (_state.value as? State.Failed)?.message ?: "Resource not loaded"
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
     * Copy tasks.json to tasks/tasks.json (compatible with new directory structure)
     */
    private fun copyTasksJson(resourcePath: String) {
        try {
            val src = File(resourcePath, "tasks.json")
            if (!src.exists()) return
            val destDir = File(resourcePath, "tasks").apply { mkdirs() }
            val dest = File(destDir, "tasks.json")
            if (dest.exists() && dest.length() == src.length() && dest.lastModified() >= src.lastModified()) {
                return
            }
            src.copyTo(dest, overwrite = true)
            Timber.d("copyTasksJson: ${src.absolutePath} -> ${dest.absolutePath}")
        } catch (e: Exception) {
            Timber.w(e, "copyTasksJson failed: $resourcePath")
        }
    }
}
