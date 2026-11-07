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
import com.aliothmoon.maameow.data.model.DepotMaintainConfig
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
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
import com.aliothmoon.maameow.data.model.autoProfileName
import com.aliothmoon.maameow.data.model.autoTaskName
import com.aliothmoon.maameow.data.model.normalizeTaskName
import com.aliothmoon.maameow.data.model.parseAutoProfileName
import com.aliothmoon.maameow.data.model.parseAutoTaskName
import com.aliothmoon.maameow.data.model.parseLegacyProfileName
import com.aliothmoon.maameow.data.model.taskTypeInfoForConfig
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                        name = autoProfileName(1),
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

    /**
     * 增删改指定配置档的「库存保持」计划。
     *
     * 库存数据页允许直接点物品改目标库存，此时用户并没有停在任务配置页，
     * 所以这里按 profileId 定位链——非活跃档也能改，活跃档走同一条写盘路径。
     * 该档还没有库存保持节点时就地新建一个（启用态，否则计划不会执行）。
     *
     * @param nodeId 计划所属节点。null = 新建计划，取该档第一个库存保持节点（启用优先），
     *   没有就建一个；非 null = 改已有计划，节点必须还在，找不到就整笔放弃——
     *   退回「第一个库存保持节点」会把 [transform] 里的下标套到另一个节点的计划上
     * @param transform 拿到该节点当前的 plans，返回新列表
     * @return 实际写入的节点 ID；配置档或节点不存在时返回空串
     */
    suspend fun updateDepotMaintainPlans(
        profileId: String,
        nodeId: String? = null,
        transform: (List<DepotMaintainPlan>) -> List<DepotMaintainPlan>,
    ): String = updateDepotMaintainConfig(profileId, nodeId) {
        it.copy(plans = transform(it.plans))
    }

    /**
     * 改指定配置档某个「库存保持」节点的整份配置。
     *
     * 计划之外的开关（更新仓库、理智药、自动代理倍率……）原先只能在任务配置页改，
     * 库存数据页的计划面板也要改它们，所以补上这条入口：定位、新建节点、写盘
     * 全部复用同一条路径，两边改的必然是同一条链上的同一个节点。
     *
     * @param nodeId 目标节点。null = 取该档第一个库存保持节点（启用优先），没有就建一个；
     *   非 null = 必须命中该节点，找不到就整笔放弃——退回「第一个库存保持节点」会把
     *   改动落到另一个节点的配置上
     * @param transform 拿到该节点当前的配置，返回新配置；**整份替换**，想保留的字段要自己带上
     * @return 实际写入的节点 ID；配置档或节点不存在时返回空串
     */
    suspend fun updateDepotMaintainConfig(
        profileId: String,
        nodeId: String? = null,
        transform: (DepotMaintainConfig) -> DepotMaintainConfig,
    ): String {
        _isLoaded.first { it }
        return locked {
            val target = _profiles.value.firstOrNull { it.id == profileId } ?: run {
                Timber.w("updateDepotMaintainConfig: profile %s not found", profileId)
                return@locked ""
            }
            // 活跃档的最新链在 _chain 上；非活跃档只能读 _profiles 里那份
            val isActive = profileId == _profileId.value
            val source = if (isActive) _chain.value else target.chain
            val updated = source.applyDepotConfig(nodeId, transform) ?: return@locked ""
            val (nodes, writtenNodeId) = updated
            if (isActive) {
                _chain.value = nodes
            }
            _profiles.value =
                _profiles.value.map { if (it.id == profileId) it.copy(chain = nodes) else it }
            doSync()
            Timber.d("updateDepotMaintainConfig: profile=%s node=%s", profileId, writtenNodeId)
            writtenNodeId
        }
    }

    /** @return null = 目标节点已不存在，调用方应放弃这次写入 */
    private fun List<TaskChainNode>.applyDepotConfig(
        nodeId: String?,
        transform: (DepotMaintainConfig) -> DepotMaintainConfig,
    ): Pair<List<TaskChainNode>, String>? {
        val nodes = toMutableList()
        if (nodeId != null) {
            val idx = nodes.indexOfFirst { it.id == nodeId && it.config is DepotMaintainConfig }
            if (idx < 0) {
                Timber.w("applyDepotConfig: 库存保持节点 %s 已不存在，放弃写入", nodeId)
                return null
            }
            val config = nodes[idx].config as DepotMaintainConfig
            nodes[idx] = nodes[idx].copy(config = transform(config))
            return nodes.toList() to nodes[idx].id
        }
        val existing = nodes.indexOfFirst { it.config is DepotMaintainConfig && it.enabled }
            .takeIf { it >= 0 }
            ?: nodes.indexOfFirst { it.config is DepotMaintainConfig }
        if (existing >= 0) {
            val config = nodes[existing].config as DepotMaintainConfig
            nodes[existing] = nodes[existing].copy(config = transform(config))
            return nodes.toList() to nodes[existing].id
        }
        val node = TaskChainNode(
            id = UUID.randomUUID().toString(),
            name = defaultTaskName(TaskTypeInfo.DEPOT_MAINTAIN),
            enabled = true,
            order = nodes.size,
            config = transform(DepotMaintainConfig()),
        )
        nodes.add(node)
        return nodes.toList() to node.id
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
        val currentProfiles = _profiles.value
        val target = currentProfiles.find { it.id == profileId } ?: run {
            Timber.w("switchProfile: profile %s not found", profileId)
            return
        }
        locked {
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
        Timber.d("Switched to profile: %s (%s)", target.name, profileId)
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
        _profileDeleted.tryEmit(profileId)
        Timber.d("Deleted profile: %s", profileId)
    }

    suspend fun renameProfile(profileId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_PROFILE_NAME_LENGTH) {
            Timber.w("renameProfile: invalid name length: %d", trimmed.length)
            return
        }
        locked {
            _profiles.value = _profiles.value.map { p ->
                if (p.id == profileId) p.copy(name = profileNameForStorage(p, trimmed)) else p
            }
            doSync()
        }
        Timber.d("Renamed profile %s to: %s", profileId, trimmed)
    }

    /** 输入名恰好等于自动档名（如「配置-2」）时继续存键，否则按自定义名存 */
    private fun profileNameForStorage(profile: TaskProfile, newName: String): String {
        val number = parseAutoProfileName(profile.name) ?: return newName
        return if (newName == context.getString(R.string.profile_default_name, number)) {
            profile.name
        } else {
            newName
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
                name = nextProfileName(savedProfiles), chain = duplicatedChain
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
        locked {
            _profiles.value = profiles
            _profileId.value = resolvedActiveId
            _chain.value = activeChain
            doSync()
        }
        // 导入是用户可见的终态操作（随后会提示「导入成功」），必须确认落盘再返回
        flush()
        Timber.d("Imported %d profiles, active: %s", profiles.size, resolvedActiveId)
    }

    /** 旧配置迁移，两个解码入口（DataStore 载入与导入备份）共用 */
    private fun List<TaskProfile>.migrated(): List<TaskProfile> = map { profile ->
        profile.copy(
            name = migrateProfileName(profile.name),
            chain = profile.chain.map { node ->
                node.copy(
                    name = migrateNodeName(node),
                    config = node.config.migrate(),
                )
            },
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

    private fun migrateProfileName(name: String): String {
        if (parseAutoProfileName(name) != null) return name
        val number = parseLegacyProfileName(name) ?: return name
        return autoProfileName(number)
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
            parseAutoProfileName(p.name) ?: parseLegacyProfileName(p.name)
        }.maxOrNull() ?: 0
        return autoProfileName(maxNum + 1)
    }
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
