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
import kotlinx.coroutines.channels.BufferOverflow
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AchievementRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = JsonUtils.common
    private val recordMutex = Mutex()

    private val definitions: Map<String, AchievementDefinition> by lazy {
        AchievementDefinitions.all.associateBy { it.id }
    }
    private val _records = MutableStateFlow<Map<String, AchievementRecord>>(emptyMap())
    val records: StateFlow<Map<String, AchievementRecord>> = _records.asStateFlow()

    private val _unlockEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val unlockEvents: SharedFlow<String> = _unlockEvents.asSharedFlow()

    companion object {
        private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "achievements")
        private val RECORDS_KEY = stringPreferencesKey("records")
    }

    init {
        scope.launch {
            recordMutex.withLock {
                _records.value = context.store.data.first()[RECORDS_KEY]?.let { it ->
                    runCatching {
                        json.decodeFromString<List<AchievementRecord>>(it)
                            .associateBy { it.id }
                    }.getOrNull()
                } ?: emptyMap()
            }
        }
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
            val records = _records.value.toMutableMap()
            definitions.keys.forEach { id ->
                val record = records[id] ?: AchievementRecord(id = id)
                records[id] = record.copy(
                    unlocked = true,
                    unlockedAtMillis = record.unlockedAtMillis ?: nowMillis
                )
            }
            save(records)
            _records.value = records
        }
    }

    suspend fun clearAllRecords() {
        recordMutex.withLock {
            val records = _records.value.toMutableMap()
            records.clear()
            save(records)
            _records.value = records
        }
    }

    suspend fun reportEvent(
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
        val unlockedIds = recordMutex.withLock {
            val records = _records.value.toMutableMap()
            val newlyUnlocked = mutableListOf<String>()
            var changed = false

            definitions.values.forEach { definition ->
                definition.allTriggers().forEach { trigger ->
                    if (trigger.event != event || !trigger.matches(eventPayload)) return@forEach

                    val current = records[definition.id] ?: AchievementRecord(id = definition.id)
                    val (updated, unlockedNow) = applyTrigger(
                        definition,
                        current,
                        trigger,
                        amount,
                        eventPayload
                    )
                    if (updated != current) {
                        records[definition.id] = updated
                        changed = true
                    }
                    if (unlockedNow) newlyUnlocked += definition.id
                }
            }

            if (!changed) return@withLock emptyList()

            save(records)
            _records.value = records
            newlyUnlocked
        }
        unlockedIds.forEach { _unlockEvents.emit(it) }
    }

    class ReportBuilder {
        var event: String = ""
        var amount: Int = 1
        internal val payload = mutableMapOf<String, String>()

        infix fun String.to(value: Any?) {
            if (value != null) payload[this] = value.toString()
        }

        fun payload(map: Map<String, String>) {
            payload.putAll(map)
        }
    }

    private suspend fun mutateRecord(
        id: String,
        transform: (AchievementRecord) -> Pair<AchievementRecord, Boolean>,
    ) {
        if (!definitions.containsKey(id)) return

        val unlockedId = recordMutex.withLock {
            val records = _records.value.toMutableMap()
            val current = records[id] ?: AchievementRecord(id = id)
            val (updated, unlockedNow) = transform(current)
            records[id] = updated
            save(records)
            _records.value = records
            if (unlockedNow) id else null
        }
        if (unlockedId != null) {
            _unlockEvents.emit(unlockedId)
        }
    }

    private fun AchievementTrigger.matches(payload: Map<String, String>): Boolean {
        return where.all { (key, value) -> payload[key] == value } && conditions.all {
            it.matches(
                payload
            )
        }
    }

    private fun AchievementCondition.matches(payload: Map<String, String>): Boolean {
        val actual = payload[field] ?: return false
        return when (op) {
            AchievementConditionOp.EQ -> actual == value
            AchievementConditionOp.NE -> actual != value
            AchievementConditionOp.GT -> actual.toDoubleOrNull()
                ?.let { it > (value.toDoubleOrNull() ?: return false) } == true

            AchievementConditionOp.GTE -> actual.toDoubleOrNull()
                ?.let { it >= (value.toDoubleOrNull() ?: return false) } == true

            AchievementConditionOp.LT -> actual.toDoubleOrNull()
                ?.let { it < (value.toDoubleOrNull() ?: return false) } == true

            AchievementConditionOp.LTE -> actual.toDoubleOrNull()
                ?.let { it <= (value.toDoubleOrNull() ?: return false) } == true

            AchievementConditionOp.BETWEEN -> {
                val actualNumber = actual.toDoubleOrNull() ?: return false
                val bounds = value.split("..", limit = 2).mapNotNull { it.toDoubleOrNull() }
                bounds.size == 2 && actualNumber >= bounds[0] && actualNumber <= bounds[1]
            }

            AchievementConditionOp.CONTAINS -> actual.contains(value, ignoreCase = true)
            AchievementConditionOp.HAS_ITEM -> actual.split(',').any { it == value }
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
            actualOrdinal !in (endOrdinal + 1)..<startOrdinal
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
        if (record.unlocked) return record to false
        val current = System.currentTimeMillis()
        val updated = when (trigger.mode) {
            AchievementTriggerMode.UNLOCK -> record.copy(
                unlocked = true,
                unlockedAtMillis = current
            )

            AchievementTriggerMode.INCREMENT -> {
                val nextProgress = (record.progress + trigger.amount * eventAmount).coerceAtLeast(0)
                record.withProgress(definition, nextProgress, current)
            }

            AchievementTriggerMode.SET_MAX -> {
                val value = trigger.valueKey.takeIf { it.isNotBlank() }
                    ?.let { payload[it]?.toIntOrNull() }
                    ?: trigger.amount
                record.withProgress(definition, maxOf(record.progress, value), current)
            }

            AchievementTriggerMode.SAME_DAY_COUNT -> {
                val today = payload["date"].orEmpty()
                val key = trigger.dateKey.ifBlank { "${trigger.event}_date" }
                val nextProgress =
                    if (record.extra[key] == today) record.progress + trigger.amount * eventAmount else trigger.amount * eventAmount
                record.withProgress(definition, nextProgress, current)
                    .copy(extra = record.extra + (key to today))
            }

            AchievementTriggerMode.DAILY_STREAK -> {
                val today = payload["date"].orEmpty()
                val key = trigger.dateKey.ifBlank { "${trigger.event}_date" }
                val lastDate = record.extra[key]
                val nextProgress = when {
                    lastDate == today -> record.progress
                    lastDate == null -> trigger.amount * eventAmount
                    runCatching {
                        LocalDate.parse(lastDate).plusDays(1).toString()
                    }.getOrNull() == today -> record.progress + trigger.amount * eventAmount

                    else -> trigger.amount * eventAmount
                }
                record.withProgress(definition, nextProgress, current)
                    .copy(extra = record.extra + (key to today))
            }

            AchievementTriggerMode.RESET -> return record.copy(progress = 0) to false
        }
        return updated to (updated.unlocked)
    }

    private fun AchievementRecord.withProgress(
        definition: AchievementDefinition,
        progress: Int,
        nowMillis: Long,
    ): AchievementRecord {
        val shouldUnlock = definition.target in 1..progress
        return copy(
            progress = progress,
            unlocked = unlocked || shouldUnlock,
            unlockedAtMillis = if (!unlocked && shouldUnlock) nowMillis else unlockedAtMillis,
        )
    }


    private suspend fun save(records: Map<String, AchievementRecord>) {
        context.store.edit {
            it[RECORDS_KEY] = json.encodeToString(records.values.toList())
        }
    }

    suspend fun report(block: ReportBuilder.() -> Unit) {
        val builder = ReportBuilder().apply(block)
        reportEvent(builder.event, builder.payload.toMap(), builder.amount)
    }
}
