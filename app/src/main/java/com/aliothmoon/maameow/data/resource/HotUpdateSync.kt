package com.aliothmoon.maameow.data.resource

import android.os.SystemClock
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.MaaApiService
import com.aliothmoon.maameow.data.api.MaaApiService.FetchResult
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.ClientResourceProfile
import com.aliothmoon.maameow.domain.service.CoreFilesGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 热更唯一联网入口：只落盘并递增 [revision]，读方按 revision 从磁盘重载 */
class HotUpdateSync(
    private val api: MaaApiService,
    private val chainState: TaskChainState,
    private val coreFiles: Lazy<CoreFilesGate>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** 计入深睡的时钟，息屏挂机时单调时钟不走 */
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val inFlight = HashMap<String, Deferred<Unit>>()
    private val checkedAt = ConcurrentHashMap<String, Long>()

    // 刷新排队，旧响应不会覆盖新的
    private val refreshMutex = Mutex()

    fun stageActivity(): String? = api.cached(MaaApi.STAGE_ACTIVITY_API)

    fun start() {
        scope.launch {
            chainState.isLoaded.first { it }
            chainState.clientTypeFlow
                .map(ClientResourceProfile::metadataClient)
                .distinctUntilChanged()
                .collect { refreshAsync(it) }
        }
        scope.launch {
            while (isActive) {
                delay(nextCheckDelayMs())
                refreshAsync(ClientResourceProfile.metadataClient(chainState.clientType))
            }
        }
    }

    fun refreshAsync(client: String): Deferred<Unit> = synchronized(inFlight) {
        inFlight[client]?.takeIf { it.isActive }
            ?: scope.async { refresh(client) }.also { inFlight[client] = it }
    }

    /** 超时不取消请求，先用磁盘上的 */
    suspend fun awaitFresh(clientType: String, budget: Duration = AWAIT_BUDGET) {
        val client = ClientResourceProfile.metadataClient(clientType)
        if (checkedAt[client]?.let { now() - it < MAX_AGE.inWholeMilliseconds } == true) return
        withTimeoutOrNull(budget) { refreshAsync(client).await() }
    }

    private suspend fun refresh(client: String) = refreshMutex.withLock {
        try {
            val apis = buildList {
                add(MaaApi.STAGE_ACTIVITY_API)
                add(MaaApi.TASKS_API)
                if (client != OFFICIAL) add(MaaApi.getGlobalTasksApi(client))
            }
            val results = coroutineScope { apis.map { async { it to api.fetch(it) } }.awaitAll() }
            val changed = results.mapNotNull { (path, r) -> (r as? FetchResult.Changed)?.let { path to it } }
            var committed = true
            if (changed.isNotEmpty()) {
                coreFiles.value.whenIdle {
                    committed = changed.map { (path, r) -> api.commit(path, r) }.all { it }
                    // 写失败也可能已改了一部分，照样让读的一方重载
                    _revision.update { it + 1 }
                }
                Timber.i("Hot update committed: %s ok=%s", changed.map { it.first }, committed)
            }
            if (committed && results.all { it.second != null }) {
                checkedAt[client] = now()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Hot update refresh failed: %s", client)
        }
    }

    /** 鹰历日期切换点 04:00 / 16:00 后 0~10 分钟 */
    private fun nextCheckDelayMs(): Long {
        val zone = ServerTimezone.getServerZone(chainState.clientType)
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        val next = listOf(today.atTime(4, 0), today.atTime(16, 0), today.plusDays(1).atTime(4, 0))
            .map { it.atZone(zone) }
            .first { it.isAfter(now) }
        return ChronoUnit.MILLIS.between(now, next) + (Math.random() * 10.minutes.inWholeMilliseconds).toLong()
    }

    companion object {
        private const val OFFICIAL = "Official"
        val AWAIT_BUDGET = 5.seconds
        private val MAX_AGE = 1.minutes
    }
}
