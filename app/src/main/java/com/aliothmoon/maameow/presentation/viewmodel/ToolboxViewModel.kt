package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementIds
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.repository.toSortedItems
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.domain.models.RunMode
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

enum class ToolboxTab(@field:StringRes val labelRes: Int) {
    MINI_GAME(R.string.toolbox_tab_mini_game),
    RECRUIT_CALC(R.string.toolbox_tab_recruit_calc),
    DEPOT(R.string.maa_depot),
    OPER_BOX(R.string.panel_operbox_title),
    GACHA(R.string.toolbox_tab_gacha),
    ;

    companion object {
        /** 前台模式不展示牛牛抽卡 */
        fun visibleFor(runMode: RunMode): List<ToolboxTab> =
            if (runMode == RunMode.FOREGROUND) {
                entries.filter { it != GACHA }
            } else {
                entries.toList()
            }
    }
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
    private val achievementRepository: AchievementRepository,
    val depotRepository: DepotRepository,
    val operBoxRepository: OperBoxRepository,
    private val itemHelper: ItemHelper,
    private val appSettingsManager: AppSettingsManager,
) : ViewModel() {

    val miniGame = MiniGameDelegate(appContext, activityManager, compositionService, viewModelScope, achievementRepository)

    private val _currentTab = MutableStateFlow(ToolboxTab.MINI_GAME)
    val currentTab: StateFlow<ToolboxTab> = _currentTab.asStateFlow()

    /** 可见子 Tab：前台模式隐藏牛牛抽卡。 */
    val visibleTabs: StateFlow<List<ToolboxTab>> = appSettingsManager.runMode
        .map { ToolboxTab.visibleFor(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ToolboxTab.visibleFor(appSettingsManager.runMode.value),
        )

    private val _statusMessage = MutableStateFlow<UiText>(UiText.Empty)
    val statusMessage: StateFlow<UiText> = _statusMessage.asStateFlow()

    private val _dialog = MutableStateFlow<PanelDialogUiState?>(null)
    val dialog: StateFlow<PanelDialogUiState?> = _dialog.asStateFlow()

    private var pendingStartContext: TaskStartContext? = null
    /** 牛牛抽卡：底部「开始任务」或面板按钮共用；null = 非 gacha 启动。 */
    private var pendingGachaOnce: Boolean? = null
    private var gachaTipJob: Job? = null

    // ==================== 牛牛抽卡（对齐 WPF Toolbox Gacha）====================

    private val _gachaDisclaimerAccepted = MutableStateFlow(false)
    val gachaDisclaimerAccepted: StateFlow<Boolean> = _gachaDisclaimerAccepted.asStateFlow()

    private val _gachaTip = MutableStateFlow(uiTextOf(R.string.gacha_init_tip))
    val gachaTip: StateFlow<UiText> = _gachaTip.asStateFlow()

    init {
        // 切到前台时若正停在抽卡 Tab，回退到牛杂
        viewModelScope.launch {
            appSettingsManager.runMode.collect { mode ->
                if (mode == RunMode.FOREGROUND && _currentTab.value == ToolboxTab.GACHA) {
                    _currentTab.value = ToolboxTab.MINI_GAME
                    pendingGachaOnce = null
                    stopGachaTipRotation()
                }
            }
        }
    }

    fun onGachaAgreeDisclaimer() {
        viewModelScope.launch {
            achievementRepository.unlock(AchievementIds.REAL_GACHA)
            _gachaDisclaimerAccepted.value = true
            _gachaTip.value = uiTextOf(R.string.gacha_init_tip)
        }
    }

    /** 面板「寻访一次 / 十次」入口。 */
    fun onStartGacha(once: Boolean) {
        if (appSettingsManager.runMode.value == RunMode.FOREGROUND) return
        if (!_gachaDisclaimerAccepted.value) {
            _statusMessage.value = uiTextOf(R.string.gacha_need_disclaimer)
            return
        }
        pendingGachaOnce = once
        onStart(TaskStartContext(TaskStartMode.MANUAL))
    }

    // ==================== 公招识别配置 ====================

    private val _recruitConfig = MutableStateFlow(RecruitCalcConfig())
    val recruitConfig: StateFlow<RecruitCalcConfig> = _recruitConfig.asStateFlow()

    fun onRecruitConfigChange(config: RecruitCalcConfig) {
        _recruitConfig.value = config
    }

    fun onTabChange(tab: ToolboxTab) {
        if (tab == ToolboxTab.GACHA &&
            appSettingsManager.runMode.value == RunMode.FOREGROUND
        ) {
            return
        }
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
                    pendingGachaOnce = null
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
            ToolboxTab.MINI_GAME -> {
                pendingGachaOnce = null
                miniGame.onStart()
            }
            ToolboxTab.GACHA -> {
                if (appSettingsManager.runMode.value == RunMode.FOREGROUND) {
                    pendingGachaOnce = null
                    return
                }
                val once = pendingGachaOnce ?: true
                pendingGachaOnce = null
                doStartGacha(once)
            }
            ToolboxTab.RECRUIT_CALC -> {
                pendingGachaOnce = null
                onStartRecruitCalc()
            }
            ToolboxTab.DEPOT -> {
                pendingGachaOnce = null
                onStartDepot()
            }
            ToolboxTab.OPER_BOX -> {
                pendingGachaOnce = null
                onStartOperBox()
            }
        }
    }

    private fun doStartGacha(once: Boolean) {
        viewModelScope.launch {
            if (!_gachaDisclaimerAccepted.value) {
                _statusMessage.value = uiTextOf(R.string.gacha_need_disclaimer)
                return@launch
            }
            _statusMessage.value = uiTextOf(R.string.toolbox_status_starting_gacha)
            val taskName = if (once) "GachaOnce" else "GachaTenTimes"
            val params = buildJsonObject {
                put("task_names", buildJsonArray { add(JsonPrimitive(taskName)) })
            }.toString()
            val result = compositionService.startCopilot(
                listOf(MaaTaskParams(MaaTaskType.CUSTOM, params)),
            )
            handleStartResult(result, uiTextOf(R.string.toolbox_status_gacha_started))
            if (result is MaaCompositionService.StartResult.Success) {
                startGachaTipRotation()
            }
        }
    }

    private fun startGachaTipRotation() {
        gachaTipJob?.cancel()
        gachaTipJob = viewModelScope.launch {
            while (isActive) {
                val tipRes = GACHA_TIP_RES_IDS[Random.nextInt(GACHA_TIP_RES_IDS.size)]
                _gachaTip.value = uiTextOf(tipRes)
                delay(5_000)
            }
        }
    }

    private fun stopGachaTipRotation() {
        gachaTipJob?.cancel()
        gachaTipJob = null
        _gachaTip.value = uiTextOf(R.string.gacha_init_tip)
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
        pendingGachaOnce = null
        _dialog.value = null
    }

    fun onStop() {
        when (_currentTab.value) {
            ToolboxTab.MINI_GAME -> miniGame.onStop()
            ToolboxTab.GACHA -> viewModelScope.launch {
                _statusMessage.value = uiTextOf(R.string.toolbox_status_stopping)
                compositionService.stop()
                stopGachaTipRotation()
                _statusMessage.value = uiTextOf(R.string.toolbox_status_stopped)
            }
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

    private fun handleStartResult(
        result: MaaCompositionService.StartResult,
        successMessage: UiText = uiTextOf(R.string.toolbox_status_started),
    ) {
        _statusMessage.value = appContext.formatStartResult(result, successMessage)
    }

    companion object {
        /** 对齐 WPF GachaTip1..17 */
        private val GACHA_TIP_RES_IDS = intArrayOf(
            R.string.gacha_tip_1,
            R.string.gacha_tip_2,
            R.string.gacha_tip_3,
            R.string.gacha_tip_4,
            R.string.gacha_tip_5,
            R.string.gacha_tip_6,
            R.string.gacha_tip_7,
            R.string.gacha_tip_8,
            R.string.gacha_tip_9,
            R.string.gacha_tip_10,
            R.string.gacha_tip_11,
            R.string.gacha_tip_12,
            R.string.gacha_tip_13,
            R.string.gacha_tip_14,
            R.string.gacha_tip_15,
            R.string.gacha_tip_16,
            R.string.gacha_tip_17,
        )
    }
}
