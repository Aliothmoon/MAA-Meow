package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.model.activity.MiniGame
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber

private const val SECRET_FRONT_VALUE = "MiniGame@SecretFront"

/** 热更的 StageActivityV2 下发的是带 @Begin 的写法，两种都认，对齐上游 IsPixelPaint */
private val PIXEL_PAINT_VALUES = setOf("MiniGame@PixelPaint", "MiniGame@PixelPaint@Begin")

/** 实际下发的任务名固定，与选中项的 Value 无关，对齐上游 AsstPixelPaint */
private const val PIXEL_PAINT_TASK = "MiniGame@PixelPaint@Begin"
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
    private val pixelArt: PixelArtDelegate,
    private val sessionLogger: MaaSessionLogger,
) {

    private val _state = MutableStateFlow(MiniGameUiState())
    val state: StateFlow<MiniGameUiState> = _state.asStateFlow()

    val miniGames: StateFlow<List<MiniGame>> = activityManager.miniGames

    fun isSecretFront(selectedTaskName: String): Boolean =
        selectedTaskName == SECRET_FRONT_VALUE

    fun isPixelPaint(selectedTaskName: String): Boolean =
        selectedTaskName in PIXEL_PAINT_VALUES

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
        if (isPixelPaint(snapshot.selectedTaskName)) {
            return PIXEL_PAINT_TASK
        }
        if (snapshot.selectedTaskName == SECRET_FRONT_VALUE) {
            val base = "${snapshot.selectedTaskName}@Begin@Ending${snapshot.selectedEnding}"
            return if (snapshot.selectedEvent.isNotBlank()) "$base@${snapshot.selectedEvent}" else base
        }
        return snapshot.selectedTaskName
    }

    private fun buildTaskParams(): MaaTaskParams {
        val taskName = buildTaskName()
        val pixelArtState = pixelArt.state.value
        val params = buildJsonObject {
            putJsonArray("task_names") { add(JsonPrimitive(taskName)) }
            // 像素画额外带 params.pixel_paint.groups，由 Core 的 PixelPaintTaskPlugin 消费
            if (isPixelPaint(_state.value.selectedTaskName)) {
                pixelArtState.plan?.let { plan ->
                    putJsonObject("params") {
                        putJsonObject("pixel_paint") {
                            put("swipe", pixelArtState.swipeEnabled)
                            put("grid_click_delay", pixelArtState.gridClickDelayMs)
                            putJsonArray("groups") {
                                plan.groups.forEach { group ->
                                    addJsonObject {
                                        put("color", group.color)
                                        putJsonArray("points") {
                                            group.points.forEach { point ->
                                                addJsonArray {
                                                    add(point[0])
                                                    add(point[1])
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
        return MaaTaskParams(MaaTaskType.CUSTOM, params)
    }

    fun onStart() {
        val selectedTaskName = _state.value.selectedTaskName
        val pixelPaint = isPixelPaint(selectedTaskName)
        if (pixelPaint) {
            val plan = pixelArt.state.value.plan
            if (plan == null) {
                _state.update { it.copy(statusMessage = uiTextOf(R.string.pixel_art_need_image)) }
                return
            }
            if (plan.groups.isEmpty()) {
                _state.update { it.copy(statusMessage = uiTextOf(R.string.pixel_art_nothing_to_paint)) }
                return
            }
        }
        if (findGame(selectedTaskName)?.isUnsupported == true) {
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
                achievementRepository.report {
                    event = AchievementEvents.MINI_GAME_STARTED
                    "task" to selectedTaskName
                }
                // 面板可能被收起，格数/色数只有客户端知道，写进运行日志留痕
                if (pixelPaint) {
                    pixelArt.state.value.plan?.let { plan ->
                        sessionLogger.append(
                            appContext.getString(
                                R.string.pixel_art_status_started,
                                plan.paintedCellCount,
                                plan.groups.size,
                            ),
                            LogLevel.INFO,
                        )
                    }
                }
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
