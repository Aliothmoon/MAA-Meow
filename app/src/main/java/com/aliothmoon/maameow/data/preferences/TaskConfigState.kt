package com.aliothmoon.maameow.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.data.model.AwardConfig
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.InfrastConfig
import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.data.model.ReclamationConfig
import com.aliothmoon.maameow.data.model.RecruitConfig
import com.aliothmoon.maameow.data.model.RoguelikeConfig
import com.aliothmoon.maameow.data.model.TaskItem
import com.aliothmoon.maameow.data.model.TaskType
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.domain.models.TaskConfigPrefs
import com.aliothmoon.maameow.domain.models.TaskConfigPrefsSchema
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import timber.log.Timber

class TaskConfigState(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = JsonUtils.common

    companion object {
        private val Context.store: DataStore<Preferences> by preferencesDataStore(
            name = "task_config"
        )
    }

    private val prefs: Flow<TaskConfigPrefs> = with(TaskConfigPrefsSchema) { context.store.flow }


    val taskList: StateFlow<List<TaskItem>> =
        prefs.map { it.taskList }.distinctUntilChanged().map { jsonStr ->
            if (jsonStr.isEmpty()) {
                getDefaultTaskList()
            } else {
                runCatching { json.decodeFromString<List<TaskItem>>(jsonStr) }.getOrElse { getDefaultTaskList() }
            }
        }.stateIn(scope, SharingStarted.Eagerly, getDefaultTaskList())

    suspend fun setTaskList(tasks: List<TaskItem>) {
        with(TaskConfigPrefsSchema) {
            context.store.edit { it[taskList] = json.encodeToString(tasks) }
        }
    }

    suspend fun updateTaskEnabled(taskType: TaskType, enabled: Boolean): Result<Unit> {
        return runCatching {
            val currentTasks = taskList.value
            val updatedTasks = currentTasks.map { task ->
                if (task.type == taskType.id) task.copy(isEnabled = enabled) else task
            }
            setTaskList(updatedTasks)
            Timber.d("TaskConfigDataStore: Updated %s enabled: %s", taskType.displayName, enabled)
        }
    }

    suspend fun reorderTasks(fromIndex: Int, toIndex: Int): Result<Unit> {
        return runCatching {
            val currentTasks = taskList.value
            require(fromIndex in currentTasks.indices) { "fromIndex 越界: $fromIndex" }
            require(toIndex in currentTasks.indices) { "toIndex 越界: $toIndex" }

            val mutableTasks = currentTasks.toMutableList()
            val task = mutableTasks.removeAt(fromIndex)
            mutableTasks.add(toIndex, task)
            mutableTasks.forEachIndexed { index, taskItem -> taskItem.order = index }

            setTaskList(mutableTasks)
            Timber.d("TaskConfigDataStore: Moved task from %d to %d", fromIndex, toIndex)
        }
    }


    val wakeUpConfig: StateFlow<WakeUpConfig> = prefs.map { it.wakeUpConfig }.distinctUntilChanged()
        .map { decodeOrDefault(it, WakeUpConfig()) }
        .stateIn(scope, SharingStarted.Eagerly, WakeUpConfig())

    suspend fun setWakeUpConfig(config: WakeUpConfig) {
        with(TaskConfigPrefsSchema) {
            context.store.edit { it[wakeUpConfig] = json.encodeToString(config) }
        }
    }


    val recruitConfig: StateFlow<RecruitConfig> =
        prefs.map { it.recruitConfig }.distinctUntilChanged()
            .map { decodeOrDefault(it, RecruitConfig()) }
            .stateIn(scope, SharingStarted.Eagerly, RecruitConfig())

    suspend fun setRecruitConfig(config: RecruitConfig) {
        with(TaskConfigPrefsSchema) {
            context.store.edit { it[recruitConfig] = json.encodeToString(config) }
        }
    }


    val infrastConfig: StateFlow<InfrastConfig> =
        prefs.map { it.infrastConfig }.distinctUntilChanged()
            .map { decodeOrDefault(it, InfrastConfig()) }
            .stateIn(scope, SharingStarted.Eagerly, InfrastConfig())

    suspend fun setInfrastConfig(config: InfrastConfig) {
        with(TaskConfigPrefsSchema) {
            context.store.edit { it[infrastConfig] = json.encodeToString(config) }
        }
    }


    val fightConfig: StateFlow<FightConfig> = prefs.map { it.fightConfig }.distinctUntilChanged()
        .map { decodeOrDefault(it, FightConfig()) }
        .stateIn(scope, SharingStarted.Eagerly, FightConfig())

    suspend fun setFightConfig(config: FightConfig) {
        with(TaskConfigPrefsSchema) {
            context.store.edit { it[fightConfig] = json.encodeToString(config) }
        }
    }


    val mallConfig: StateFlow<MallConfig> =
        prefs.map { it.mallConfig }
            .distinctUntilChanged()
            .map { decodeOrDefault(it, MallConfig()) }
            .stateIn(scope, SharingStarted.Eagerly, MallConfig())

    suspend fun setMallConfig(config: MallConfig) {
        with(TaskConfigPrefsSchema) {
            context.store.edit { it[mallConfig] = json.encodeToString(config) }
        }
    }


    val awardConfig: StateFlow<AwardConfig> = prefs.map { it.awardConfig }.distinctUntilChanged()
        .map { decodeOrDefault(it, AwardConfig()) }
        .stateIn(scope, SharingStarted.Eagerly, AwardConfig())

    suspend fun setAwardConfig(config: AwardConfig) {
        with(TaskConfigPrefsSchema) {
            context.store.edit { it[awardConfig] = json.encodeToString(config) }
        }
    }


    val roguelikeConfig: StateFlow<RoguelikeConfig> =
        prefs.map { it.roguelikeConfig }.distinctUntilChanged()
            .map { decodeOrDefault(it, RoguelikeConfig()) }
            .stateIn(scope, SharingStarted.Eagerly, RoguelikeConfig())

    suspend fun setRoguelikeConfig(config: RoguelikeConfig) {
        with(TaskConfigPrefsSchema) {
            context.store.edit { it[roguelikeConfig] = json.encodeToString(config) }
        }
    }


    val reclamationConfig: StateFlow<ReclamationConfig> =
        prefs.map { it.reclamationConfig }.distinctUntilChanged()
            .map { decodeOrDefault(it, ReclamationConfig()) }
            .stateIn(scope, SharingStarted.Eagerly, ReclamationConfig())

    suspend fun setReclamationConfig(config: ReclamationConfig) {
        with(TaskConfigPrefsSchema) {
            context.store.edit { it[reclamationConfig] = json.encodeToString(config) }
        }
    }


    private inline fun <reified T> decodeOrDefault(jsonStr: String, defaultValue: T): T {
        if (jsonStr.isEmpty()) return defaultValue
        return runCatching { json.decodeFromString<T>(jsonStr) }.getOrDefault(defaultValue)
    }

    @Suppress("EnumValuesSoftDeprecate")
    private fun getDefaultTaskList(): List<TaskItem> {
        return TaskType.values().mapIndexed { index, taskType ->
            TaskItem.from(taskType, isEnabled = false, order = index)
        }
    }
}