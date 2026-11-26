package com.aliothmoon.maameow.data.preferences

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.InfrastConfig
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.data.model.OperProgressConfig
import com.aliothmoon.maameow.data.model.OperProgressResult
import com.aliothmoon.maameow.data.model.ReclamationConfig
import com.aliothmoon.maameow.data.model.RecruitConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.model.autoTaskName
import com.aliothmoon.maameow.data.model.defaultProfileName
import com.aliothmoon.maameow.data.model.normalizeTaskName
import com.aliothmoon.maameow.data.model.parseAutoProfileName
import com.aliothmoon.maameow.data.model.parseAutoTaskName
import com.aliothmoon.maameow.data.model.parseDefaultProfileName
import com.aliothmoon.maameow.data.model.taskTypeInfoForConfig
import com.aliothmoon.maameow.domain.models.RunDurationLimit
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import java.util.UUID


class TaskChainState(
    private val context: Context,
    private val appSettings: AppSettingsManager,
    private val achievementRepository: AchievementRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val json = JsonUtils.common

    companion object {
        private val Context.store: DataStore<Preferences> by preferencesDataStore(
            name = "task_chain"
        )

        private val PROFILES_KEY = stringPreferencesKey("profiles")
        private val ACTIVE_PROFILE_KEY = stringPreferencesKey("active_profile_id")

        private const val MAX_PROFILE_NAME_LENGTH = 20

        /** 迁移时按这些语言把旧默认名换成键（只有 zh/en 两套资源） */
        private val MIGRATION_NAME_TAGS = listOf("zh", "en")

        /** 重复节点/配置档的编号后缀，如「名称 2」里的 " 2" */
        private val TRAILING_NUMBER_REGEX = Regex(" \\d+$")
    }

    private val _chain = MutableStateFlow(buildDefaultChain())
    val chain: StateFlow<List<TaskChainNode>> = _chain.asStateFlow()

    private val _profiles = MutableStateFlow<List<TaskProfile>>(emptyList())
    val profiles: StateFlow<List<TaskProfile>> = _profiles.asStateFlow()

    private val _profileId = MutableStateFlow("")
    val profileId: StateFlow<String> = _profileId.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _profileDeleted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val profileDeleted: SharedFlow<String> = _profileDeleted.asSharedFlow()

    /**
     * 删除事件没有第二次机会：列表已经改完，调用方恰好被取消就会丢掉「清分片」，
     * 重启 load 又把死档读回来。
     *
     * 无订阅者时 `tryEmit` 直接成功但**不入队**（replay=0 → 事件丢弃），所以这里
     * 只记一条 warn，真正的兜底是 [ProfileShardStore.load] 按 profiles 对账。
     * 有订阅者时 `tryEmit` 成功即入队；仅在缓冲满（一次导入顶掉 ≥8 个档）时才退化成
     * 挂起 `emit`，用 `NonCancellable` 挡住「用户离开设置页 → viewModelScope 取消」。
     */
    private suspend fun emitProfileDeleted(profileId: String) {
        if (_profileDeleted.subscriptionCount.value == 0) {
            Timber.w("No profileDeleted subscriber, shard cleanup relies on next load(): %s", profileId)
        }
        if (!_profileDeleted.tryEmit(profileId)) {
            withContext(NonCancellable) { _profileDeleted.emit(profileId) }
        }
    }

    private val _lastUsedClientType = MutableStateFlow<String?>(null)

    /**
     * 串行化 [_chain] / [_profiles] 的读改写。
     *
     * 这两份状态有两个写入方：界面在 Main（改任务配置、库存保持计划），
     * 任务回调在 [Dispatchers.IO]（SubTaskHandler / TaskChainHandler 里的
     * `recordCreditFightCompleted`、`clearRecruitUseExpeditedFlags`）。
     * 都是「读出来 → 改 → 写回去」，不加锁就会互相覆盖：跑任务时保存一条
     * 库存保持计划，可能被同一时刻到达的信用战回调整份吞掉。
     *
     * 锁内只放非挂起代码，所以不会和 [_isLoaded] 互相等待。
     */
    private val chainLock = Mutex()

    /** 在链锁内做一次非挂起的读改写 */
    private suspend fun <T> locked(block: () -> T): T = chainLock.withLock { block() }

    private sealed interface PersistOp {
        data object Sync : PersistOp
        data class Flush(val done: CompletableDeferred<Unit>) : PersistOp
    }

    private val persistOps = Channel<PersistOp>(Channel.UNLIMITED)

    val clientType: String
        get() = resolveClientType(findFirstEnabledConfig<WakeUpConfig>()?.clientType, _lastUsedClientType.value)

    /** 当前 Profile 单独设置的运行时长上限（分钟），null 跟随全局 */
    val activeRunDurationLimitMinutes: Int?
        get() = _profiles.value.find { it.id == _profileId.value }?.runDurationLimitMinutes

    /** 最近一次成功启动的会话所用客户端；运行中即本次会话的服务器，不随切换 Profile 变 */
    val lastUsedClientType: String?
        get() = _lastUsedClientType.value

    val clientTypeFlow: Flow<String>
        get() = combine(
            firstEnabledConfigFlow<WakeUpConfig>().map { it?.clientType },
            _lastUsedClientType,
        ) { wakeUp, lastUsed -> resolveClientType(wakeUp, lastUsed) }
            .distinctUntilChanged()

    private fun resolveClientType(wakeUp: String?, lastUsed: String?): String =
        wakeUp ?: lastUsed ?: "Official"

    private fun doSync() {
        persistOps.trySend(PersistOp.Sync)
    }

    /**
     * 等到本 Flush 之前的 Sync 全部处理完。
     * 期间若有写盘失败，抛出 [IOException]（[importProfiles] 等「确认落盘」路径依赖此契约）。
     */
    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        persistOps.send(PersistOp.Flush(done))
        done.await()
    }

    private suspend fun doConsume() {
        var pendingError: IOException? = null
        var carried: PersistOp? = null
        while (true) {
            val op = carried ?: persistOps.receiveCatching().getOrNull() ?: break
            carried = null
            try {
                when (op) {
                    PersistOp.Sync -> {
                        // sync 写的是当下全量快照，排队中的连续 Sync 一次写完
                        carried = skipQueuedSyncs()
                        try {
                            sync()
                            pendingError = null
                        } catch (e: IOException) {
                            Timber.e(e, "写入任务链配置失败")
                            pendingError = e
                        }
                    }

                    is PersistOp.Flush -> {
                        val err = pendingError
                        if (err != null) {
                            pendingError = null
                            op.done.completeExceptionally(err)
                        } else {
                            op.done.complete(Unit)
                        }
                    }
                }
            } catch (e: Throwable) {
                Timber.e(e, "任务链配置持久化队列处理失败")
                if (op is PersistOp.Flush) {
                    op.done.completeExceptionally(e)
                } else if (e is IOException) {
                    pendingError = e
                }
            }
        }
    }

    /** 取走队首连续的 Sync，返回之后第一个别的操作 */
    private fun skipQueuedSyncs(): PersistOp? {
        while (true) {
            val next = persistOps.tryReceive().getOrNull() ?: return null
            if (next !is PersistOp.Sync) return next
        }
    }

    private suspend fun sync() {
        context.store.edit { prefs ->
            prefs[PROFILES_KEY] = json.encodeToString<List<TaskProfile>>(_profiles.value)
            prefs[ACTIVE_PROFILE_KEY] = _profileId.value
        }
    }

    init {
        syncScope.launch { doConsume() }
        scope.launch {
            try {
                val prefs = context.store.data.first()
                val storedActiveId = prefs[ACTIVE_PROFILE_KEY]

                val rawProfiles = prefs[PROFILES_KEY]?.let {
                    runCatching { json.decodeFromString<List<TaskProfile>>(it) }.onFailure { e ->
                        Timber.e(e, "TaskChainState decodeFromString error")
                    }.getOrNull()
                }

                // 旧存档里的默认名（当时语言）迁移成键，之后跟着语言走
                val migratedProfiles = rawProfiles?.migrated()
                val needsNameMigration = rawProfiles != null && rawProfiles != migratedProfiles

                // 迁出生息演算会换成新列表
                val storedProfiles = migratedProfiles?.let { adoptReclamation(it, storedActiveId) }

                val needsDefaultProfile = storedProfiles.isNullOrEmpty()

                val profiles = storedProfiles?.takeIf { it.isNotEmpty() } ?: listOf(
                    TaskProfile(
                        name = defaultProfileName(context, 1),
                        chain = buildDefaultChain(),
                    )
                )

                val activeProfile =
                    profiles.firstOrNull { it.id == storedActiveId } ?: profiles.first()

                // 迁出生息演算会换成新列表；改名迁移同样需要落盘
                val needsSync = needsDefaultProfile || storedActiveId != activeProfile.id ||
                        needsNameMigration || storedProfiles !== migratedProfiles

                locked {
                    _profiles.value = profiles
                    _profileId.value = activeProfile.id
                    _chain.value = activeProfile.chain
                    _isLoaded.value = true
                }

                if (needsSync) {
                    doSync()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load profiles")
                _isLoaded.value = true
            }
        }
    }


    suspend fun addNode(typeInfo: TaskTypeInfo, afterIndex: Int = -1): String {
        val nodeId = mutate {
            val node = TaskChainNode(
                id = UUID.randomUUID().toString(),
                name = autoTaskName(typeInfo),
                enabled = true,
                config = typeInfo.defaultConfig()
            )
            if (afterIndex < 0 || afterIndex >= it.size) {
                it.add(node)
            } else {
                it.add(afterIndex + 1, node)
            }
            Timber.d("Added node: %s (%s)", node.name, typeInfo.name)
            node.id
        }
        achievementRepository.report {
            event = AchievementEvents.TASK_NODE_ADDED
        }
        return nodeId
    }

    suspend fun removeNode(nodeId: String) {
        mutate {
            it.removeAll { iter ->
                iter.id == nodeId
            }
            Timber.d("Removed node: %s", nodeId)
        }
        achievementRepository.report {
            event = AchievementEvents.TASK_NODE_REMOVED
        }
    }

    suspend fun duplicateNode(nodeId: String): String {
        return mutate { current ->
            val idx = current.indexOfFirst { it.id == nodeId }
            if (idx >= 0) {
                val src = current[idx]
                val copy = src.copy(
                    id = UUID.randomUUID().toString(),
                    name = duplicateTaskName(src.name, current.map { it.name }),
                )
                current.add(idx + 1, copy)
                Timber.d("Duplicated node %s → %s (\"%s\")", nodeId, copy.id, copy.name)
                copy.id
            } else {
                Timber.w("duplicateNode: node %s not found", nodeId)
                ""
            }
        }
    }

    suspend fun renameNode(nodeId: String, newName: String) {
        val trimmed = newName.trim()
        mutate { current ->
            val idx = current.indexOfFirst { it.id == nodeId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(name = nodeNameForStorage(current[idx], trimmed))
                Timber.d("Renamed node %s to: %s", nodeId, trimmed)
            } else {
                Timber.w("renameNode: node %s not found", nodeId)
            }
        }
    }

    /**
     * 输入名恰好等于该类型的默认名（含重复编号后缀）时仍然存自动键，这样名字继续跟随语言；
     * 其余情况按自定义名原样存。
     */
    private fun nodeNameForStorage(node: TaskChainNode, newName: String): String {
        val parsed = parseAutoTaskName(node.name)
        val typeInfo = parsed?.first ?: taskTypeInfoForConfig(node.config) ?: return newName
        val suffix = parsed?.second ?: ""
        return if (normalizeTaskName(newName) == normalizeTaskName(defaultTaskName(typeInfo) + suffix)) {
            autoTaskName(typeInfo) + suffix
        } else {
            newName
        }
    }

    suspend fun setNodeEnabled(nodeId: String, enabled: Boolean) {
        mutate { current ->
            val idx = current.indexOfFirst { it.id == nodeId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(enabled = enabled)
                Timber.d("Set node %s enabled: %s", nodeId, enabled)
            } else {
                Timber.w("setNodeEnabled: node %s not found", nodeId)
            }
        }
    }

    suspend fun updateNodeConfig(nodeId: String, config: TaskParamProvider) {
        mutate { current ->
            val idx = current.indexOfFirst { it.id == nodeId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(config = config)
            } else {
                Timber.w("updateNodeConfig: node %s not found", nodeId)
            }
        }
    }

    /** 以回调的节点 ID 定位，切换配置后也不会写到另一个信用任务 */
    suspend fun recordCreditFightCompleted(nodeId: String, date: String) {
        updateMallCompletion(nodeId) { it.copy(creditFightLastDate = date) }
    }

    suspend fun recordVisitFriendsCompleted(nodeId: String, date: String) {
        updateMallCompletion(nodeId) { it.copy(visitFriendsLastDate = date) }
    }

    suspend fun recordOperProgress(
        nodeId: String,
        results: List<OperProgressResult>,
        removeFinished: Boolean = false,
    ) {
        val update = { node: TaskChainNode ->
            val config = node.config
            if (node.id == nodeId && config is OperProgressConfig) {
                node.copy(config = config.applyResults(results, removeFinished))
            } else node
        }
        // 回调按原节点写回，切换配置也能保留培养进度
        mutate(others = { chain -> chain.map(update) }) { current -> current.replaceAll { update(it) } }
    }

    private suspend fun updateMallCompletion(nodeId: String, transform: (MallConfig) -> MallConfig) {
        val update = { node: TaskChainNode ->
            val config = node.config
            if (node.id == nodeId && config is MallConfig) {
                node.copy(config = transform(config))
            } else node
        }
        // 运行中可能已切走 Profile，非当前 Profile 的链也要找
        mutate(others = { chain -> chain.map(update) }) { current -> current.replaceAll { update(it) } }
    }

    suspend fun reorderNodes(fromIndex: Int, toIndex: Int) {
        mutate { current ->
            require(fromIndex in current.indices) { "fromIndex out of bounds: $fromIndex" }
            require(toIndex in current.indices) { "toIndex out of bounds: $toIndex" }
            val node = current.removeAt(fromIndex)
            current.add(toIndex, node)
            Timber.d("Moved node from %d to %d", fromIndex, toIndex)
        }
    }

    suspend fun clearRecruitUseExpeditedFlags() {
        mutate { current ->
            for (i in current.indices) {
                val node = current[i]
                if (!node.enabled) continue
                when (val cfg = node.config) {
                    is RecruitConfig -> if (cfg.useExpedited) {
                        current[i] = node.copy(config = cfg.copy(useExpedited = false))
                        Timber.d(
                            "clearRecruitUseExpeditedFlags on node %s", node.id
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    /**
     * 自定义基建任务链完成后，将目标节点的 planSelect 自动切到下一个计划。
     *
     * 对齐 WPF `InfrastSettingsUserControlModel.IncreaseCustomInfrastPlanIndex`:
     * - 仅 Custom 模式生效
     * - planSelect == -1(时间轮换)不切
     * - planSelect 越界或计划列表未就绪直接放弃
     * - 自增后超出范围回环到 0
     *
     * 返回 Pair(新索引, 新计划名)，若未满足切换条件返回 null。
     */
    suspend fun incrementCustomInfrastPlanSelect(nodeId: String): Pair<Int, String?>? {
        val node = _chain.value.firstOrNull { it.id == nodeId } ?: run {
            Timber.d("incrementCustomInfrastPlanSelect: node %s not found", nodeId)
            return null
        }
        val cfg = node.config as? InfrastConfig ?: return null
        if (cfg.mode != com.aliothmoon.maameow.domain.enums.InfrastMode.Custom) return null
        if (cfg.customInfrastPlanSelect < 0) return null
        val count = cfg.customPlanNames.size
        if (count <= 0) {
            Timber.d("incrementCustomInfrastPlanSelect: plan names empty for node %s", nodeId)
            return null
        }
        if (cfg.customInfrastPlanSelect >= count) return null
        val next = (cfg.customInfrastPlanSelect + 1) % count
        updateNodeConfig(nodeId, cfg.copy(customInfrastPlanSelect = next))
        return next to cfg.customPlanNames.getOrNull(next)
    }

    fun saveLastUsedClientType(clientType: String) {
        _lastUsedClientType.value = clientType
    }

    inline fun <reified T : TaskParamProvider> findFirstEnabledConfig(): T? {
        return chain.value.filter { it.enabled }.firstNotNullOfOrNull { it.config as? T }
    }

    inline fun <reified T : TaskParamProvider> firstEnabledConfigFlow(): Flow<T?> {
        return chain.map { nodes ->
            nodes.filter { it.enabled }.firstNotNullOfOrNull { it.config as? T }
        }
    }

    // ========== Profile 管理 ==========

    suspend fun switchProfile(profileId: String) {
        var targetName: String? = null
        locked {
            // 读也放进锁内：与 createProfile/renameProfile/reorderProfiles 并发时，
            // 锁外的快照会在回写时把它们的改动整段覆盖（importProfiles 已修过同类问题）
            val currentProfiles = _profiles.value
            val target = currentProfiles.find { it.id == profileId } ?: run {
                Timber.w("switchProfile: profile %s not found", profileId)
                return@locked
            }
            targetName = target.name
            // 保存当前链到旧 Profile
            val updatedProfiles = currentProfiles.map { p ->
                if (p.id == _profileId.value) p.copy(chain = _chain.value) else p
            }
            // 加载新 Profile 的链
            _chain.value = target.chain
            _profileId.value = profileId
            _profiles.value = updatedProfiles
            // 持久化
            doSync()
        }
        Timber.d("Switched to profile: %s (%s)", targetName, profileId)
    }

    suspend fun createProfile(): String {
        val newProfileId = locked {
            val currentProfiles = _profiles.value
            // 先保存当前活跃 Profile 的链
            val savedProfiles = currentProfiles.map { p ->
                if (p.id == _profileId.value) p.copy(chain = _chain.value) else p
            }
            val newProfile = TaskProfile(
                name = nextProfileName(savedProfiles), chain = buildDefaultChain()
            )
            // 切换到新 Profile
            _chain.value = newProfile.chain
            _profileId.value = newProfile.id
            _profiles.value = savedProfiles + newProfile
            doSync()
            newProfile.id
        }
        Timber.d("Created profile: %s", newProfileId)
        return newProfileId
    }

    suspend fun removeProfile(profileId: String) {
        val removed = locked {
            val currentProfiles = _profiles.value
            if (currentProfiles.size <= 1) {
                Timber.w("deleteProfile: cannot delete last profile")
                return@locked false
            }
            // 先保存当前链
            val savedProfiles = currentProfiles.map { p ->
                if (p.id == _profileId.value) p.copy(chain = _chain.value) else p
            }
            val remaining = savedProfiles.filter { it.id != profileId }
            if (remaining.size == savedProfiles.size) {
                Timber.w("deleteProfile: profile %s not found", profileId)
                return@locked false
            }
            // 若删除的是活跃 Profile,切换到列表第一个
            val newActiveId = if (_profileId.value == profileId) {
                val first = remaining.first()
                _chain.value = first.chain
                first.id
            } else {
                _profileId.value
            }
            _profileId.value = newActiveId
            _profiles.value = remaining
            doSync()
            true
        }
        if (!removed) return
        emitProfileDeleted(profileId)
        Timber.d("Deleted profile: %s", profileId)
    }

    suspend fun renameProfile(profileId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_PROFILE_NAME_LENGTH) {
            Timber.w("renameProfile: invalid name length: %d", trimmed.length)
            return
        }
        // 保留键是内部存储格式（载入时会被迁移成当前语言的字面量）：允许用户改成
        // `@profile:3` 会让界面显示「配置-3」而存档是键，下次启动名字被静默换掉
        if (trimmed.startsWith("@profile:")) {
            Timber.w("renameProfile: reserved profile name: %s", trimmed)
            return
        }
        locked {
            _profiles.value = _profiles.value.map { p ->
                if (p.id == profileId) p.copy(name = trimmed) else p
            }
            doSync()
        }
        Timber.d("Renamed profile %s to: %s", profileId, trimmed)
    }

    /** [minutes] 为 null 时恢复跟随全局设置 */
    suspend fun setProfileRunDurationLimit(profileId: String, minutes: Int?) {
        val clamped = minutes?.coerceIn(RunDurationLimit.MIN_MINUTES, RunDurationLimit.MAX_MINUTES)
        locked {
            val current = _profiles.value
            val changed = current.any { it.id == profileId && it.runDurationLimitMinutes != clamped }
            if (!changed) return@locked
            _profiles.value = current.map { p ->
                if (p.id == profileId) p.copy(runDurationLimitMinutes = clamped) else p
            }
            doSync()
        }
    }

    suspend fun duplicateProfile(profileId: String): String? {
        val newProfileId = locked {
            val currentProfiles = _profiles.value
            // 先保存当前活跃 Profile 的链
            val savedProfiles = currentProfiles.map { p ->
                if (p.id == _profileId.value) p.copy(chain = _chain.value) else p
            }
            val source = savedProfiles.find { it.id == profileId } ?: run {
                Timber.w("duplicateProfile: profile %s not found", profileId)
                return@locked null
            }
            // 复制链时为每个节点生成新 ID
            val duplicatedChain = source.chain.map { it.copy(id = UUID.randomUUID().toString()) }
            val newProfile = TaskProfile(
                name = nextProfileName(savedProfiles),
                chain = duplicatedChain,
                runDurationLimitMinutes = source.runDurationLimitMinutes,
            )
            _profiles.value = savedProfiles + newProfile
            doSync()
            newProfile.id
        }
        Timber.d("Duplicated profile %s as: %s", profileId, newProfileId)
        return newProfileId
    }

    suspend fun reorderProfiles(fromIndex: Int, toIndex: Int) {
        val current = _profiles.value
        if (fromIndex !in current.indices || toIndex !in current.indices) {
            Timber.w(
                "reorderProfiles: invalid index from=%d to=%d size=%d",
                fromIndex,
                toIndex,
                current.size
            )
            return
        }
        if (fromIndex == toIndex) return

        locked {
            // 顺便把当前未保存的链快照写回 active profile, 避免重排时丢失正在编辑的内容
            val savedProfiles = _profiles.value.map { p ->
                if (p.id == _profileId.value) p.copy(chain = _chain.value) else p
            }.toMutableList()
            val moved = savedProfiles.removeAt(fromIndex)
            savedProfiles.add(toIndex, moved)

            _profiles.value = savedProfiles
            doSync()
        }
        Timber.d("Reordered profile from %d to %d", fromIndex, toIndex)
    }

    // ========== 内部工具方法 ==========

    /** 改链统一走这里；[others] 非空时顺带改非当前 Profile 的链 */
    private suspend inline fun <T> mutate(
        noinline others: ((List<TaskChainNode>) -> List<TaskChainNode>)? = null,
        crossinline block: (MutableList<TaskChainNode>) -> T
    ): T {
        _isLoaded.first { it }
        return locked {
            val current = _chain.value.toMutableList()
            val ret = block(current)
            reindex(current)
            // 没改动就不写盘，滑块、输入框会反复提交同值
            if (others == null && current == _chain.value) return@locked ret
            val snapshot = current.toList()
            _chain.value = snapshot
            _profiles.value = _profiles.value.map { p ->
                when {
                    p.id == _profileId.value -> p.copy(chain = snapshot)
                    others != null -> p.copy(chain = others(p.chain))
                    else -> p
                }
            }
            doSync()
            ret
        }
    }

    private fun reindex(nodes: MutableList<TaskChainNode>) {
        for (i in nodes.indices) {
            if (nodes[i].order != i) nodes[i] = nodes[i].copy(order = i)
        }
    }

    private fun buildDefaultChain(): List<TaskChainNode> {
        return TaskTypeInfo.entries.filter { it.inDefaultChain }.mapIndexed { index, info ->
            TaskChainNode(
                name = autoTaskName(info),
                enabled = info.enabledByDefault,
                order = index,
                config = info.defaultConfig()
            )
        }
    }

    /** 当前选中语言下的默认任务名（新建节点、以及改回默认名时的判等） */
    private fun defaultTaskName(typeInfo: TaskTypeInfo): String =
        defaultNameIn(typeInfo, resolveSelectedLanguage(appSettings.language.value).tag)

    private fun defaultNameIn(typeInfo: TaskTypeInfo, languageTag: String): String {
        val localizedContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(languageTag))
            })
        return typeInfo.defaultName(localizedContext)
    }

    suspend fun importProfiles(rawProfiles: List<TaskProfile>, activeId: String) {
        val profiles = adoptReclamation(rawProfiles.migrated(), activeId)
        val resolvedActiveId =
            profiles.find { it.id == activeId }?.id ?: profiles.firstOrNull()?.id ?: return
        val activeChain = profiles.find { it.id == resolvedActiveId }?.chain ?: buildDefaultChain()
        var previousIds: List<String> = emptyList()
        locked {
            // 在锁内取旧列表：与 createProfile 并发时，锁外的快照可能已经过时
            previousIds = _profiles.value.map { it.id }
            _profiles.value = profiles
            _profileId.value = resolvedActiveId
            _chain.value = activeChain
            doSync()
        }
        // 导入会整体替换列表：被顶掉的档不走 removeProfile，必须补发删除事件，
        // 否则它们的分片留在磁盘上（UI 看不到、重启 load 又会读回，导入同 id 备份时还会复活）。
        // 交接语义见 emitProfileDeleted：有订阅者才真正入队，无订阅者靠 load() 对账兜底。
        previousIds
            .filter { id -> profiles.none { it.id == id } }
            .forEach { emitProfileDeleted(it) }
        // 导入是用户可见的终态操作（随后会提示「导入成功」），必须确认落盘再返回
        flush()
        Timber.d("Imported %d profiles, active: %s", profiles.size, resolvedActiveId)
    }

    /** 旧配置迁移与取值校正，两个解码入口（DataStore 载入与导入备份）共用 */
    private fun List<TaskProfile>.migrated(): List<TaskProfile> = map { profile ->
        profile.copy(
            name = migrateProfileName(profile.name),
            chain = profile.chain.map { node ->
                node.copy(
                    name = migrateNodeName(node),
                    config = node.config.migrate(),
                )
            },
            // 手改的备份可能越界，界面显示与实际生效须一致
            runDurationLimitMinutes = profile.runDurationLimitMinutes
                ?.coerceIn(RunDurationLimit.MIN_MINUTES, RunDurationLimit.MAX_MINUTES),
        )
    }

    /** 旧存档里的默认名（创建时那门语言）换成键，自定义名原样保留 */
    private fun migrateNodeName(node: TaskChainNode): String {
        if (parseAutoTaskName(node.name) != null) return node.name
        val typeInfo = taskTypeInfoForConfig(node.config) ?: return node.name
        val suffix = TRAILING_NUMBER_REGEX.find(node.name)?.value ?: ""
        val base = node.name.removeSuffix(suffix)
        val isDefaultName = MIGRATION_NAME_TAGS.any {
            normalizeTaskName(defaultNameIn(typeInfo, it)) == normalizeTaskName(base)
        }
        return if (isDefaultName) autoTaskName(typeInfo) + suffix else node.name
    }

    /** 旧存档迁移：键 `@profile:N` 换成当前语言下的字面量，已有字面量原样保留 */
    private fun migrateProfileName(name: String): String {
        val number = parseAutoProfileName(name) ?: return name
        return defaultProfileName(context, number)
    }

    /**
     * 生息演算设置迁入工具箱，写入成功才返回移除旧节点后的新列表
     * 写入失败原样返回，下次载入再试，不能连累任务链载入或导入
     */
    private suspend fun adoptReclamation(profiles: List<TaskProfile>, activeId: String?): List<TaskProfile> {
        val (migrated, legacy) = profiles.extractReclamation(activeId)
        legacy ?: return profiles
        return try {
            appSettings.adoptReclamationConfig(legacy)
            migrated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "生息演算设置迁入工具箱失败")
            profiles
        }
    }

    private fun nextProfileName(profiles: List<TaskProfile>): String {
        val maxNum = profiles.mapNotNull { p ->
            parseDefaultProfileName(context, p.name) ?: trailingNumber(p.name)
        }.maxOrNull() ?: 0
        return defaultProfileName(context, maxNum + 1)
    }

    /**
     * 切过界面语言后，旧默认名可能是别的语言的字面量（`Profile 2` / `配置-2`），
     * 当前语言的正则认不出来；取末尾数字参与编号去重，避免新档与旧档编号撞车。
     */
    private fun trailingNumber(name: String): Int? =
        Regex("(\\d+)\\s*$").find(name.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()
}

/**
 * 生息演算随上游迁到工具箱：各链去掉生息演算节点，
 * 当前配置档优先取第一个节点的设置交给工具箱，对齐 WPF ToolboxSectionMigrationConverter
 */
internal fun List<TaskProfile>.extractReclamation(
    activeId: String?,
): Pair<List<TaskProfile>, ReclamationConfig?> {
    val legacy = sortedByDescending { it.id == activeId }
        .firstNotNullOfOrNull { profile -> profile.chain.firstNotNullOfOrNull { it.config as? ReclamationConfig } }
        ?: return this to null
    val profiles = map { profile ->
        profile.copy(
            chain = profile.chain.filterNot { it.config is ReclamationConfig }
                .mapIndexed { index, node -> node.copy(order = index) }
        )
    }
    return profiles to legacy
}

/** 复制任务的名字：去掉末尾 " N" 得基础名，取最小未占用编号（从 2 起，1 留给源名称） */
internal fun duplicateTaskName(name: String, existing: List<String>): String {
    val baseName = name.replace(Regex(" \\d+$"), "")
    val pattern = Regex("^${Regex.escape(baseName)} (\\d+)$")
    val used = existing.mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.toSet()
    val next = generateSequence(2) { it + 1 }.first { it !in used }
    return "$baseName $next"
}
