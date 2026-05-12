package com.aliothmoon.maameow.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.model.InfrastConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.remote.PermissionGrantRequest
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import timber.log.Timber
import java.util.UUID

class TaskChainState(
    private val context: Context,
    private val appSettings: AppSettingsManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = JsonUtils.common

    companion object {
        private val Context.store: DataStore<Preferences> by preferencesDataStore(
            name = "task_chain"
        )
        private val CHAIN_KEY = stringPreferencesKey("chain")
        private val PROFILES_KEY = stringPreferencesKey("profiles")
        private val ACTIVE_PROFILE_KEY = stringPreferencesKey("active_profile_id")

        private const val PROFILE_NAME_PREFIX = "配置-"
        private const val MAX_PROFILES = 10
        private const val MAX_PROFILE_NAME_LENGTH = 20
    }

    private val _chain = MutableStateFlow(buildDefaultChain())
    val chain: StateFlow<List<TaskChainNode>> = _chain.asStateFlow()

    private val _profiles = MutableStateFlow<List<TaskProfile>>(emptyList())
    val profiles: StateFlow<List<TaskProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow("")
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _lastUsedClientType = MutableStateFlow<String?>(null)

    init {
        scope.launch {
            val prefs = context.store.data.first()
            val profilesJson = prefs[PROFILES_KEY]

            if (profilesJson != null) {
                // 已有 Profile 数据
                val loadedProfiles = decodeProfiles(profilesJson)
                val activeId = prefs[ACTIVE_PROFILE_KEY] ?: loadedProfiles.firstOrNull()?.id ?: ""
                _profiles.value = loadedProfiles
                _activeProfileId.value = activeId
                val activeProfile = loadedProfiles.find { it.id == activeId }
                    ?: loadedProfiles.firstOrNull()
                if (activeProfile != null) {
                    _activeProfileId.value = activeProfile.id
                    _chain.value = activeProfile.chain
                }
            } else {
                // 迁移: 旧版数据无 profiles key, 将现有 chain 包装为单个 Profile
                val legacyChain = decodeChain(prefs[CHAIN_KEY])
                val profile = TaskProfile(
                    name = "${PROFILE_NAME_PREFIX}1",
                    chain = legacyChain
                )
                _profiles.value = listOf(profile)
                _activeProfileId.value = profile.id
                _chain.value = legacyChain
                // 持久化迁移结果
                persistProfiles(listOf(profile), profile.id)
            }
            _isLoaded.value = true
        }
    }


    suspend fun addNode(typeInfo: TaskTypeInfo, afterIndex: Int = -1): String {
        var newNodeId = ""
        updateChain { current ->
            val node = TaskChainNode(
                id = UUID.randomUUID().toString(),
                name = defaultTaskName(typeInfo),
                enabled = true,
                config = typeInfo.defaultConfig()
            )
            newNodeId = node.id
            if (afterIndex < 0 || afterIndex >= current.size) {
                current.add(node)
            } else {
                current.add(afterIndex + 1, node)
            }
            Timber.d("Added node: %s (%s)", node.name, typeInfo.name)
        }
        return newNodeId
    }

    suspend fun removeNode(nodeId: String) {
        updateChain { current ->
            current.removeAll { it.id == nodeId }
            Timber.d("Removed node: %s", nodeId)
        }
    }

    suspend fun renameNode(nodeId: String, newName: String) {
        updateChain { current ->
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
        updateChain { current ->
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
        updateChain { current ->
            val idx = current.indexOfFirst { it.id == nodeId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(config = config)
            } else {
                Timber.w("updateNodeConfig: node %s not found", nodeId)
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

    suspend fun reorderNodes(fromIndex: Int, toIndex: Int) {
        updateChain { current ->
            require(fromIndex in current.indices) { "fromIndex out of bounds: $fromIndex" }
            require(toIndex in current.indices) { "toIndex out of bounds: $toIndex" }
            val node = current.removeAt(fromIndex)
            current.add(toIndex, node)
            Timber.d("Moved node from %d to %d", fromIndex, toIndex)
        }
    }

    inline fun <reified T : TaskParamProvider> firstConfigFlow(): Flow<T?> {
        return chain.map { nodes ->
            nodes.firstNotNullOfOrNull { it.config as? T }
        }.distinctUntilChanged()
    }

    inline fun <reified T : TaskParamProvider> findFirstConfig(): T? {
        return chain.value.firstNotNullOfOrNull { it.config as? T }
    }

    fun getClientType(): String {
        return getClientTypeOrNull() ?: "Official"
    }

    fun getClientTypeOrNull(): String? {
        return findFirstEnabledConfig<WakeUpConfig>()?.clientType
            ?: _lastUsedClientType.value
    }

    fun getLastUsedClientType(): String? = _lastUsedClientType.value

    fun saveLastUsedClientType(clientType: String) {
        _lastUsedClientType.value = clientType
    }

    inline fun <reified T : TaskParamProvider> findFirstEnabledConfig(): T? {
        return chain.value
            .filter { it.enabled }
            .firstNotNullOfOrNull { it.config as? T }
    }

    inline fun <reified T : TaskParamProvider> firstEnabledConfigFlow(): Flow<T?> {
        return chain.map { nodes ->
            nodes.filter { it.enabled }.firstNotNullOfOrNull { it.config as? T }
        }.distinctUntilChanged()
    }

    fun grantGameBatteryExemption(clientType: String) {
        val pkg = Packages[clientType] ?: return
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.grantPermissions(
                PermissionGrantRequest(
                    packageName = pkg,
                    permissions =
                        PermissionGrantRequest.PERM_BATTERY
                                or PermissionGrantRequest.PERM_BACKGROUND
                )
            )
            Timber.d("Battery exemption granted for game: %s", pkg)
        }.onFailure { e ->
            Timber.w(e, "Failed to grant battery exemption for game")
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
            if (p.id == _activeProfileId.value) p.copy(chain = _chain.value) else p
        }
        // 加载新 Profile 的链
        _chain.value = target.chain
        _activeProfileId.value = profileId
        _profiles.value = updatedProfiles
        // 持久化
        persistProfiles(updatedProfiles, profileId)
        Timber.d("Switched to profile: %s (%s)", target.name, profileId)
    }

    suspend fun createProfile(): String? {
        val currentProfiles = _profiles.value
        if (currentProfiles.size >= MAX_PROFILES) {
            Timber.w("createProfile: max profiles (%d) reached", MAX_PROFILES)
            return null
        }
        // 先保存当前活跃 Profile 的链
        val savedProfiles = currentProfiles.map { p ->
            if (p.id == _activeProfileId.value) p.copy(chain = _chain.value) else p
        }
        val newProfile = TaskProfile(
            name = nextProfileName(savedProfiles),
            chain = buildDefaultChain()
        )
        val updatedProfiles = savedProfiles + newProfile
        // 切换到新 Profile
        _chain.value = newProfile.chain
        _activeProfileId.value = newProfile.id
        _profiles.value = updatedProfiles
        persistProfiles(updatedProfiles, newProfile.id)
        Timber.d("Created profile: %s (%s)", newProfile.name, newProfile.id)
        return newProfile.id
    }

    suspend fun deleteProfile(profileId: String) {
        val currentProfiles = _profiles.value
        if (currentProfiles.size <= 1) {
            Timber.w("deleteProfile: cannot delete last profile")
            return
        }
        // 先保存当前链
        val savedProfiles = currentProfiles.map { p ->
            if (p.id == _activeProfileId.value) p.copy(chain = _chain.value) else p
        }
        val remaining = savedProfiles.filter { it.id != profileId }
        if (remaining.size == savedProfiles.size) {
            Timber.w("deleteProfile: profile %s not found", profileId)
            return
        }
        // 若删除的是活跃 Profile,切换到列表第一个
        val newActiveId = if (_activeProfileId.value == profileId) {
            val first = remaining.first()
            _chain.value = first.chain
            first.id
        } else {
            _activeProfileId.value
        }
        _activeProfileId.value = newActiveId
        _profiles.value = remaining
        persistProfiles(remaining, newActiveId)
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
        persistProfiles(updatedProfiles, _activeProfileId.value)
        Timber.d("Renamed profile %s to: %s", profileId, trimmed)
    }

    suspend fun duplicateProfile(profileId: String): String? {
        val currentProfiles = _profiles.value
        if (currentProfiles.size >= MAX_PROFILES) {
            Timber.w("duplicateProfile: max profiles (%d) reached", MAX_PROFILES)
            return null
        }
        // 先保存当前活跃 Profile 的链
        val savedProfiles = currentProfiles.map { p ->
            if (p.id == _activeProfileId.value) p.copy(chain = _chain.value) else p
        }
        val source = savedProfiles.find { it.id == profileId } ?: run {
            Timber.w("duplicateProfile: profile %s not found", profileId)
            return null
        }
        // 复制链时为每个节点生成新 ID
        val duplicatedChain = source.chain.map { it.copy(id = UUID.randomUUID().toString()) }
        val newProfile = TaskProfile(
            name = nextProfileName(savedProfiles),
            chain = duplicatedChain
        )
        val updatedProfiles = savedProfiles + newProfile
        _profiles.value = updatedProfiles
        persistProfiles(updatedProfiles, _activeProfileId.value)
        Timber.d("Duplicated profile %s as: %s (%s)", profileId, newProfile.name, newProfile.id)
        return newProfile.id
    }

    suspend fun reorderProfiles(fromIndex: Int, toIndex: Int) {
        val current = _profiles.value
        if (fromIndex !in current.indices || toIndex !in current.indices) {
            Timber.w(
                "reorderProfiles: invalid index from=%d to=%d size=%d",
                fromIndex, toIndex, current.size
            )
            return
        }
        if (fromIndex == toIndex) return

        // 顺便把当前未保存的链快照写回 active profile, 避免重排时丢失正在编辑的内容
        val savedProfiles = current.map { p ->
            if (p.id == _activeProfileId.value) p.copy(chain = _chain.value) else p
        }.toMutableList()
        val moved = savedProfiles.removeAt(fromIndex)
        savedProfiles.add(toIndex, moved)

        _profiles.value = savedProfiles
        persistProfiles(savedProfiles, _activeProfileId.value)
        Timber.d("Reordered profile from %d to %d", fromIndex, toIndex)
    }

    // ========== 内部工具方法 ==========

    private suspend inline fun updateChain(
        crossinline block: (MutableList<TaskChainNode>) -> Unit
    ) {
        val current = _chain.value.toMutableList()
        block(current)
        reindex(current)
        val snapshot = current.toList()
        _chain.value = snapshot              // 同步更新，立即可见
        // 同步更新 profiles 中活跃 Profile 的 chain
        val updatedProfiles = _profiles.value.map { p ->
            if (p.id == _activeProfileId.value) p.copy(chain = snapshot) else p
        }
        _profiles.value = updatedProfiles
        context.store.edit { prefs ->        // 异步持久化
            prefs[CHAIN_KEY] = json.encodeToString<List<TaskChainNode>>(snapshot)
            prefs[PROFILES_KEY] = json.encodeToString<List<TaskProfile>>(updatedProfiles)
        }
    }

    private fun decodeChain(raw: String?): List<TaskChainNode> {
        if (raw.isNullOrEmpty()) return buildDefaultChain()
        return runCatching {
            json.decodeFromString<List<TaskChainNode>>(raw)
        }.getOrElse {
            Timber.w(it, "Failed to decode task chain, using defaults")
            buildDefaultChain()
        }
    }

    private fun decodeProfiles(raw: String): List<TaskProfile> {
        return runCatching {
            json.decodeFromString<List<TaskProfile>>(raw)
        }.getOrElse {
            Timber.w(it, "Failed to decode profiles")
            emptyList()
        }
    }

    private suspend fun persistProfiles(profiles: List<TaskProfile>, activeId: String) {
        context.store.edit { prefs ->
            prefs[PROFILES_KEY] = json.encodeToString<List<TaskProfile>>(profiles)
            prefs[ACTIVE_PROFILE_KEY] = activeId
            // 同步更新 CHAIN_KEY 以保持兼容
            val activeChain = profiles.find { it.id == activeId }?.chain ?: _chain.value
            prefs[CHAIN_KEY] = json.encodeToString<List<TaskChainNode>>(activeChain)
        }
    }

    private fun reindex(nodes: MutableList<TaskChainNode>) {
        for (i in nodes.indices) {
            nodes[i] = nodes[i].copy(order = i)
        }
    }

    private fun buildDefaultChain(): List<TaskChainNode> {
        return TaskTypeInfo.entries.mapIndexed { index, info ->
            TaskChainNode(
                name = defaultTaskName(info),
                enabled = false,
                order = index,
                config = info.defaultConfig()
            )
        }
    }

    private fun defaultTaskName(typeInfo: TaskTypeInfo): String {
        return typeInfo.defaultName(resolveSelectedLanguage(appSettings.language.value))
    }

    suspend fun importProfiles(profiles: List<TaskProfile>, activeId: String) {
        val resolvedActiveId = profiles.find { it.id == activeId }?.id
            ?: profiles.firstOrNull()?.id ?: return
        val activeChain = profiles.find { it.id == resolvedActiveId }?.chain ?: buildDefaultChain()
        _profiles.value = profiles
        _activeProfileId.value = resolvedActiveId
        _chain.value = activeChain
        persistProfiles(profiles, resolvedActiveId)
        Timber.d("Imported %d profiles, active: %s", profiles.size, resolvedActiveId)
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
}
