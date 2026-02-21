package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.model.copilot.CopilotTaskData
import com.aliothmoon.maameow.data.repository.CopilotRepository
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class CopilotUiState(
    // 当前作业
    val currentCopilot: CopilotTaskData? = null,
    val inputText: String = "",
    val copilotId: Int = 0,
    val isDataFromWeb: Boolean = false,
    val currentJsonContent: String = "",       // 当前作业原始 JSON
    val currentFilePath: String = "",          // 当前作业文件路径

    // 配置选项
    val config: CopilotConfig = CopilotConfig(),

    // 多作业列表
    val useCopilotList: Boolean = false,
    val taskList: List<CopilotListItem> = emptyList(),

    // 状态
    val isLoading: Boolean = false,
    val statusMessage: String = "",
)

class CopilotViewModel(
    private val copilotManager: CopilotManager,
    private val compositionService: MaaCompositionService,
    private val repository: CopilotRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "CopilotViewModel"
    }

    private val _state = MutableStateFlow(CopilotUiState())
    val state: StateFlow<CopilotUiState> = _state.asStateFlow()

    val maaState: StateFlow<MaaExecutionState> = compositionService.state

    // ===== 输入处理 =====

    fun onInputChanged(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    /**
     * 解析输入 (ID 或文件路径)
     */
    fun onParseInput() {
        val input = _state.value.inputText.trim()
        if (input.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, statusMessage = "正在解析...") }

            if (copilotManager.isSetId(input)) {
                // 作业集模式
                importCopilotSet(input)
            } else {
                // 单个作业 (ID 或文件路径)
                parseSingleCopilot(input)
            }
        }
    }

    private suspend fun parseSingleCopilot(input: String) {
        // Try as PRTS ID first
        val result = copilotManager.parseFromId(input)
        result.fold(
            onSuccess = { (id, data, json) ->
                val filePath = repository.saveCopilotJson(id, json)
                _state.update {
                    it.copy(
                        currentCopilot = data,
                        copilotId = id,
                        isDataFromWeb = true,
                        currentJsonContent = json,
                        currentFilePath = filePath,
                        isLoading = false,
                        statusMessage = "作业加载成功: ${data.stageName}",
                    )
                }
            },
            onFailure = { e ->
                // Try as local file
                val fileResult = copilotManager.parseFromFile(input)
                fileResult.fold(
                    onSuccess = { (data, json) ->
                        _state.update {
                            it.copy(
                                currentCopilot = data,
                                copilotId = 0,
                                isDataFromWeb = false,
                                currentJsonContent = json,
                                currentFilePath = input,
                                isLoading = false,
                                statusMessage = "作业加载成功: ${data.stageName}",
                            )
                        }
                    },
                    onFailure = { e2 ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = "解析失败: ${e.message}",
                            )
                        }
                        Timber.e(e2, "$TAG: 解析作业失败")
                    }
                )
            }
        )
    }

    private suspend fun importCopilotSet(input: String) {
        val result = copilotManager.getCopilotSetIds(input)
        result.fold(
            onSuccess = { ids ->
                _state.update { it.copy(statusMessage = "正在导入作业集 (${ids.size} 个)...") }
                val newItems = mutableListOf<CopilotListItem>()
                ids.forEach { id ->
                    val copilotResult = copilotManager.parseFromId(id.toString())
                    copilotResult.onSuccess { (copilotId, data, json) ->
                        val filePath = repository.saveCopilotJson(copilotId, json)
                        newItems.add(
                            CopilotListItem(
                                name = data.stageName,
                                filePath = filePath,
                                copilotId = copilotId,
                            )
                        )
                    }
                }
                _state.update {
                    it.copy(
                        taskList = it.taskList + newItems,
                        useCopilotList = true,
                        isLoading = false,
                        statusMessage = "已导入 ${newItems.size}/${ids.size} 个作业",
                    )
                }
            },
            onFailure = { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "导入作业集失败: ${e.message}",
                    )
                }
            }
        )
    }

    // ===== 配置 =====

    fun onConfigChanged(config: CopilotConfig) {
        _state.update { it.copy(config = config) }
    }

    // ===== 列表操作 =====

    fun onToggleListMode(useCopilotList: Boolean) {
        _state.update { it.copy(useCopilotList = useCopilotList) }
    }

    /**
     * 将当前作业添加到列表
     */
    fun onAddToList(isRaid: Boolean = false) {
        val current = _state.value.currentCopilot ?: return
        val filePath = _state.value.currentFilePath
        if (filePath.isEmpty()) return

        val item = CopilotListItem(
            name = current.stageName,
            filePath = filePath,
            isRaid = isRaid,
            copilotId = _state.value.copilotId,
        )
        _state.update {
            it.copy(
                taskList = it.taskList + item,
                statusMessage = "已添加: ${current.stageName}${if (isRaid) " (突袭)" else ""}",
            )
        }
    }

    fun onRemoveFromList(index: Int) {
        _state.update {
            it.copy(taskList = it.taskList.filterIndexed { i, _ -> i != index })
        }
    }

    fun onClearList() {
        _state.update { it.copy(taskList = emptyList()) }
    }

    fun onToggleListItem(index: Int) {
        _state.update {
            it.copy(taskList = it.taskList.mapIndexed { i, item ->
                if (i == index) item.copy(isChecked = !item.isChecked) else item
            })
        }
    }

    fun onReorderList(from: Int, to: Int) {
        _state.update {
            val list = it.taskList.toMutableList()
            if (from in list.indices && to in list.indices) {
                val item = list.removeAt(from)
                list.add(to, item)
            }
            it.copy(taskList = list)
        }
    }

    // ===== 执行控制 =====

    fun onStart() {
        viewModelScope.launch {
            val state = _state.value
            val params = if (state.useCopilotList) {
                val checkedItems = state.taskList.filter { it.isChecked }
                if (checkedItems.isEmpty()) {
                    _state.update { it.copy(statusMessage = "没有勾选的作业") }
                    return@launch
                }
                copilotManager.buildListTaskParams(checkedItems, state.config)
            } else {
                val filePath = state.currentFilePath
                if (filePath.isEmpty()) {
                    _state.update { it.copy(statusMessage = "请先加载作业") }
                    return@launch
                }
                copilotManager.buildSingleTaskParams(filePath, state.config)
            }

            _state.update { it.copy(statusMessage = "正在启动...") }
            val result = compositionService.startCopilot(params)
            when (result) {
                is MaaCompositionService.StartResult.Success -> {
                    _state.update { it.copy(statusMessage = "自动战斗已启动") }
                }
                else -> {
                    _state.update { it.copy(statusMessage = "启动失败: $result") }
                }
            }
        }
    }

    fun onStop() {
        viewModelScope.launch {
            _state.update { it.copy(statusMessage = "正在停止...") }
            compositionService.stop()
            _state.update { it.copy(statusMessage = "已停止") }
        }
    }

    // ===== 评分 =====

    fun onRate(isLike: Boolean) {
        val id = _state.value.copilotId
        if (id <= 0) return

        viewModelScope.launch {
            val success = copilotManager.rateCopilot(id, isLike)
            _state.update {
                it.copy(statusMessage = if (success) "评分成功" else "评分失败")
            }
        }
    }
}
