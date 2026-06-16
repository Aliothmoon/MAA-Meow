package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.activity.MiniGame
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.resolve
import com.aliothmoon.maameow.utils.i18n.uiTextOf
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
    val statusMessage: UiText = UiText.Empty,
)

class MiniGameDelegate(
    private val appContext: Context,
    activityManager: ActivityManager,
    private val compositionService: MaaCompositionService,
    private val scope: CoroutineScope,
    private val achievementRepository: AchievementRepository,
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
            _state.update {
                it.copy(statusMessage = uiTextOf(R.string.panel_mini_game_not_supported))
            }
            return
        }

        scope.launch {
            val task = buildTaskParams()
            _state.update { it.copy(statusMessage = uiTextOf(R.string.toolbox_status_starting)) }
            val result = compositionService.startCopilot(listOf(task))
            if (result is MaaCompositionService.StartResult.Success) {
                achievementRepository.recordEvent(
                    AchievementEvents.MiniGameStarted,
                    mapOf("task" to _state.value.selectedTaskName),
                )
            }
            _state.update {
                it.copy(
                    statusMessage = appContext.formatStartResult(
                        result,
                        uiTextOf(R.string.panel_mini_game_started),
                    ),
                )
            }
        }
    }

    fun onStop() {
        scope.launch {
            _state.update { it.copy(statusMessage = uiTextOf(R.string.toolbox_status_stopping)) }
            compositionService.stop()
            _state.update { it.copy(statusMessage = uiTextOf(R.string.toolbox_status_stopped)) }
        }
    }

    private fun logCurrentSelection(source: String) {
        val selectedTaskName = _state.value.selectedTaskName
        val game = miniGames.value.find { it.value == selectedTaskName }
        Timber.d(
            "MiniGame[%s]: selectedTaskName=%s, matchedDisplay=%s, matchedValue=%s, tipKey=%s, tip=%s, listSize=%d",
            source,
            selectedTaskName,
            game?.display?.resolve(appContext),
            game?.value,
            game?.tipKey,
            game?.tip?.resolve(appContext)?.replace("\n", "\\n"),
            miniGames.value.size
        )
    }

    companion object {
        val ENDINGS = listOf("A", "B", "C", "D", "E")

        // value 是拼入任务名的流水线片段，必须保持简中；display 走资源本地化
        val EVENTS: List<Pair<String, UiText>> = listOf(
            "" to uiTextOf(R.string.mini_game_sf_event_none),
            "支援作战平台" to uiTextOf(R.string.mini_game_sf_event_support_platform),
            "游侠" to uiTextOf(R.string.mini_game_sf_event_knight_errant),
            "诡影迷踪" to uiTextOf(R.string.mini_game_sf_event_sly_shadows),
        )
    }
}
