package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.maa.callback.ToolboxResultCollector
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class ToolboxTab(val displayName: String) {
    MINI_GAME("小游戏"),
    RECRUIT_CALC("公招识别"),
    DEPOT("仓库识别"),
    OPER_BOX("干员识别"),
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
    private val compositionService: MaaCompositionService,
    val collector: ToolboxResultCollector,
    activityManager: ActivityManager,
) : ViewModel() {

    val miniGame = MiniGameDelegate(activityManager, compositionService, viewModelScope)

    private val _currentTab = MutableStateFlow(ToolboxTab.MINI_GAME)
    val currentTab: StateFlow<ToolboxTab> = _currentTab.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

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
        when (_currentTab.value) {
            ToolboxTab.MINI_GAME -> miniGame.onStart()
            ToolboxTab.RECRUIT_CALC -> onStartRecruitCalc()
            ToolboxTab.DEPOT -> onStartDepot()
            ToolboxTab.OPER_BOX -> onStartOperBox()
        }
    }

    fun onStop() {
        when (_currentTab.value) {
            ToolboxTab.MINI_GAME -> miniGame.onStop()
            else -> viewModelScope.launch {
                _statusMessage.value = "正在停止..."
                compositionService.stop()
                _statusMessage.value = "已停止"
            }
        }
    }

    // ==================== 公招识别 ====================

    private fun onStartRecruitCalc() {
        viewModelScope.launch {
            collector.clearRecruit()
            _statusMessage.value = "正在启动公招识别..."
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
            _statusMessage.value = "正在启动仓库识别..."
            handleStartResult(
                compositionService.startCopilot(listOf(MaaTaskParams(MaaTaskType.DEPOT, "{}")))
            )
        }
    }

    // ==================== 干员识别 ====================

    private fun onStartOperBox() {
        viewModelScope.launch {
            collector.clearOperBox()
            _statusMessage.value = "正在启动干员识别..."
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
        return "[${all.joinToString(",") { op ->
            """{"id":"${op.id}","name":"${op.name}","own":${op.own},"rarity":${op.rarity},"elite":${op.elite},"level":${op.level},"potential":${op.potential}}"""
        }}]"
    }

    private fun handleStartResult(result: MaaCompositionService.StartResult) {
        _statusMessage.value = formatStartResult(result, "识别任务已启动")
    }
}
