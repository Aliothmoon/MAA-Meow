package com.aliothmoon.maameow.data.preferences

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.InfrastConfig
import com.aliothmoon.maameow.data.model.ProfileSequenceEntry
import com.aliothmoon.maameow.data.model.RecruitConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.TaskSequenceConfig
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.remote.PermissionGrantRequest
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
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
        private val PROFILE_SEQUENCE_KEY = stringPreferencesKey("profile_sequence")
        private val SEQUENCE_CONFIGS_KEY = stringPreferencesKey("sequence_configs")
        private val ACTIVE_SEQUENCE_CONFIG_KEY =
            stringPreferencesKey("active_sequence_config_id")
        private val PROFILE_SEQUENCE_ENABLED_KEY =
            booleanPreferencesKey("profile_sequence_enabled")

        private const val PROFILE_NAME_PREFIX = "配置-"
        const val MAX_PROFILE_NAME_LENGTH = 20
        const val MAX_SEQUENCE_NAME_LENGTH = 20
        const val MAX_SEQUENCE_ENTRIES = 20
    }

    private val sequenceMutex = Mutex()

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

    private val _sequenceConfigs = MutableStateFlow<List<TaskSequenceConfig>>(emptyList())
    val sequenceConfigs: StateFlow<List<TaskSequenceConfig>> = _sequenceConfigs.asStateFlow()

    private val _activeSequenceConfigId = MutableStateFlow("")
    val activeSequenceConfigId: StateFlow<String> = _activeSequenceConfigId.asStateFlow()

    private val _profileSequence = MutableStateFlow<List<ProfileSequenceEntry>>(emptyList())
    val profileSequence: StateFlow<List<ProfileSequenceEntry>> = _profileSequence.asStateFlow()

    private val _profileSequenceEnabled = MutableStateFlow(true)
    val profileSequenceEnabled: StateFlow<Boolean> = _profileSequenceEnabled.asStateFlow()

    private val _lastUsedClientType = MutableStateFlow<String?>(null)

    private sealed interface PersistOp {
        data object Sync : PersistOp
        data class Flush(val done: CompletableDeferred<Unit>) : PersistOp
    }

    private val persistOps = Channel<PersistOp>(Channel.UNLIMITED)

    val clientType: String
        get() = getClientTypeOrNull() ?: "Official"

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
        for (op in persistOps) {
            try {
                when (op) {
                    PersistOp.Sync -> {
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

                val storedProfiles = prefs[PROFILES_KEY]?.let {
                    runCatching { json.decodeFromString<List<TaskProfile>>(it) }.onFailure { e ->
                        Timber.e(e, "TaskChainState decodeFromString error")
                    }.getOrNull()
                }

                val needsDefaultProfile = storedProfiles.isNullOrEmpty()

                val profiles = storedProfiles?.takeIf { it.isNotEmpty() } ?: listOf(
                    TaskProfile(
                        name = "${PROFILE_NAME_PREFIX}1",
                        chain = buildDefaultChain(),
                    )
                )

                val storedActiveId = prefs[ACTIVE_PROFILE_KEY]

                val activeProfile =
                    profiles.firstOrNull { it.id == storedActiveId } ?: profiles.first()

                val needsSync = needsDefaultProfile || storedActiveId != activeProfile.id

                _profiles.value = profiles
                _profileId.value = activeProfile.id
                _chain.value = activeProfile.chain

                val validIds = profiles.map { it.id }.toSet()
                val storedSeqConfigs = prefs[SEQUENCE_CONFIGS_KEY]?.let { decodeSequenceConfigs(it) }
                val configs = when {
                    !storedSeqConfigs.isNullOrEmpty() -> storedSeqConfigs.map {
                        it.copy(entries = sanitizeSequence(it.entries, validIds))
                    }
                    else -> {
                        val legacy = sanitizeSequence(
                            decodeProfileSequence(prefs[PROFILE_SEQUENCE_KEY]),
                            validIds,
                        )
                        listOf(
                            TaskSequenceConfig(
                                name = sequenceDefaultName(),
                                entries = legacy,
                            )
                        )
                    }
                }.ifEmpty { listOf(defaultSequenceConfig()) }
                val storedActiveSeq = prefs[ACTIVE_SEQUENCE_CONFIG_KEY].orEmpty()
                val activeSeqId = configs.find { it.id == storedActiveSeq }?.id
                    ?: configs.first().id
                applySequenceConfigs(configs, activeSeqId)
                _profileSequenceEnabled.value =
                    prefs[PROFILE_SEQUENCE_ENABLED_KEY] ?: true

                _isLoaded.value = true

                if (needsSync) {
                    doSync()
                }
                // 迁移/确保序列字段落盘
                persistSequenceConfigs(_sequenceConfigs.value, _activeSequenceConfigId.value)
                if (prefs[PROFILE_SEQUENCE_ENABLED_KEY] == null) {
                    context.store.edit { it[PROFILE_SEQUENCE_ENABLED_KEY] = true }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load profiles")
                if (_sequenceConfigs.value.isEmpty()) {
                    applySequenceConfigs(listOf(defaultSequenceConfig()), "")
                }
                _isLoaded.value = true
            }
        }
    }


    suspend fun addNode(typeInfo: TaskTypeInfo, afterIndex: Int = -1): String {
        val nodeId = mutate {
            val node = TaskChainNode(
                id = UUID.randomUUID().toString(),
                name = defaultTaskName(typeInfo),
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
                // 去掉末尾 " N"（空格+数字）得到基础名
                val baseName = src.name.replace(Regex(" \\d+$"), "")
                // 收集链中所有以 "baseName N" 形式命名已占用的编号
                val usedNumbers = current.mapNotNull { node ->
                    Regex("^${Regex.escape(baseName)} (\\d+)$").matchEntire(node.name)?.groupValues?.get(
                        1
                    )?.toIntOrNull()
                }.toSet()
                // 取最小未占用的正整数（从 2 开始，1 留给源名称本身）
                val nextNum = generateSequence(2) { it + 1 }.first { it !in usedNumbers }
                val copy = src.copy(
                    id = UUID.randomUUID().toString(), name = "$baseName $nextNum"
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
        mutate { current ->
            val idx = current.indexOfFirst { it.id == nodeId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(name = newName)
                Timber.d("Renamed node %s to: %s", nodeId, newName)
            } else {
                Timber.w("renameNode: node %s not found", nodeId)
            }
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

    fun getClientTypeOrNull(): String? {
        return findFirstEnabledConfig<WakeUpConfig>()?.clientType ?: _lastUsedClientType.value
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
        Timber.d("Switched to profile: %s (%s)", target.name, profileId)
    }

    suspend fun createProfile(): String {
        val currentProfiles = _profiles.value
        // 先保存当前活跃 Profile 的链
        val savedProfiles = currentProfiles.map { p ->
            if (p.id == _profileId.value) p.copy(chain = _chain.value) else p
        }
        val newProfile = TaskProfile(
            name = nextProfileName(savedProfiles), chain = buildDefaultChain()
        )
        val updatedProfiles = savedProfiles + newProfile
        // 切换到新 Profile
        _chain.value = newProfile.chain
        _profileId.value = newProfile.id
        _profiles.value = updatedProfiles
        doSync()
        Timber.d("Created profile: %s (%s)", newProfile.name, newProfile.id)
        return newProfile.id
    }

    suspend fun removeProfile(profileId: String) {
        val currentProfiles = _profiles.value
        if (currentProfiles.size <= 1) {
            Timber.w("deleteProfile: cannot delete last profile")
            return
        }
        // 先保存当前链
        val savedProfiles = currentProfiles.map { p ->
            if (p.id == _profileId.value) p.copy(chain = _chain.value) else p
        }
        val remaining = savedProfiles.filter { it.id != profileId }
        if (remaining.size == savedProfiles.size) {
            Timber.w("deleteProfile: profile %s not found", profileId)
            return
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
        // 清理任务链里引用已删 profile 的条目
        sequenceMutex.withLock {
            val validIds = remaining.map { it.id }.toSet()
            val cleaned = _sequenceConfigs.value.map { cfg ->
                cfg.copy(entries = sanitizeSequence(cfg.entries, validIds))
            }
            if (cleaned != _sequenceConfigs.value) {
                applySequenceConfigs(cleaned, _activeSequenceConfigId.value)
                persistSequenceConfigs(cleaned, _activeSequenceConfigId.value)
            }
        }
        doSync()
        _profileDeleted.tryEmit(profileId)
        Timber.d("Deleted profile: %s", profileId)
    }

    suspend fun renameProfile(profileId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_PROFILE_NAME_LENGTH) {
            Timber.w("renameProfile: invalid name length: %d", trimmed.length)
            return
        }
        val currentProfiles = _profiles.value
        val updatedProfiles = currentProfiles.map { p ->
            if (p.id == profileId) p.copy(name = trimmed) else p
        }
        _profiles.value = updatedProfiles
        doSync()
        Timber.d("Renamed profile %s to: %s", profileId, trimmed)
    }

    suspend fun duplicateProfile(profileId: String): String? {
        val currentProfiles = _profiles.value
        // 先保存当前活跃 Profile 的链
        val savedProfiles = currentProfiles.map { p ->
            if (p.id == _profileId.value) p.copy(chain = _chain.value) else p
        }
        val source = savedProfiles.find { it.id == profileId } ?: run {
            Timber.w("duplicateProfile: profile %s not found", profileId)
            return null
        }
        // 复制链时为每个节点生成新 ID
        val duplicatedChain = source.chain.map { it.copy(id = UUID.randomUUID().toString()) }
        val newProfile = TaskProfile(
            name = nextProfileName(savedProfiles), chain = duplicatedChain
        )
        val updatedProfiles = savedProfiles + newProfile
        _profiles.value = updatedProfiles
        doSync()
        Timber.d("Duplicated profile %s as: %s (%s)", profileId, newProfile.name, newProfile.id)
        return newProfile.id
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

        // 顺便把当前未保存的链快照写回 active profile, 避免重排时丢失正在编辑的内容
        val savedProfiles = current.map { p ->
            if (p.id == _profileId.value) p.copy(chain = _chain.value) else p
        }.toMutableList()
        val moved = savedProfiles.removeAt(fromIndex)
        savedProfiles.add(toIndex, moved)

        _profiles.value = savedProfiles
        doSync()
        Timber.d("Reordered profile from %d to %d", fromIndex, toIndex)
    }

    // ========== 内部工具方法 ==========

    private suspend inline fun <T> mutate(
        crossinline block: (MutableList<TaskChainNode>) -> T
    ): T {
        val current = _chain.value.toMutableList()
        val ret = block(current)
        reindex(current)
        val snapshot = current.toList()
        _chain.value = snapshot
        _profiles.value = _profiles.value.map { p ->
            if (p.id == _profileId.value) p.copy(chain = snapshot) else p
        }
        doSync()
        return ret
    }

    private fun reindex(nodes: MutableList<TaskChainNode>) {
        for (i in nodes.indices) {
            nodes[i] = nodes[i].copy(order = i)
        }
    }

    private fun buildDefaultChain(): List<TaskChainNode> {
        return TaskTypeInfo.entries.filter { it.inDefaultChain }.mapIndexed { index, info ->
            TaskChainNode(
                name = defaultTaskName(info),
                enabled = false,
                order = index,
                config = info.defaultConfig()
            )
        }
    }

    private fun defaultTaskName(typeInfo: TaskTypeInfo): String {
        val tag = resolveSelectedLanguage(appSettings.language.value).tag
        val localizedContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            })
        return typeInfo.defaultName(localizedContext)
    }

    suspend fun importProfiles(
        profiles: List<TaskProfile>,
        activeId: String,
        sequence: List<ProfileSequenceEntry> = emptyList(),
        sequenceEnabled: Boolean = true,
        sequenceConfigs: List<TaskSequenceConfig> = emptyList(),
        activeSequenceConfigId: String = "",
    ) {
        val resolvedActiveId =
            profiles.find { it.id == activeId }?.id ?: profiles.firstOrNull()?.id ?: return
        val activeChain = profiles.find { it.id == resolvedActiveId }?.chain ?: buildDefaultChain()
        val validIds = profiles.map { it.id }.toSet()
        val importedConfigs = when {
            sequenceConfigs.isNotEmpty() -> sequenceConfigs.map {
                it.copy(entries = sanitizeSequence(it.entries, validIds))
            }
            else -> listOf(
                TaskSequenceConfig(
                    name = sequenceDefaultName(),
                    entries = sanitizeSequence(sequence, validIds),
                )
            )
        }.ifEmpty { listOf(defaultSequenceConfig()) }
        val resolvedSeqActive = importedConfigs.find { it.id == activeSequenceConfigId }?.id
            ?: importedConfigs.first().id
        sequenceMutex.withLock {
            _profiles.value = profiles
            _profileId.value = resolvedActiveId
            _chain.value = activeChain
            applySequenceConfigs(importedConfigs, resolvedSeqActive)
            _profileSequenceEnabled.value = sequenceEnabled
        }
        doSync()
        persistSequenceConfigs(importedConfigs, resolvedSeqActive)
        context.store.edit { prefs ->
            prefs[PROFILE_SEQUENCE_ENABLED_KEY] = sequenceEnabled
        }
        // 导入是用户可见的终态操作（随后会提示「导入成功」），必须确认落盘再返回
        flush()
        Timber.d(
            "Imported %d profiles, active: %s, sequenceConfigs: %d, enabled: %s",
            profiles.size,
            resolvedActiveId,
            _sequenceConfigs.value.size,
            sequenceEnabled,
        )
    }

    private fun nextProfileName(profiles: List<TaskProfile>): String {
        val maxNum = profiles.mapNotNull { p ->
            if (p.name.startsWith(PROFILE_NAME_PREFIX)) {
                p.name.removePrefix(PROFILE_NAME_PREFIX).toIntOrNull()
            } else {
                null
            }
        }.maxOrNull() ?: 0
        return "$PROFILE_NAME_PREFIX${maxNum + 1}"
    }

    // ========== 任务链（按用户配置顺序逐个执行，不是拼接节点） ==========

    suspend fun switchSequenceConfig(configId: String) = sequenceMutex.withLock {
        val target = _sequenceConfigs.value.find { it.id == configId } ?: run {
            Timber.w("switchSequenceConfig: %s not found", configId)
            return@withLock
        }
        if (_activeSequenceConfigId.value == configId) return@withLock
        _activeSequenceConfigId.value = configId
        _profileSequence.value = target.entries
        persistSequenceConfigs(_sequenceConfigs.value, configId)
        Timber.d("Switched sequence config to %s", configId)
    }

    suspend fun createSequenceConfig(name: String? = null): String? = sequenceMutex.withLock {
        val resolvedName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: nextSequenceName(_sequenceConfigs.value)
        val config = TaskSequenceConfig(name = resolvedName.take(MAX_SEQUENCE_NAME_LENGTH))
        val updated = _sequenceConfigs.value + config
        applySequenceConfigs(updated, config.id)
        persistSequenceConfigs(updated, config.id)
        Timber.d("Created sequence config %s (%s)", config.id, config.name)
        config.id
    }

    suspend fun renameSequenceConfig(configId: String, name: String) = sequenceMutex.withLock {
        val trimmed = name.trim().take(MAX_SEQUENCE_NAME_LENGTH)
        if (trimmed.isEmpty()) return@withLock
        val updated = _sequenceConfigs.value.map {
            if (it.id == configId) it.copy(name = trimmed) else it
        }
        if (updated == _sequenceConfigs.value) return@withLock
        _sequenceConfigs.value = updated
        persistSequenceConfigs(updated, _activeSequenceConfigId.value)
        Timber.d("Renamed sequence config %s to %s", configId, trimmed)
    }

    suspend fun deleteSequenceConfig(configId: String) = sequenceMutex.withLock {
        val current = _sequenceConfigs.value
        if (current.size <= 1) {
            Timber.w("deleteSequenceConfig: cannot delete last config")
            return@withLock
        }
        val remaining = current.filter { it.id != configId }
        if (remaining.size == current.size) return@withLock
        val newActive = if (_activeSequenceConfigId.value == configId) {
            remaining.first().id
        } else {
            _activeSequenceConfigId.value
        }
        applySequenceConfigs(remaining, newActive)
        persistSequenceConfigs(remaining, newActive)
        Timber.d("Deleted sequence config %s, active=%s", configId, newActive)
    }

    suspend fun addProfilesToSequence(profileIds: List<String>): Int = sequenceMutex.withLock {
        if (profileIds.isEmpty()) return@withLock 0
        val validIds = _profiles.value.map { it.id }.toSet()
        val current = _profileSequence.value
        val room = MAX_SEQUENCE_ENTRIES - current.size
        if (room <= 0) {
            Timber.w("addProfilesToSequence: max entries (%d) reached", MAX_SEQUENCE_ENTRIES)
            return@withLock 0
        }
        val toAdd = profileIds
            .filter { it in validIds }
            .take(room)
            .map { ProfileSequenceEntry(profileId = it) }
        if (toAdd.isEmpty()) return@withLock 0
        val updated = current + toAdd
        commitActiveSequenceEntries(updated)
        Timber.d("Added %d profile(s) to sequence (size=%d)", toAdd.size, updated.size)
        toAdd.size
    }

    suspend fun removeSequenceEntry(entryId: String) = sequenceMutex.withLock {
        val updated = _profileSequence.value.filter { it.id != entryId }
        if (updated.size == _profileSequence.value.size) return@withLock
        commitActiveSequenceEntries(updated)
        Timber.d("Removed sequence entry %s", entryId)
    }

    suspend fun reorderSequence(fromIndex: Int, toIndex: Int) = sequenceMutex.withLock {
        val current = _profileSequence.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return@withLock
        if (fromIndex == toIndex) return@withLock
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        commitActiveSequenceEntries(current)
        Timber.d("Reordered sequence from %d to %d", fromIndex, toIndex)
    }

    suspend fun clearProfileSequence() = sequenceMutex.withLock {
        if (_profileSequence.value.isEmpty()) return@withLock
        commitActiveSequenceEntries(emptyList())
        Timber.d("Cleared profile sequence")
    }

    suspend fun setProfileSequenceEnabled(enabled: Boolean) = sequenceMutex.withLock {
        if (_profileSequenceEnabled.value == enabled) return@withLock
        _profileSequenceEnabled.value = enabled
        context.store.edit { prefs ->
            prefs[PROFILE_SEQUENCE_ENABLED_KEY] = enabled
        }
        Timber.d("Profile sequence enabled: %s", enabled)
    }

    /**
     * 解析要按序跑的用户配置 ID 列表。
     * 开启任务链且序列非空 → 返回序列里的 profileId（可重复）。
     * 否则返回当前激活 profile（单配置）。
     * 注意：执行语义是 **逐配置 switch + 跑该配置自己的任务链**，不是 flatMap 拼节点。
     */
    fun resolveSequentialProfileIds(
        sequence: List<ProfileSequenceEntry> = _profileSequence.value,
        sequenceEnabled: Boolean = _profileSequenceEnabled.value,
        profiles: List<TaskProfile> = _profiles.value,
        activeProfileId: String = _profileId.value,
        fallbackToActive: Boolean = true,
    ): List<String> {
        val validIds = profiles.map { it.id }.toSet()
        if (sequenceEnabled && sequence.isNotEmpty()) {
            val ids = sequence.map { it.profileId }.filter { it in validIds }
            if (ids.isNotEmpty()) return ids
            if (!fallbackToActive) return emptyList()
        }
        return if (activeProfileId.isNotEmpty() && activeProfileId in validIds) {
            listOf(activeProfileId)
        } else if (fallbackToActive) {
            profiles.firstOrNull()?.id?.let { listOf(it) }.orEmpty()
        } else {
            emptyList()
        }
    }

    private suspend fun commitActiveSequenceEntries(entries: List<ProfileSequenceEntry>) {
        val activeId = _activeSequenceConfigId.value
        val configs = _sequenceConfigs.value
        val updated = if (configs.any { it.id == activeId }) {
            configs.map { if (it.id == activeId) it.copy(entries = entries) else it }
        } else {
            val cfg = TaskSequenceConfig(
                id = activeId.ifEmpty { java.util.UUID.randomUUID().toString() },
                name = sequenceDefaultName(),
                entries = entries,
            )
            listOf(cfg)
        }
        val resolvedActive = updated.find { it.id == activeId }?.id ?: updated.first().id
        applySequenceConfigs(updated, resolvedActive)
        persistSequenceConfigs(updated, resolvedActive)
    }

    private fun applySequenceConfigs(configs: List<TaskSequenceConfig>, activeId: String) {
        val resolved = configs.ifEmpty { listOf(defaultSequenceConfig()) }
        val resolvedActive = resolved.find { it.id == activeId }?.id ?: resolved.first().id
        _sequenceConfigs.value = resolved
        _activeSequenceConfigId.value = resolvedActive
        _profileSequence.value = resolved.find { it.id == resolvedActive }?.entries.orEmpty()
    }

    private fun sequenceDefaultName(): String {
        return try {
            val tag = resolveSelectedLanguage(appSettings.language.value).tag
            val localized = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(tag))
                }
            )
            localized.getString(R.string.task_sequence_name_prefix) + "1"
        } catch (_: Exception) {
            "任务链-1"
        }
    }

    private fun defaultSequenceConfig(): TaskSequenceConfig {
        return TaskSequenceConfig(name = sequenceDefaultName())
    }

    private fun nextSequenceName(configs: List<TaskSequenceConfig>): String {
        val prefix = try {
            val tag = resolveSelectedLanguage(appSettings.language.value).tag
            val localized = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(tag))
                }
            )
            localized.getString(R.string.task_sequence_name_prefix)
        } catch (_: Exception) {
            "任务链-"
        }
        val maxNum = configs.mapNotNull { cfg ->
            if (cfg.name.startsWith(prefix)) {
                cfg.name.removePrefix(prefix).toIntOrNull()
            } else null
        }.maxOrNull() ?: 0
        return "$prefix${maxNum + 1}"
    }

    private fun decodeProfileSequence(raw: String?): List<ProfileSequenceEntry> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ProfileSequenceEntry>>(raw)
        }.getOrElse {
            Timber.w(it, "Failed to decode profile sequence")
            emptyList()
        }
    }

    private fun decodeSequenceConfigs(raw: String): List<TaskSequenceConfig> {
        return runCatching {
            json.decodeFromString<List<TaskSequenceConfig>>(raw)
        }.getOrElse {
            Timber.w(it, "Failed to decode sequence configs")
            emptyList()
        }
    }

    private fun sanitizeSequence(
        sequence: List<ProfileSequenceEntry>,
        validProfileIds: Set<String>,
    ): List<ProfileSequenceEntry> {
        return sequence
            .filter { it.profileId in validProfileIds }
            .take(MAX_SEQUENCE_ENTRIES)
    }

    private suspend fun persistSequenceConfigs(
        configs: List<TaskSequenceConfig>,
        activeId: String,
    ) {
        context.store.edit { prefs ->
            prefs[SEQUENCE_CONFIGS_KEY] =
                json.encodeToString<List<TaskSequenceConfig>>(configs)
            prefs[ACTIVE_SEQUENCE_CONFIG_KEY] = activeId
            val activeEntries = configs.find { it.id == activeId }?.entries.orEmpty()
            prefs[PROFILE_SEQUENCE_KEY] =
                json.encodeToString<List<ProfileSequenceEntry>>(activeEntries)
        }
    }

}
