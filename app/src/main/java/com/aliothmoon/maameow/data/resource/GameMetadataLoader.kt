package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.ClientResourceProfile
import com.aliothmoon.maameow.domain.models.GameMetadataKey
import com.aliothmoon.maameow.domain.service.GameMetadataGate
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.state.ResourceInitState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * 界面用的本地游戏数据：干员、公招 Tag、肉鸽推荐、地图、材料、活动关卡
 *
 * 只读 App 侧磁盘，不依赖提权服务也不联网；独立目录模式下 App 也读不到 core 那份
 * 按「客户端档 + 显示语言 + 资源版本 + 热更版本」加载，同一档不重复解析
 */
class GameMetadataLoader(
    private val resourceDataManager: ResourceDataManager,
    private val itemHelper: ItemHelper,
    private val activityManager: ActivityManager,
    private val hotUpdateSync: HotUpdateSync,
    private val resourceInitService: ResourceInitService,
    private val chainState: TaskChainState,
    private val appSettings: AppSettingsManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : GameMetadataGate {

    sealed interface State {
        /** 本地资源未就绪 */
        data object Idle : State
        data class Loading(val key: GameMetadataKey) : State
        data class Ready(val key: GameMetadataKey) : State
        data class Failed(val key: GameMetadataKey, val error: Throwable) : State
    }

    private val started = AtomicBoolean(false)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // 失败档靠它重放
    private val retryToken = MutableStateFlow(0)

    // 只在收集协程里读写
    private var itemsRevision: Int? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // 未读盘时 clientType 会先落到 Official
            chainState.isLoaded.first { it }
            combine(targetFlow(), retryToken) { key, _ -> key }
                // 读文件取消不掉，每轮跑完再取最新
                .conflate()
                .collect { key ->
                    if (key == null) _state.value = State.Idle else load(key)
                }
        }
    }

    /** 每次收集都现读各来源，不会落后于调用方刚做的切换 */
    private fun targetFlow(): Flow<GameMetadataKey?> = combine(
        resourceInitService.state.map { it is ResourceInitState.Ready },
        resourceInitService.revision,
        chainState.clientTypeFlow.map(ClientResourceProfile::metadataClient),
        appSettings.displayLanguageFlow,
        hotUpdateSync.revision,
    ) { ready, revision, client, language, hotUpdate ->
        if (ready) GameMetadataKey(client, language, revision, hotUpdate) else null
    }.distinctUntilChanged()

    private suspend fun load(key: GameMetadataKey) {
        if (_state.value == State.Ready(key)) return
        _state.value = State.Loading(key)
        val mark = TimeSource.Monotonic.markNow()
        val reloadItems = itemsRevision != key.revision
        // 活动关卡单独兜错，不连累干员表
        runCatching { activityManager.load(key.clientType, hotUpdateSync.stageActivity()) }
            .onFailure { Timber.e(it, "Activity data load failed: %s", key) }
        try {
            coroutineScope {
                launch { resourceDataManager.load(key.clientType, key.language, key.revision) }
                // 材料表只跟资源版本有关
                if (reloadItems) launch { itemHelper.load() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 不能让异常结束收集；等待方据此立即放弃，下次等待时重试
            Timber.e(e, "UI metadata load failed: %s", key)
            _state.value = State.Failed(key, e)
            return
        }
        if (reloadItems) itemsRevision = key.revision
        _state.value = State.Ready(key)
        Timber.i("UI metadata ready %s in %dms", key, mark.elapsedNow().inWholeMilliseconds)
    }

    override suspend fun awaitCurrent(timeout: Duration): Boolean {
        val ready = withTimeoutOrNull(timeout) {
            chainState.isLoaded.first { it }
            retryIfFailed()
            combine(targetFlow(), _state, resourceInitService.state) { want, have, init ->
                when {
                    want != null && have == State.Ready(want) -> true
                    want != null && have is State.Failed && have.key == want -> false
                    // 等不来，不白耗超时
                    init is ResourceInitState.Failed -> false
                    else -> null
                }
            }.filterNotNull().first()
        } ?: false
        if (!ready) {
            Timber.w("UI metadata not ready: %s", _state.value)
        }
        return ready
    }

    /** 先改成 Loading 再重放，等待方不会撞上旧的 Failed 立即返回 */
    private fun retryIfFailed() {
        val failed = _state.value as? State.Failed ?: return
        if (_state.compareAndSet(failed, State.Loading(failed.key))) {
            retryToken.update { it + 1 }
        }
    }
}
