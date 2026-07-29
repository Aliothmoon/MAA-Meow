package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.repository.toSortedItems
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.usecase.CheckGameReadinessUseCase
import com.aliothmoon.maameow.domain.usecase.GameReadiness
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.data.model.toolbox.OperBoxExportFormatter
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.maa.callback.ToolboxResultCollector
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
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
    private val checkGameReadiness: CheckGameReadinessUseCase,
    private val chainState: TaskChainState,
    achievementRepository: AchievementRepository,
    val depotRepository: DepotRepository,
    val operBoxRepository: OperBoxRepository,
    private val itemHelper: ItemHelper,
) : ViewModel() {

    val miniGame = MiniGameDelegate(appContext, activityManager, compositionService, viewModelScope, achievementRepository)

    private val _currentTab = MutableStateFlow(ToolboxTab.MINI_GAME)
    val currentTab: StateFlow<ToolboxTab> = _currentTab.asStateFlow()

    private val _statusMessage = MutableStateFlow<UiText>(UiText.Empty)
    val statusMessage: StateFlow<UiText> = _statusMessage.asStateFlow()

    private val _dialog = MutableStateFlow<PanelDialogUiState?>(null)
    val dialog: StateFlow<PanelDialogUiState?> = _dialog.asStateFlow()

    private var pendingStartContext: TaskStartContext? = null

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

    fun onStart() = onStart(TaskStartContext(TaskStartMode.MANUAL))

    private fun onStart(context: TaskStartContext) {
        viewModelScope.launch {
            when (val readiness = checkGameReadiness(
                clientType = chainState.clientType,
                launchesGame = false,
                context = context,
            )) {
                is GameReadiness.RequiresConfirmation -> {
                    pendingStartContext = context.acknowledged(readiness.acknowledgement)
                    _dialog.value = appContext.createStartWarningDialog(
                        appContext.resolveTaskStartConfirmationMessage(readiness.acknowledgement)
                    )
                    return@launch
                }

                is GameReadiness.Blocked -> {
                    pendingStartContext = null
                    _dialog.value = appContext.createStartBlockedDialog(
                        appContext.resolveTaskStartBlockedMessage(readiness.reason)
                    )
                    return@launch
                }

                is GameReadiness.Ready -> pendingStartContext = null
            }
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
        when (_dialog.value?.confirmAction) {
            PanelDialogConfirmAction.CONFIRM_PENDING_START -> {
                val pending = pendingStartContext
                _dialog.value = null
                pendingStartContext = null
                if (pending != null) onStart(pending)
            }

            else -> _dialog.value = null
        }
    }

    fun onDialogDismiss() {
        pendingStartContext = null
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
            // 不 clear 持久化快照：识别失败时仍可看历史；成功 set 后自动刷新
            _statusMessage.value = uiTextOf(R.string.toolbox_status_starting_depot)
            handleStartResult(
                compositionService.startCopilot(listOf(MaaTaskParams(MaaTaskType.DEPOT, "{}")))
            )
        }
    }

    // ==================== 干员识别 ====================

    private fun onStartOperBox() {
        viewModelScope.launch {
            _statusMessage.value = uiTextOf(R.string.toolbox_status_starting_oper_box)
            handleStartResult(
                compositionService.startCopilot(listOf(MaaTaskParams(MaaTaskType.OPER_BOX, "{}")))
            )
        }
    }

    // ==================== 导出（与屏幕同源：Repository 快照）====================

    fun exportDepotArkPlanner(): String {
        val items = depotRepository.snapshot.value.toSortedItems(itemHelper.items.value)
        val itemsJson = items.joinToString(",") { """{"id":"${it.id}","have":${it.count}}""" }
        return """{"@type":"@penguin-statistics/depot","items":[$itemsJson]}"""
    }

    fun exportDepotLolicon(): String {
        val items = depotRepository.snapshot.value.toSortedItems(itemHelper.items.value)
        return "{${items.joinToString(",") { "\"${it.id}\":${it.count}" }}}"
    }

    /** 干员识别导出列表：owned + notOwned（全部可用干员）。 */
    fun exportOperBoxList(): List<OperBoxOperator> {
        val snap = operBoxRepository.snapshot.value
        if (!snap.hasSynced) return emptyList()
        return snap.owned + snap.notOwned
    }

    /** 干员识别导出为 JSON（剪贴板与 .json 文件共用）。 */
    fun exportOperBox(): String = OperBoxExportFormatter.toJson(exportOperBoxList())

    private fun handleStartResult(result: MaaCompositionService.StartResult) {
        _statusMessage.value =
            appContext.formatStartResult(result, uiTextOf(R.string.toolbox_status_started))
    }
}
