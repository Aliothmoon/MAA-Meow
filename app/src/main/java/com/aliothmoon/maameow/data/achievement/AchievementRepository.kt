package com.aliothmoon.maameow.data.achievement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AchievementRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = JsonUtils.common
    private val recordMutex = Mutex()

    private val definitions: Map<String, AchievementDefinition> by lazy {
        loadDefinitions().associateBy { it.id }
    }
    private val _achievements = MutableStateFlow(buildStates(emptyMap()))
    val achievements: StateFlow<List<AchievementState>> = _achievements.asStateFlow()

    private val _unlockEvents = MutableSharedFlow<AchievementState>(extraBufferCapacity = 8)
    val unlockEvents: SharedFlow<AchievementState> = _unlockEvents.asSharedFlow()

    companion object {
        private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "achievements")
        private val RECORDS_KEY = stringPreferencesKey("records")
    }

    init {
        scope.launch {
            recordMutex.withLock {
                val records = loadRecords()
                _achievements.value = buildStates(records)
            }
        }
    }

    suspend fun recordAppLaunch() {
        recordEvent(AchievementEvents.AppLaunch)
    }

    suspend fun unlock(id: String) {
        mutateRecord(id) { record ->
            if (record.unlocked) {
                record to false
            } else {
                record.copy(unlocked = true, unlockedAtMillis = System.currentTimeMillis()) to true
            }
        }
    }

    suspend fun unlockAll() {
        recordMutex.withLock {
            val nowMillis = System.currentTimeMillis()
            val records = loadRecords().toMutableMap()
            definitions.keys.forEach { id ->
                val record = records[id] ?: AchievementRecord(id = id)
                records[id] = record.copy(unlocked = true, unlockedAtMillis = record.unlockedAtMillis ?: nowMillis)
            }
            persistRecords(records)
            _achievements.value = buildStates(records)
        }
    }

    suspend fun clearAllRecords() {
        recordMutex.withLock {
            persistRecords(emptyMap())
            _achievements.value = buildStates(emptyMap())
        }
    }

    suspend fun recordEvent(
        event: String,
        payload: Map<String, String> = emptyMap(),
        amount: Int = 1,
    ) {
        val now = LocalDate.now(ZoneId.systemDefault())
        val eventPayload = payload + mapOf(
            "date" to now.toString(),
            "monthDay" to "%02d-%02d".format(now.monthValue, now.dayOfMonth),
            "hour" to LocalTime.now().hour.toString(),
            "random" to Math.random().toString(),
            "version" to BuildConfig.VERSION_NAME,
        )
        val unlockEvents = recordMutex.withLock {
            val records = loadRecords().toMutableMap()
            val newUnlocks = mutableListOf<AchievementState>()

            definitions.values.forEach { definition ->
                val triggers = definition.allTriggers().filter { it.event == event }
                triggers.forEach { trigger ->
                    if (!trigger.matches(eventPayload)) return@forEach

                    val current = records[definition.id] ?: AchievementRecord(id = definition.id)
                    val (updated, unlockedNow) = applyTrigger(definition, current, trigger, amount, eventPayload)
                    records[definition.id] = updated
                    if (unlockedNow) {
                        AchievementState(
                            definition = definition,
                            unlocked = true,
                            unlockedAtMillis = updated.unlockedAtMillis,
                            progress = updated.progress,
                            isNewUnlock = true,
                        ).let(newUnlocks::add)
                    }
                }
            }

            if (newUnlocks.isEmpty()) {
                val anyMatched = definitions.values.any { definition ->
                    definition.allTriggers().any { it.event == event && it.matches(eventPayload) }
                }
                if (!anyMatched) return@withLock emptyList()
            }

            persistRecords(records)
            _achievements.value = buildStates(records)
            newUnlocks
        }
        unlockEvents.forEach { _unlockEvents.emit(it) }
    }

    suspend fun addProgress(id: String, amount: Int = 1) {
        mutateRecord(id) { record ->
            val definition = definitions[id] ?: return@mutateRecord record to false
            if (record.unlocked) {
                record to false
            } else {
                val progress = (record.progress + amount).coerceAtLeast(0)
                val shouldUnlock = definition.target > 0 && progress >= definition.target
                record.copy(
                    progress = progress,
                    unlocked = shouldUnlock,
                    unlockedAtMillis = if (shouldUnlock) System.currentTimeMillis() else record.unlockedAtMillis,
                ) to shouldUnlock
            }
        }
    }

    suspend fun addProgressToGroup(group: String, amount: Int = 1) {
        definitions.values
            .filter { it.group == group }
            .forEach { addProgress(it.id, amount) }
    }

    suspend fun setProgress(id: String, progress: Int) {
        mutateRecord(id) { record ->
            val definition = definitions[id] ?: return@mutateRecord record to false
            if (record.unlocked) {
                record to false
            } else {
                val normalizedProgress = progress.coerceAtLeast(0)
                val shouldUnlock = definition.target > 0 && normalizedProgress >= definition.target
                record.copy(
                    progress = normalizedProgress,
                    unlocked = shouldUnlock,
                    unlockedAtMillis = if (shouldUnlock) System.currentTimeMillis() else record.unlockedAtMillis,
                ) to shouldUnlock
            }
        }
    }

    suspend fun recordMissionStart() {
        recordEvent(AchievementEvents.MissionStarted)
    }

    suspend fun recordClueOpening() {
        recordEvent(AchievementEvents.ProcessTaskCompleted, mapOf("taskchain" to "Infrast", "task" to "UnlockClues"))
    }

    private suspend fun mutateRecord(
        id: String,
        transform: (AchievementRecord) -> Pair<AchievementRecord, Boolean>,
    ) {
        if (!definitions.containsKey(id)) return

        val unlockEvent = recordMutex.withLock {
            val records = loadRecords().toMutableMap()
            val current = records[id] ?: AchievementRecord(id = id)
            val (updated, unlockedNow) = transform(current)
            records[id] = updated
            persistRecords(records)

            val states = buildStates(records)
            _achievements.value = states
            if (unlockedNow) {
                states.firstOrNull { it.definition.id == id }?.copy(isNewUnlock = true)
            } else {
                null
            }
        }
        if (unlockEvent != null) {
            _unlockEvents.emit(unlockEvent)
        }
    }

    private fun AchievementDefinition.allTriggers(): List<AchievementTrigger> = buildList {
        trigger?.let(::add)
        addAll(triggers)
    }

    private fun AchievementTrigger.matches(payload: Map<String, String>): Boolean {
        return where.all { (key, value) -> payload[key] == value } && conditions.all { it.matches(payload) }
    }

    private fun AchievementCondition.matches(payload: Map<String, String>): Boolean {
        val actual = payload[field] ?: return false
        return when (op) {
            AchievementConditionOp.EQ -> actual == value
            AchievementConditionOp.NE -> actual != value
            AchievementConditionOp.GT -> actual.toDoubleOrNull()?.let { it > (value.toDoubleOrNull() ?: return false) } == true
            AchievementConditionOp.GTE -> actual.toDoubleOrNull()?.let { it >= (value.toDoubleOrNull() ?: return false) } == true
            AchievementConditionOp.LT -> actual.toDoubleOrNull()?.let { it < (value.toDoubleOrNull() ?: return false) } == true
            AchievementConditionOp.LTE -> actual.toDoubleOrNull()?.let { it <= (value.toDoubleOrNull() ?: return false) } == true
            AchievementConditionOp.BETWEEN -> {
                val actualNumber = actual.toDoubleOrNull() ?: return false
                val bounds = value.split("..", limit = 2).mapNotNull { it.toDoubleOrNull() }
                bounds.size == 2 && actualNumber >= bounds[0] && actualNumber <= bounds[1]
            }
            AchievementConditionOp.CONTAINS -> actual.contains(value, ignoreCase = true)
            AchievementConditionOp.MONTH_DAY -> actual == value
            AchievementConditionOp.MONTH_DAY_BETWEEN -> {
                val bounds = value.split("..", limit = 2)
                bounds.size == 2 && actual.isMonthDayBetween(bounds[0], bounds[1])
            }
        }
    }

    private fun String.isMonthDayBetween(start: String, end: String): Boolean {
        val actualOrdinal = toMonthDayOrdinal() ?: return false
        val startOrdinal = start.toMonthDayOrdinal() ?: return false
        val endOrdinal = end.toMonthDayOrdinal() ?: return false
        return if (startOrdinal <= endOrdinal) {
            actualOrdinal in startOrdinal..endOrdinal
        } else {
            actualOrdinal >= startOrdinal || actualOrdinal <= endOrdinal
        }
    }

    private fun String.toMonthDayOrdinal(): Int? {
        val parts = split("-", limit = 2)
        if (parts.size != 2) return null
        val month = parts[0].toIntOrNull() ?: return null
        val day = parts[1].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(2000, month, day).dayOfYear }.getOrNull()
    }

    private fun applyTrigger(
        definition: AchievementDefinition,
        record: AchievementRecord,
        trigger: AchievementTrigger,
        eventAmount: Int,
        payload: Map<String, String>,
    ): Pair<AchievementRecord, Boolean> {
        if (trigger.mode == AchievementTriggerMode.RESET) return record.copy(progress = 0) to false
        if (record.unlocked) return record to false

        val nowMillis = System.currentTimeMillis()
        val updated = when (trigger.mode) {
            AchievementTriggerMode.UNLOCK -> record.copy(unlocked = true, unlockedAtMillis = nowMillis)
            AchievementTriggerMode.INCREMENT -> {
                val nextProgress = (record.progress + trigger.amount * eventAmount).coerceAtLeast(0)
                record.withProgress(definition, nextProgress, nowMillis)
            }
            AchievementTriggerMode.SET_MAX -> {
                val value = payload["value"]?.toIntOrNull() ?: eventAmount
                record.withProgress(definition, maxOf(record.progress, value), nowMillis)
            }
            AchievementTriggerMode.SAME_DAY_COUNT -> {
                val today = payload["date"].orEmpty()
                val key = trigger.dateKey.ifBlank { "${trigger.event}_date" }
                val nextProgress = if (record.customData[key] == today) record.progress + trigger.amount * eventAmount else trigger.amount * eventAmount
                record.withProgress(definition, nextProgress, nowMillis)
                    .copy(customData = record.customData + (key to today))
            }
            AchievementTriggerMode.DAILY_STREAK -> {
                val today = payload["date"].orEmpty()
                val key = trigger.dateKey.ifBlank { "${trigger.event}_date" }
                val lastDate = record.customData[key]
                val nextProgress = when {
                    lastDate == today -> record.progress
                    lastDate == null -> trigger.amount * eventAmount
                    runCatching { LocalDate.parse(lastDate).plusDays(1).toString() }.getOrNull() == today -> record.progress + trigger.amount * eventAmount
                    else -> trigger.amount * eventAmount
                }
                record.withProgress(definition, nextProgress, nowMillis)
                    .copy(customData = record.customData + (key to today))
            }
            AchievementTriggerMode.RESET -> record
        }
        return updated to (!record.unlocked && updated.unlocked)
    }

    private fun AchievementRecord.withProgress(
        definition: AchievementDefinition,
        progress: Int,
        nowMillis: Long,
    ): AchievementRecord {
        val shouldUnlock = definition.target > 0 && progress >= definition.target
        return copy(
            progress = progress,
            unlocked = unlocked || shouldUnlock,
            unlockedAtMillis = if (!unlocked && shouldUnlock) nowMillis else unlockedAtMillis,
        )
    }

    private suspend fun loadRecords(): Map<String, AchievementRecord> {
        val raw = context.store.data.first()[RECORDS_KEY].orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString(ListSerializer(AchievementRecord.serializer()), raw).associateBy { it.id }
        }.getOrDefault(emptyMap())
    }

    private suspend fun persistRecords(records: Map<String, AchievementRecord>) {
        val encoded = json.encodeToString(ListSerializer(AchievementRecord.serializer()), records.values.toList())
        context.store.edit { prefs -> prefs[RECORDS_KEY] = encoded }
    }

    private fun loadDefinitions(): List<AchievementDefinition> = runCatching {
        context.assets.open("achievements.json").bufferedReader().use { reader ->
            json.decodeFromString(ListSerializer(AchievementDefinition.serializer()), reader.readText())
        }
    }.getOrElse {
        Timber.e(it, "Failed to load achievements.json")
        emptyList()
    }

    private fun buildStates(records: Map<String, AchievementRecord>): List<AchievementState> = definitions.values
        .filter { definition -> definition.allTriggers().isNotEmpty() || records.containsKey(definition.id) }
        .map { definition ->
            val record = records[definition.id]
            AchievementState(
                definition = definition,
                unlocked = record?.unlocked == true,
                unlockedAtMillis = record?.unlockedAtMillis,
                progress = record?.progress ?: 0,
            )
        }
        .sortedWith(
            compareByDescending<AchievementState> { it.unlocked }
                .thenBy { it.definition.category.ordinal }
                .thenBy { it.definition.releasePhase }
                .thenBy { it.definition.group }
                .thenBy { it.definition.groupIndex }
                .thenBy { it.definition.id }
        )
}
