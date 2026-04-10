package com.aliothmoon.maameow.presentation.viewmodel

import android.app.Application
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.activity.MiniGame
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import timber.log.Timber

private const val SECRET_FRONT_VALUE = "MiniGame@SecretFront"
private const val DEFAULT_TASK_NAME = "SS@Store@Begin"

data class MiniGameUiState(
    val selectedTaskName: String = DEFAULT_TASK_NAME,
    val selectedEnding: String = "A",
    val selectedEvent: String = "",
    val statusMessage: String = "",
)

class MiniGameDelegate(
    activityManager: ActivityManager,
    private val app: Application,
    private val compositionService: MaaCompositionService,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(MiniGameUiState())
    val state: StateFlow<MiniGameUiState> = _state.asStateFlow()

    val miniGames: StateFlow<List<MiniGame>> = activityManager.miniGames

    fun isSecretFront(selectedTaskName: String): Boolean =
        selectedTaskName == SECRET_FRONT_VALUE

    fun onTaskSelected(value: String) {
        _state.update { it.copy(selectedTaskName = value) }
        logCurrentSelection("onTaskSelected")
    }

    fun onEndingSelected(ending: String) {
        _state.update { it.copy(selectedEnding = ending) }
    }

    fun onEventSelected(event: String) {
        _state.update { it.copy(selectedEvent = event) }
    }

    fun findGame(selectedTaskName: String): MiniGame? =
        miniGames.value.find { it.value == selectedTaskName }

    private fun buildTaskName(): String {
        val snapshot = _state.value
        if (snapshot.selectedTaskName == SECRET_FRONT_VALUE) {
            val base = "${snapshot.selectedTaskName}@Begin@Ending${snapshot.selectedEnding}"
            return if (snapshot.selectedEvent.isNotBlank()) "$base@${snapshot.selectedEvent}" else base
        }
        return snapshot.selectedTaskName
    }

    private fun buildTaskParams(): MaaTaskParams {
        val taskName = buildTaskName()
        val params = buildJsonObject {
            putJsonArray("task_names") { add(JsonPrimitive(taskName)) }
        }.toString()
        return MaaTaskParams(MaaTaskType.CUSTOM, params)
    }

    fun onStart() {
        if (findGame(_state.value.selectedTaskName)?.isUnsupported == true) {
            _state.update { it.copy(statusMessage = app.getString(R.string.version_not_supported)) }
            return
        }

        scope.launch {
            val task = buildTaskParams()
            _state.update { it.copy(statusMessage = app.getString(R.string.task_starting)) }
            val result = compositionService.startCopilot(listOf(task))
            _state.update {
                it.copy(statusMessage = app.formatStartResult(result, app.getString(R.string.mini_game_task_started)))
            }
        }
    }

    fun onStop() {
        scope.launch {
            _state.update { it.copy(statusMessage = app.getString(R.string.task_stopping)) }
            compositionService.stop()
            _state.update { it.copy(statusMessage = app.getString(R.string.task_stopped)) }
        }
    }

    private fun logCurrentSelection(source: String) {
        val selectedTaskName = _state.value.selectedTaskName
        val game = miniGames.value.find { it.value == selectedTaskName }
        Timber.d(
            "MiniGame[%s]: selectedTaskName=%s, matchedDisplay=%s, matchedValue=%s, tipKey=%s, tip=%s, listSize=%d",
            source,
            selectedTaskName,
            game?.display,
            game?.value,
            game?.tipKey,
            game?.tip?.replace("\n", "\\n"),
            miniGames.value.size
        )
    }

    val events: List<Pair<String, String>>
        get() = listOf(
            "" to app.getString(R.string.mini_game_event_none),
            "支援作战平台" to app.getString(R.string.mini_game_event_support_platform),
            "游侠" to app.getString(R.string.mini_game_event_ranger),
            "诡影迷踪" to app.getString(R.string.mini_game_event_phantom_tracking),
        )

    companion object {
        val ENDINGS = listOf("A", "B", "C", "D", "E")
    }
}
