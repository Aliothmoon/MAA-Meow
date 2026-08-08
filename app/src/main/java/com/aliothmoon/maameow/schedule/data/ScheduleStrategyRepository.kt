package com.aliothmoon.maameow.schedule.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.TaskSequenceConfig
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.model.ScheduleTargetKind
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

class ScheduleStrategyRepository(private val context: Context) {

    companion object {
        private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "schedule_strategies")
        private val STRATEGIES_KEY = stringPreferencesKey("strategies")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = JsonUtils.common

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    /** 从 DataStore 自动同步的策略列表 */
    val strategies: StateFlow<List<ScheduleStrategy>> = context.store.data
        .map { prefs ->
            val list = decodeStrategies(prefs[STRATEGIES_KEY])
            _isLoaded.value = true
            list
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // ---- 策略 CRUD ----

    suspend fun add(strategy: ScheduleStrategy) {
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            current.add(strategy)
            prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
            Timber.d("添加调度策略: %s (%s)", strategy.name, strategy.id)
        }
    }

    suspend fun update(strategy: ScheduleStrategy) {
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            val idx = current.indexOfFirst { it.id == strategy.id }
            if (idx >= 0) {
                current[idx] = strategy
                prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
                Timber.d("更新调度策略: %s (%s)", strategy.name, strategy.id)
            }
        }
    }

    suspend fun remove(strategyId: String) {
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            if (current.removeAll { it.id == strategyId }) {
                prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
                Timber.d("删除调度策略: %s", strategyId)
            }
        }
    }

    suspend fun setEnabled(strategyId: String, enabled: Boolean) {
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            val idx = current.indexOfFirst { it.id == strategyId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(enabled = enabled)
                prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
                Timber.d("策略 %s 启用状态 -> %s", strategyId, enabled)
            }
        }
    }

    suspend fun getById(strategyId: String): ScheduleStrategy? {
        return strategies.value.find { it.id == strategyId }
    }

    suspend fun recordExecutionResult(
        strategyId: String,
        result: com.aliothmoon.maameow.schedule.model.ExecutionResult,
        message: String? = null,
        executedAt: Long = System.currentTimeMillis(),
    ) {
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            val idx = current.indexOfFirst { it.id == strategyId }
            if (idx < 0) {
                return@edit
            }

            current[idx] = current[idx].copy(
                lastExecutedAt = executedAt,
                lastResult = result,
                lastResultMessage = message,
            )
            prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
            Timber.d("记录调度结果: %s -> %s (%s)", strategyId, result, message)
        }
    }


    suspend fun importStrategies(strategies: List<ScheduleStrategy>) {
        context.store.edit { prefs ->
            prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(strategies)
            Timber.d("导入 %d 条调度策略", strategies.size)
        }
    }


    /**
     * 任务链配置被删除后：禁用仍绑定该配置的定时策略，并清空 sequenceConfigId。
     * @return 被改动的策略 id（用于取消闹钟）
     */
    suspend fun detachSequenceConfig(sequenceConfigId: String): List<String> {
        if (sequenceConfigId.isEmpty()) return emptyList()
        val changedIds = mutableListOf<String>()
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            var changed = false
            for (i in current.indices) {
                val strategy = current[i]
                if (strategy.targetKind != ScheduleTargetKind.SEQUENCE) continue
                if (strategy.sequenceConfigId != sequenceConfigId) continue
                current[i] = strategy.copy(
                    enabled = false,
                    sequenceConfigId = "",
                )
                changedIds += strategy.id
                changed = true
            }
            if (changed) {
                prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
                Timber.d(
                    "任务链 %s 已删除，已禁用 %d 条定时策略",
                    sequenceConfigId,
                    changedIds.size,
                )
            }
        }
        return changedIds
    }

    /**
     * 用户配置被删除后：禁用仍绑定该 profile 的 PROFILE 定时策略，并清空 profileId。
     * @return 被改动的策略 id（用于取消闹钟）
     */
    suspend fun detachProfileConfig(profileId: String): List<String> {
        if (profileId.isEmpty()) return emptyList()
        val changedIds = mutableListOf<String>()
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            var changed = false
            for (i in current.indices) {
                val strategy = current[i]
                if (strategy.targetKind != ScheduleTargetKind.PROFILE) continue
                if (strategy.profileId != profileId) continue
                current[i] = strategy.copy(
                    enabled = false,
                    profileId = "",
                )
                changedIds += strategy.id
                changed = true
            }
            if (changed) {
                prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
                Timber.d(
                    "用户配置 %s 已删除，已禁用 %d 条定时策略",
                    profileId,
                    changedIds.size,
                )
            }
        }
        return changedIds
    }

    /**
     * 导入/删配置后：禁用目标缺失或无可执行任务的定时策略。
     * - PROFILE：profile 不存在，或链上无启用节点
     * - SEQUENCE：任务链不存在、条目为空，或拼接后无启用节点
     * @return 被改动的策略 id（用于取消闹钟）
     */
    suspend fun sanitizeInvalidTargets(
        profiles: List<TaskProfile>,
        sequenceConfigs: List<TaskSequenceConfig>,
    ): List<String> {
        val profileMap = profiles.associateBy { it.id }
        val sequenceMap = sequenceConfigs.associateBy { it.id }
        val changedIds = mutableListOf<String>()
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            var changed = false
            for (i in current.indices) {
                val strategy = current[i]
                if (!strategy.enabled) continue
                val invalid = when (strategy.targetKind) {
                    ScheduleTargetKind.PROFILE -> {
                        val profile = profileMap[strategy.profileId]
                        profile == null || profile.chain.none { it.enabled }
                    }
                    ScheduleTargetKind.SEQUENCE -> {
                        val seq = sequenceMap[strategy.sequenceConfigId]
                        if (seq == null || seq.entries.isEmpty()) {
                            true
                        } else {
                            seq.entries.none { entry ->
                                profileMap[entry.profileId]?.chain?.any { it.enabled } == true
                            }
                        }
                    }
                }
                if (!invalid) continue
                current[i] = when (strategy.targetKind) {
                    ScheduleTargetKind.PROFILE -> strategy.copy(
                        enabled = false,
                        profileId = if (strategy.profileId !in profileMap) "" else strategy.profileId,
                    )
                    ScheduleTargetKind.SEQUENCE -> strategy.copy(
                        enabled = false,
                        sequenceConfigId = if (strategy.sequenceConfigId !in sequenceMap) {
                            ""
                        } else {
                            strategy.sequenceConfigId
                        },
                    )
                }
                changedIds += strategy.id
                changed = true
            }
            if (changed) {
                prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
                Timber.d("sanitizeInvalidTargets: disabled %d schedule(s)", changedIds.size)
            }
        }
        return changedIds
    }

    private fun decodeStrategies(raw: String?): List<ScheduleStrategy> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ScheduleStrategy>>(raw)
        }.getOrElse {
            Timber.w(it, "解析调度策略失败，返回空列表")
            emptyList()
        }
    }
}
