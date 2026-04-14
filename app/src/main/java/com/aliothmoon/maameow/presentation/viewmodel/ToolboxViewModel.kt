package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.service.AppAliveChecker
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.remote.AppAliveStatus
import com.aliothmoon.maameow.maa.callback.ToolboxResultCollector
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class ToolboxTab(@field:StringRes val labelRes: Int) {
    MINI_GAME(R.string.toolbox_tab_mini_game),
    RECRUIT_CALC(R.string.toolbox_tab_recruit_calc),
    DEPOT(R.string.maa_depot),
    OPER_BOX(R.string.panel_operbox_title),
}

data class RecruitCalcConfig(
    val chooseLevel3: Boolean = true,
    val chooseLevel4: Boolean = true,
    val chooseLevel5: Boolean = true,
    val chooseLevel6: Boolean = true,
    val autoSetTime: Boolean = true,
    val level3Time: Int = 540,
    val level4Time: Int = 540,
    val level5Time: Int = 540,
)

class ToolboxViewModel(
    private val appContext: Context,
    private val compositionService: MaaCompositionService,
    val collector: ToolboxResultCollector,
    activityManager: ActivityManager,
    private val appAliveChecker: AppAliveChecker,
    private val chainState: TaskChainState,
) : ViewModel() {

    val miniGame = MiniGameDelegate(appContext, activityManager, compositionService, viewModelScope)

    private val _currentTab = MutableStateFlow(ToolboxTab.MINI_GAME)
    val currentTab: StateFlow<ToolboxTab> = _currentTab.asStateFlow()

    private val _statusMessage = MutableStateFlow<UiText>(UiText.Empty)
    val statusMessage: StateFlow<UiText> = _statusMessage.asStateFlow()

    private val _dialog = MutableStateFlow<PanelDialogUiState?>(null)
    val dialog: StateFlow<PanelDialogUiState?> = _dialog.asStateFlow()

    private var gameNotRunningAcknowledged = false

    // ==================== 公招识别配置 ====================

    private val _recruitConfig = MutableStateFlow(RecruitCalcConfig())
    val recruitConfig: StateFlow<RecruitCalcConfig> = _recruitConfig.asStateFlow()

    fun onRecruitConfigChange(config: RecruitCalcConfig) {
        _recruitConfig.value = config
    }

    fun onTabChange(tab: ToolboxTab) {
        _currentTab.value = tab
    }

    // ==================== 统一启动/停止 ====================

    fun onStart() {
        viewModelScope.launch {
            if (!gameNotRunningAcknowledged) {
                val pkg = Packages[chainState.getClientType()]
                if (pkg != null && appAliveChecker.isAppAlive(pkg) == AppAliveStatus.DEAD) {
                    _dialog.value = PanelDialogUiState(
                        type = PanelDialogType.WARNING,
                        title = uiTextOf(R.string.toolbox_dialog_start_warning_title),
                        message = appContext.resolveGameNotRunningWarningMessage(),
                        confirmText = uiTextOf(R.string.toolbox_dialog_start_anyway),
                        dismissText = uiTextOf(R.string.common_cancel),
                        confirmAction = PanelDialogConfirmAction.CONFIRM_PENDING_START,
                    )
                    return@launch
                }
            }
            gameNotRunningAcknowledged = false
            doStart()
        }
    }

    private fun doStart() {
        when (_currentTab.value) {
            ToolboxTab.MINI_GAME -> miniGame.onStart()
            ToolboxTab.RECRUIT_CALC -> onStartRecruitCalc()
            ToolboxTab.DEPOT -> onStartDepot()
            ToolboxTab.OPER_BOX -> onStartOperBox()
        }
    }

    fun onDialogConfirm() {
        _dialog.value = null
        gameNotRunningAcknowledged = true
        onStart()
    }

    fun onDialogDismiss() {
        _dialog.value = null
    }

    fun onStop() {
        when (_currentTab.value) {
            ToolboxTab.MINI_GAME -> miniGame.onStop()
            else -> viewModelScope.launch {
                _statusMessage.value = uiTextOf(R.string.toolbox_status_stopping)
                compositionService.stop()
                _statusMessage.value = uiTextOf(R.string.toolbox_status_stopped)
            }
        }
    }

    // ==================== 公招识别 ====================

    private fun onStartRecruitCalc() {
        viewModelScope.launch {
            collector.clearRecruit()
            _statusMessage.value = uiTextOf(R.string.toolbox_status_starting_recruit_calc)
            val cfg = _recruitConfig.value
            val selectList = buildJsonArray {
                if (cfg.chooseLevel3) add(3)
                if (cfg.chooseLevel4) add(4)
                if (cfg.chooseLevel5) add(5)
                if (cfg.chooseLevel6) add(6)
            }
            val params = buildJsonObject {
                put("select", selectList)
                put("confirm", buildJsonArray { add(JsonPrimitive(-1)) })
                put("times", 0)
                put("set_time", cfg.autoSetTime)
                put("expedite", false)
                if (cfg.autoSetTime) {
                    put("recruitment_time", buildJsonObject {
                        put("3", cfg.level3Time)
                        put("4", cfg.level4Time)
                        put("5", cfg.level5Time)
                    })
                }
            }.toString()
            handleStartResult(
                compositionService.startCopilot(listOf(MaaTaskParams(MaaTaskType.RECRUIT, params)))
            )
        }
    }

    // ==================== 仓库识别 ====================

    private fun onStartDepot() {
        viewModelScope.launch {
            collector.clearDepot()
            _statusMessage.value = uiTextOf(R.string.toolbox_status_starting_depot)
            handleStartResult(
                compositionService.startCopilot(listOf(MaaTaskParams(MaaTaskType.DEPOT, "{}")))
            )
        }
    }

    // ==================== 干员识别 ====================

    private fun onStartOperBox() {
        viewModelScope.launch {
            collector.clearOperBox()
            _statusMessage.value = uiTextOf(R.string.toolbox_status_starting_oper_box)
            handleStartResult(
                compositionService.startCopilot(listOf(MaaTaskParams(MaaTaskType.OPER_BOX, "{}")))
            )
        }
    }

    // ==================== 导出 ====================

    fun exportDepotArkPlanner(): String {
        val items = collector.depotItems.value
        val itemsJson = items.joinToString(",") { """{"id":"${it.id}","have":${it.count}}""" }
        return """{"@type":"@penguin-statistics/depot","items":[$itemsJson]}"""
    }

    fun exportDepotLolicon(): String {
        val items = collector.depotItems.value
        return "{${items.joinToString(",") { "\"${it.id}\":${it.count}" }}}"
    }

    fun exportOperBox(): String {
        val result = collector.operBoxResult.value ?: return "[]"
        val all = result.owned + result.notOwned
        return "[${
            all.joinToString(",") { op ->
                """{"id":"${op.id}","name":"${op.name}","own":${op.own},"rarity":${op.rarity},"elite":${op.elite},"level":${op.level},"potential":${op.potential}}"""
            }
        }]"
    }

    private fun handleStartResult(result: MaaCompositionService.StartResult) {
        _statusMessage.value =
            appContext.formatStartResult(result, uiTextOf(R.string.toolbox_status_started))
    }
}
