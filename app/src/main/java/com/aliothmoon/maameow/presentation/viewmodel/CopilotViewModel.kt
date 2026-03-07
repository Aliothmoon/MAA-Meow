package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.model.copilot.CopilotTaskData
import com.aliothmoon.maameow.data.model.copilot.DifficultyFlags
import com.aliothmoon.maameow.data.repository.CopilotRepository
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.domain.service.CopilotRequestException
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.callback.CopilotRuntimeStateStore
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TAB_MAIN = 0
private const val TAB_SSS = 1
private const val TAB_PARADOX = 2
private const val TAB_OTHER_ACTIVITY = 3
private val DIRECT_STAGE_NAME_REGEX = Regex("""^[0-9a-z-]+$""")
private val STAGE_NAME_REGEX =
    Regex(
        """[a-z]{0,3}\d{0,2}-(?:(?:A|B|C|D|EX|S|TR|MO)-?)?\d{1,2}""",
        RegexOption.IGNORE_CASE
    )
private const val MSG_NAVIGATION_NAME_MISMATCH =
    """当前作业关卡名与导航关卡名不一致，请确认是否仍可正确导航"""

private data class ResolvedStageNavigation(
    val stageCode: String?,
    val stageId: String?,
    val navigateName: String,
    val hasMapMatch: Boolean,
) {
    val hasNavigateNameOverride: Boolean
        get() = !stageCode.isNullOrBlank() && navigateName.isNotBlank() && stageCode != navigateName
}

data class CopilotUiState(
    val tabIndex: Int = TAB_MAIN,
    val inputText: String = "",
    val currentCopilot: CopilotTaskData? = null,
    val currentTaskType: MaaTaskType = MaaTaskType.COPILOT,
    val copilotId: Int = 0,
    val canLike: Boolean = false,
    val isDataFromWeb: Boolean = false,
    val currentJsonContent: String = "",
    val currentFilePath: String = "",
    val copilotTaskName: String = "",
    val config: CopilotConfig = CopilotConfig(),
    val useCopilotList: Boolean = false,
    val taskList: List<CopilotListItem> = emptyList(),
    val hasRequirementIgnored: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String = "",
    val videoUrl: String = "",
    val operatorSummary: String = "",
)

class CopilotViewModel(
    private val copilotManager: CopilotManager,
    private val compositionService: MaaCompositionService,
    private val repository: CopilotRepository,
    private val resourceDataManager: ResourceDataManager,
    private val runtimeStateStore: CopilotRuntimeStateStore,
) : ViewModel() {

    companion object {
        private const val TAG = "CopilotViewModel"

        private const val MSG_COPILOT_EMPTY = "作业为空"
        private const val MSG_TYPE_MISMATCH = "当前选择的作业与页签不匹配"
        private const val MSG_COPILOT_NOT_FOUND = "未找到对应作业！"
        private const val MSG_COPILOT_SET_NOT_FOUND = "未找到对应作业集！"
        private const val MSG_NETWORK_SERVICE_ERROR = "请求网络服务错误！"
        private const val MSG_COPILOT_JSON_ERROR = "解析作业文件错误！"
        private const val MSG_EMPTY_LIST = "正在使用 ｢战斗列表｣, 但未添加任何作业"
        private const val MSG_MIXED_LIST =
            "正在使用 ｢战斗列表｣，但不允许混用「主线/故事集/SideStory」与「悖论模拟」，请分别在对应页签建立列表后再启动"
        private const val MSG_LEGACY_LIST =
            "正在使用 ｢战斗列表｣，但列表包含旧版本条目（缺少页签信息），请在正确的页签重新添加这些作业后再启动"
        private const val MSG_TASK_NAME_EMPTY = "存在关卡名为空的作业"
        private const val MSG_RATE_FAILED = "出现错误，评价失败 : <"
        private const val MSG_RATE_SUCCESS = "感谢评价！\n网页已经开放评论区，欢迎前往留下你的评论！"
        private const val MSG_SINGLE_LIST_WARN =
            "正在使用 ｢战斗列表｣ 执行单个作业, 不推荐此行为。 单个作业请直接运行"
    }

    private val _state = MutableStateFlow(CopilotUiState())
    val state: StateFlow<CopilotUiState> = _state.asStateFlow()

    val maaState: StateFlow<MaaExecutionState> = compositionService.state

    private val pendingCopilotIds = mutableListOf<Int>()
    private val recentlyRatedCopilotIds = mutableSetOf<Int>()
    private val ratingInFlightCopilotIds = mutableSetOf<Int>()

    init {
        restoreState()
        observeRuntimeState()
    }

    private fun restoreState() {
        viewModelScope.launch {
            val config = repository.loadConfig() ?: CopilotConfig()
            val taskList = repository.loadTaskList()
            _state.update { it.copy(config = config, taskList = taskList) }
        }
    }

    private fun observeRuntimeState() {
        viewModelScope.launch {
            runtimeStateStore.hasRequirementIgnored.collect { ignored ->
                _state.update { it.copy(hasRequirementIgnored = ignored) }
            }
        }
        viewModelScope.launch {
            runtimeStateStore.taskSuccessToken.drop(1).collect {
                onCopilotTaskSuccess()
            }
        }
    }

    fun onTabChanged(tabIndex: Int) {
        _state.update { applyTabConstraints(it, tabIndex) }
        persistConfig()
    }

    fun onInputChanged(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun onParseSingleInput() {
        parseInput(forceSet = false)
    }

    fun onParseSetInput() {
        parseInput(forceSet = true)
    }

    private fun parseInput(forceSet: Boolean) {
        val input = _state.value.inputText.trim()
        if (input.isEmpty()) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    statusMessage = "正在解析...",
                    currentCopilot = null,
                    operatorSummary = "",
                    videoUrl = "",
                )
            }
            if (forceSet || copilotManager.isSetId(input)) {
                val tabIndex = _state.value.tabIndex
                if (!supportsCopilotSetImport(tabIndex)) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "${getCopilotTabName(tabIndex)}不支持作业集导入"
                        )
                    }
                    return@launch
                }
                importCopilotSet(input)
            } else {
                parseSingleCopilot(input)
            }
        }
    }

    private suspend fun parseSingleCopilot(input: String) {
        val result = copilotManager.parseFromId(input)
        result.fold(
            onSuccess = { (id, data, json) ->
                val filePath = repository.saveCopilotJson(id, json)
                applyLoadedCopilot(
                    data = data,
                    json = json,
                    filePath = filePath,
                    copilotId = id,
                    fromWeb = true
                )
                autoAddLoadedCopilotToListIfNeeded(
                    data = data,
                    filePath = filePath,
                    copilotId = id,
                    source = "web"
                )
            },
            onFailure = { remoteErr ->
                val unsupportedLocalPath = input.contains("\\") || input.contains("/")
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = if (unsupportedLocalPath) {
                            "暂不支持本地文件路径，请输入神秘代码"
                        } else {
                            mapSingleCopilotRequestError(input, remoteErr)
                        }
                    )
                }
                Timber.e(remoteErr, "$TAG: 解析作业失败")
            }
        )
    }

    private suspend fun importCopilotSet(input: String) {
        val result = copilotManager.getCopilotSetInfo(input)
        result.fold(
            onSuccess = { setInfo ->
                val ids = setInfo.copilotIds
                _state.update { it.copy(statusMessage = "正在导入作业集(${ids.size} 个)...") }

                if (ids.isEmpty()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = buildSetStatusMessage(
                                setName = setInfo.name,
                                setDescription = setInfo.description,
                                summary = "作业集为空"
                            )
                        )
                    }
                    return@fold
                }

                var workingTabIndex = _state.value.tabIndex
                val newItems = mutableListOf<CopilotListItem>()
                var failedCount = 0
                var firstFailureReason: String? = null
                ids.forEach { id ->
                    val copilotResult = copilotManager.parseFromId(id.toString())
                    copilotResult.fold(
                        onSuccess = { (copilotId, data, json) ->
                            val filePath = repository.saveCopilotJson(copilotId, json)
                            val resolvedTabIndex = resolveLoadedTabIndex(data, workingTabIndex)
                            newItems.addAll(
                                createListItemsForLoadedCopilot(
                                    data = data,
                                    filePath = filePath,
                                    copilotId = copilotId,
                                    tabIndex = resolvedTabIndex,
                                    source = "web"
                                )
                            )
                            workingTabIndex = resolvedTabIndex
                        },
                        onFailure = { e ->
                            failedCount += 1
                            if (firstFailureReason == null) {
                                firstFailureReason = mapSingleCopilotRequestError(id.toString(), e)
                            }
                        }
                    )
                }
                val summary = if (failedCount > 0) {
                    buildString {
                        append("已导入 ${newItems.size} 条作业（来自 ${ids.size} 个神秘代码，${failedCount} 个读取失败）")
                        if (!firstFailureReason.isNullOrBlank()) {
                            append("\n")
                            append(firstFailureReason)
                        }
                    }
                } else {
                    "已导入 ${newItems.size} 条作业（来自 ${ids.size} 个神秘代码）"
                }
                val previousTabIndex = _state.value.tabIndex
                _state.update { current ->
                    val base = applyTabConstraints(current, workingTabIndex)
                    val listModeEnabled = supportsBattleList(workingTabIndex)
                    base.copy(
                        taskList = base.taskList + newItems,
                        useCopilotList = listModeEnabled,
                        config = if (listModeEnabled) base.config.copy(formation = true) else base.config,
                        isLoading = false,
                        statusMessage = buildSetStatusMessage(
                            setName = setInfo.name,
                            setDescription = setInfo.description,
                            summary = summary
                        )
                    )
                }
                if (previousTabIndex != workingTabIndex) {
                    persistConfig()
                }
                persistTaskList()
            },
            onFailure = { e ->
                Timber.e(e, "$TAG: 导入作业集失败")
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = mapCopilotSetRequestError(input, e)
                    )
                }
            }
        )
    }

    private fun mapSingleCopilotRequestError(input: String, error: Throwable): String {
        return when (error) {
            is CopilotRequestException.InvalidInput -> "$MSG_COPILOT_NOT_FOUND:${error.rawInput}"
            is CopilotRequestException.NotFound -> "$MSG_COPILOT_NOT_FOUND:${error.id}"
            is CopilotRequestException.Network -> buildNetworkErrorMessage(error.detail)
            is CopilotRequestException.JsonError -> MSG_COPILOT_JSON_ERROR
            else -> "$MSG_COPILOT_NOT_FOUND:${input.trim()}"
        }
    }

    private fun mapCopilotSetRequestError(input: String, error: Throwable): String {
        return when (error) {
            is CopilotRequestException.InvalidInput -> "$MSG_COPILOT_SET_NOT_FOUND  ${error.rawInput}"
            is CopilotRequestException.NotFound -> "$MSG_COPILOT_SET_NOT_FOUND  ${error.id}"
            is CopilotRequestException.Network -> buildNetworkErrorMessage(error.detail)
            is CopilotRequestException.JsonError -> MSG_COPILOT_JSON_ERROR
            else -> "$MSG_COPILOT_SET_NOT_FOUND  ${input.trim()}"
        }
    }

    private fun buildNetworkErrorMessage(detail: String?): String {
        return if (detail.isNullOrBlank()) {
            MSG_NETWORK_SERVICE_ERROR
        } else {
            "$MSG_NETWORK_SERVICE_ERROR\n$detail"
        }
    }

    private fun buildSetStatusMessage(
        setName: String,
        setDescription: String,
        summary: String
    ): String {
        val lines = mutableListOf<String>()
        if (setName.isNotBlank()) {
            lines += setName
        }
        if (setDescription.isNotBlank()) {
            lines += setDescription
        }
        lines += summary
        return lines.joinToString("\n")
    }

    private fun applyLoadedCopilot(
        data: CopilotTaskData,
        json: String,
        filePath: String,
        copilotId: Int,
        fromWeb: Boolean
    ) {
        val previousTabIndex = _state.value.tabIndex
        val targetTabIndex = resolveLoadedTabIndex(data, previousTabIndex)
        val inferredType = inferTaskType(data)
        val inferredName = inferLoadedCopilotName(data)
        val videoUrl = extractVideoUrl(data.doc.details)
        val operatorSummary = copilotManager.getOperatorSummary(data)
        _state.update { current ->
            val base = applyTabConstraints(current, targetTabIndex)
            base.copy(
                currentCopilot = data,
                currentTaskType = inferredType,
                copilotId = copilotId,
                canLike = copilotId > 0,
                isDataFromWeb = fromWeb,
                currentJsonContent = json,
                currentFilePath = filePath,
                copilotTaskName = inferredName,
                isLoading = false,
                statusMessage = "作业加载成功: $inferredName",
                videoUrl = videoUrl,
                operatorSummary = operatorSummary,
            )
        }
        if (previousTabIndex != targetTabIndex) {
            persistConfig()
        }
    }

    private fun autoAddLoadedCopilotToListIfNeeded(
        data: CopilotTaskData,
        filePath: String,
        copilotId: Int,
        source: String
    ) {
        val snapshot = _state.value
        val tabIndex = snapshot.tabIndex
        if (!snapshot.useCopilotList || !supportsBattleList(tabIndex)) {
            return
        }

        val newItems = createListItemsForLoadedCopilot(
            data = data,
            filePath = filePath,
            copilotId = copilotId,
            tabIndex = tabIndex,
            source = source
        )
        if (newItems.isEmpty()) return

        val status = if (newItems.size == 1) {
            val item = newItems.first()
            buildAddToListStatus(name = item.name, isRaid = item.isRaid)
        } else {
            "已添加 ${newItems.size} 条作业到战斗列表"
        }
        _state.update {
            it.copy(taskList = it.taskList + newItems, statusMessage = status)
        }
        persistTaskList()
    }

    private fun createListItemsForLoadedCopilot(
        data: CopilotTaskData,
        filePath: String,
        copilotId: Int,
        tabIndex: Int,
        source: String
    ): List<CopilotListItem> {
        return if (tabIndex == TAB_PARADOX) {
            val name = inferParadoxName(data)
            if (name.isBlank()) {
                emptyList()
            } else {
                listOf(
                    CopilotListItem(
                        name = name,
                        filePath = filePath,
                        isRaid = false,
                        copilotId = copilotId,
                        tabIndex = tabIndex,
                        source = source
                    )
                )
            }
        } else {
            val stageName = data.stageName
            if (stageName.isBlank()) {
                emptyList()
            } else {
                val displayName = resolveStageNavigation(data).navigateName.ifBlank { stageName }
                val difficulty = if (data.difficulty == DifficultyFlags.NONE) {
                    DifficultyFlags.NORMAL
                } else {
                    data.difficulty
                }
                val items = mutableListOf<CopilotListItem>()
                if ((difficulty and DifficultyFlags.NORMAL) != 0) {
                    items.add(
                        CopilotListItem(
                            name = displayName,
                            filePath = filePath,
                            isRaid = false,
                            copilotId = copilotId,
                            tabIndex = tabIndex,
                            source = source
                        )
                    )
                }
                if ((difficulty and DifficultyFlags.RAID) != 0) {
                    items.add(
                        CopilotListItem(
                            name = displayName,
                            filePath = filePath,
                            isRaid = true,
                            copilotId = copilotId,
                            tabIndex = tabIndex,
                            source = source
                        )
                    )
                }
                if (items.isEmpty()) {
                    items.add(
                        CopilotListItem(
                            name = displayName,
                            filePath = filePath,
                            isRaid = false,
                            copilotId = copilotId,
                            tabIndex = tabIndex,
                            source = source
                        )
                    )
                }
                items
            }
        }
    }

    private fun applyTabConstraints(state: CopilotUiState, tabIndex: Int): CopilotUiState {
        val listAllowed = supportsBattleList(tabIndex)
        val regularCopilotOptionsAllowed = supportsRegularCopilotOptions(tabIndex)
        val newConfig = if (regularCopilotOptionsAllowed) {
            state.config
        } else {
            state.config.copy(
                formation = false,
                useFormation = false,
                useSupportUnit = false,
                addTrust = false,
                ignoreRequirements = false,
                addUserAdditional = false,
                userAdditional = ""
            )
        }
        return state.copy(
            tabIndex = tabIndex,
            useCopilotList = if (listAllowed) state.useCopilotList else false,
            config = newConfig
        )
    }

    private fun findStageName(vararg names: String): String {
        val candidates = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (candidates.isEmpty()) return ""

        val directName = candidates.firstOrNull { DIRECT_STAGE_NAME_REGEX.matches(it.lowercase()) }
        if (!directName.isNullOrBlank()) {
            return directName
        }

        return candidates.firstNotNullOfOrNull { STAGE_NAME_REGEX.find(it)?.value } ?: ""
    }

    private fun resolveStageNavigation(
        data: CopilotTaskData,
        preferredNavigateName: String = ""
    ): ResolvedStageNavigation {
        val mapInfo = resourceDataManager.findMap(data.stageName)
        val stageCode = mapInfo?.code?.takeIf { it.isNotBlank() }
        val fallbackNavigateName = findStageName(data.stageName, data.doc.title).ifBlank {
            data.stageName
        }
        val navigateName = preferredNavigateName.trim().ifBlank {
            stageCode ?: fallbackNavigateName
        }
        return ResolvedStageNavigation(
            stageCode = stageCode,
            stageId = mapInfo?.stageId?.takeIf { it.isNotBlank() },
            navigateName = navigateName,
            hasMapMatch = mapInfo != null
        )
    }

    private fun isSssCopilot(data: CopilotTaskData): Boolean {
        return data.type.equals("SSS", ignoreCase = true)
    }

    private fun isParadoxCopilot(data: CopilotTaskData): Boolean {
        val stageId = resolveStageNavigation(data).stageId
        return stageId?.startsWith("mem_", ignoreCase = true) == true
    }

    private fun resolveLoadedTabIndex(data: CopilotTaskData, currentTabIndex: Int): Int {
        return when {
            isSssCopilot(data) -> TAB_SSS
            isParadoxCopilot(data) -> TAB_PARADOX
            else -> currentTabIndex
        }
    }

    private fun inferTaskType(data: CopilotTaskData): MaaTaskType {
        return when {
            isSssCopilot(data) -> MaaTaskType.SSS_COPILOT
            isParadoxCopilot(data) -> MaaTaskType.PARADOX_COPILOT
            else -> MaaTaskType.COPILOT
        }
    }

    private fun inferLoadedCopilotName(data: CopilotTaskData): String {
        return if (isParadoxCopilot(data)) {
            inferParadoxName(data)
        } else {
            resolveStageNavigation(data).navigateName.ifBlank { data.stageName }
        }
    }

    private fun inferParadoxName(data: CopilotTaskData): String {
        val navigation = resolveStageNavigation(data)
        val localizedNameFromStageId = extractParadoxCodeName(navigation.stageId)
            ?.let(resourceDataManager::getCharacterByCodeName)
            ?.let(resourceDataManager::getLocalizedCharacterName)
        val candidates = listOf(
            localizedNameFromStageId,
            navigation.stageCode,
            data.opers.firstOrNull()?.name,
            data.stageName
        )
        return candidates.firstOrNull { !it.isNullOrBlank() } ?: "未知干员"
    }

    private fun extractParadoxCodeName(stageId: String?): String? {
        if (stageId.isNullOrBlank() || !stageId.startsWith("mem_", ignoreCase = true)) {
            return null
        }
        val endIndex = stageId.length - 2
        if (endIndex <= 4) {
            return null
        }
        return stageId.substring(4, endIndex).takeIf { it.isNotBlank() }
    }

    private fun buildAddToListStatus(
        name: String,
        isRaid: Boolean,
        hasNavigateNameOverride: Boolean = false
    ): String {
        return buildString {
            append("已添加: ")
            append(name)
            if (isRaid) {
                append(" (突袭)")
            }
            if (hasNavigateNameOverride) {
                append("\n")
                append(MSG_NAVIGATION_NAME_MISMATCH)
            }
        }
    }

    fun onTaskNameChanged(name: String) {
        _state.update { it.copy(copilotTaskName = name.trim()) }
    }

    fun onConfigChanged(config: CopilotConfig) {
        _state.update { it.copy(config = config) }
        persistConfig()
    }

    fun onToggleListMode(enabled: Boolean) {
        val tab = _state.value.tabIndex
        if (enabled && !supportsBattleList(tab)) {
            _state.update { it.copy(statusMessage = "当前页签不支持战斗列表") }
            return
        }
        _state.update {
            it.copy(
                useCopilotList = enabled,
                config = if (enabled) it.config.copy(formation = true) else it.config
            )
        }
        persistConfig()
    }

    fun onAddToList(isRaid: Boolean = false) {
        if (!_state.value.useCopilotList) {
            _state.update { it.copy(statusMessage = "请先启用战斗列表") }
            return
        }
        val current = _state.value.currentCopilot
        val filePath = _state.value.currentFilePath
        if (current == null || filePath.isBlank()) {
            _state.update { it.copy(statusMessage = MSG_COPILOT_EMPTY) }
            return
        }
        val tabIndex = _state.value.tabIndex
        if (!supportsBattleList(tabIndex)) {
            _state.update { it.copy(statusMessage = "${getCopilotTabName(tabIndex)}不支持战斗列表") }
            return
        }
        val manualTaskName = _state.value.copilotTaskName
        val navigation = resolveStageNavigation(current, manualTaskName)
        val name = if (tabIndex == TAB_PARADOX) {
            manualTaskName.ifBlank { inferParadoxName(current) }
        } else {
            navigation.navigateName
        }
        if (name.isBlank()) {
            _state.update { it.copy(statusMessage = "关卡名无效，无法导航") }
            return
        }
        if (tabIndex != TAB_PARADOX && navigation.hasNavigateNameOverride) {
            Timber.w(
                "$TAG: $MSG_NAVIGATION_NAME_MISMATCH, stageCode=%s, navigateName=%s",
                navigation.stageCode,
                navigation.navigateName
            )
        }
        val item = CopilotListItem(
            name = name,
            filePath = filePath,
            isRaid = if (tabIndex == TAB_PARADOX) false else isRaid,
            copilotId = _state.value.copilotId,
            tabIndex = tabIndex,
            source = if (_state.value.isDataFromWeb) "web" else "local"
        )
        _state.update {
            it.copy(
                taskList = it.taskList + item,
                statusMessage = buildAddToListStatus(
                    name = name,
                    isRaid = item.isRaid,
                    hasNavigateNameOverride = tabIndex != TAB_PARADOX && navigation.hasNavigateNameOverride
                )
            )
        }
        persistTaskList()
    }

    fun onSelectListItem(index: Int, disableListMode: Boolean = false) {
        val item = _state.value.taskList.getOrNull(index) ?: return
        viewModelScope.launch {
            val result = copilotManager.parseFromFile(item.filePath)
            result.fold(
                onSuccess = { (data, json) ->
                    val previousTabIndex = _state.value.tabIndex
                    val targetTabIndex =
                        item.tabIndex ?: resolveLoadedTabIndex(data, previousTabIndex)
                    _state.update { current ->
                        val base = applyTabConstraints(current, targetTabIndex)
                        base.copy(
                            currentCopilot = data,
                            currentTaskType = inferTaskType(data),
                            copilotId = item.copilotId,
                            canLike = item.copilotId > 0,
                            isDataFromWeb = item.source == "web",
                            currentJsonContent = json,
                            currentFilePath = item.filePath,
                            copilotTaskName = item.name.ifBlank { inferLoadedCopilotName(data) },
                            useCopilotList = if (disableListMode) false else base.useCopilotList,
                            statusMessage = "已选中列表作业: ${item.name}"
                        )
                    }
                    if (previousTabIndex != targetTabIndex) {
                        persistConfig()
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(statusMessage = "读取文件失败：${e.message}") }
                }
            )
        }
    }

    fun onRemoveFromList(index: Int) {
        _state.update {
            it.copy(taskList = it.taskList.filterIndexed { i, _ -> i != index })
        }
        persistTaskList()
    }

    fun onClearList() {
        _state.update { it.copy(taskList = emptyList()) }
        persistTaskList()
    }

    fun onCleanUnchecked() {
        _state.update {
            it.copy(taskList = it.taskList.filter { item -> item.isChecked })
        }
        persistTaskList()
    }

    fun onToggleListItem(index: Int) {
        _state.update {
            it.copy(
                taskList = it.taskList.mapIndexed { i, item ->
                    if (i == index) item.copy(isChecked = !item.isChecked) else item
                }
            )
        }
        persistTaskList()
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
        persistTaskList()
    }

    fun onStart() {
        viewModelScope.launch {
            val snapshot = _state.value
            if (!validateStart(snapshot)) return@launch

            val config = buildEffectiveConfig(snapshot)
            val task = if (snapshot.useCopilotList) {
                val checked = snapshot.taskList.filter { it.isChecked }
                pendingCopilotIds.clear()
                pendingCopilotIds.addAll(checked.map { it.copilotId }.filter { it > 0 })
                copilotManager.buildListTask(snapshot.tabIndex, checked, config)
            } else {
                val type = resolveSingleTaskType(snapshot)
                copilotManager.buildSingleTask(type, snapshot.currentFilePath, config)
            }

            runtimeStateStore.resetRequirementIgnored()
            _state.update { it.copy(statusMessage = "正在启动...") }
            when (val result = compositionService.startCopilot(task)) {
                is MaaCompositionService.StartResult.Success -> {
                    _state.update { it.copy(statusMessage = "自动战斗已启动") }
                }

                is MaaCompositionService.StartResult.ResourceError -> {
                    _state.update { it.copy(statusMessage = "资源加载失败，请重新初始化资源") }
                }

                is MaaCompositionService.StartResult.InitializationError -> {
                    _state.update { it.copy(statusMessage = "初始化失败: ${result.phase}") }
                }

                is MaaCompositionService.StartResult.ConnectionError -> {
                    _state.update { it.copy(statusMessage = "连接失败: ${result.phase}") }
                }

                is MaaCompositionService.StartResult.StartError -> {
                    _state.update { it.copy(statusMessage = "读取文件失败！") }
                }

                is MaaCompositionService.StartResult.PortraitOrientationError -> {
                    _state.update { it.copy(statusMessage = "当前为竖屏,无法在前台模式运行") }
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

    fun onRate(isLike: Boolean) {
        val id = _state.value.copilotId
        if (id <= 0) return
        _state.update { it.copy(canLike = false) }
        launchRateCopilot(id = id, isLike = isLike, updateStatusMessage = true)
    }

    private suspend fun validateStart(snapshot: CopilotUiState): Boolean {
        if (snapshot.useCopilotList) {
            return validateTaskListStrict(snapshot.tabIndex, snapshot.taskList)
        }

        if (snapshot.currentCopilot == null || snapshot.currentFilePath.isBlank()) {
            _state.update { it.copy(statusMessage = MSG_COPILOT_EMPTY) }
            return false
        }

        val taskType = resolveSingleTaskType(snapshot)
        if ((taskType == MaaTaskType.SSS_COPILOT && snapshot.tabIndex != TAB_SSS) ||
            (taskType != MaaTaskType.SSS_COPILOT && snapshot.tabIndex == TAB_SSS)
        ) {
            _state.update { it.copy(statusMessage = MSG_TYPE_MISMATCH) }
            return false
        }

        return true
    }

    private suspend fun validateTaskListStrict(
        tabIndex: Int,
        taskList: List<CopilotListItem>
    ): Boolean {
        val selected = taskList.filter { it.isChecked }
        if (selected.isEmpty()) {
            _state.update { it.copy(statusMessage = MSG_EMPTY_LIST) }
            return false
        }

        if (selected.any { it.tabIndex == null }) {
            _state.update { it.copy(statusMessage = MSG_LEGACY_LIST) }
            return false
        }

        val tabs = selected.mapNotNull { it.tabIndex }.distinct()
        if (tabs.size > 1) {
            _state.update { it.copy(statusMessage = MSG_MIXED_LIST) }
            return false
        }

        val listTab = tabs.firstOrNull() ?: tabIndex
        if (listTab != tabIndex) {
            _state.update {
                it.copy(
                    statusMessage = "正在使用 ｢战斗列表｣，当前页签为「${getCopilotTabName(tabIndex)}」，但列表来自「${
                        getCopilotTabName(
                            listTab
                        )
                    }」，请切换到对应页签后再启动"
                )
            }
            return false
        }

        if (tabIndex == TAB_PARADOX) {
            return verifyParadoxTasks(selected)
        }
        return verifyCopilotListTask(selected)
    }

    private suspend fun verifyCopilotListTask(items: List<CopilotListItem>): Boolean {
        when (items.size) {
            0 -> {
                _state.update { it.copy(statusMessage = MSG_EMPTY_LIST) }
                return false
            }

            1 -> {
                _state.update { it.copy(statusMessage = MSG_SINGLE_LIST_WARN) }
            }
        }

        if (items.any { it.name.trim().isEmpty() }) {
            _state.update { it.copy(statusMessage = MSG_TASK_NAME_EMPTY) }
            return false
        }

        val uniquePaths = items.map { it.filePath }.toSet()
        for (path in uniquePaths) {
            val parsed = copilotManager.parseFromFile(path)
            if (parsed.isFailure) {
                _state.update { it.copy(statusMessage = "未找到对应作业！$path") }
                return false
            }
            val stageName = parsed.getOrThrow().first.stageName
            if (stageName.isBlank() || resourceDataManager.findMap(stageName) == null) {
                // TODO: 自动触发资源更新 (参考 WPF: UpdateResource -> 重新验证)
                //  可调用 UpdateViewModel.checkResourceUpdate() 后重新执行验证
                _state.update {
                    it.copy(
                        statusMessage = "不支持的关卡 ${
                            resourceDataManager.findMap(
                                stageName
                            )?.code ?: stageName
                        }，请尝试更新资源"
                    )
                }
                return false
            }
        }
        return true
    }

    private fun verifyParadoxTasks(items: List<CopilotListItem>): Boolean {
        val operatorNames = resourceDataManager.operators.value.values.map { it.name }.toSet()
        for (item in items) {
            val normalizedName =
                resourceDataManager.getLocalizedCharacterName(item.name, "zh-cn")
                    ?: item.name
            if (normalizedName !in operatorNames) {
                _state.update { it.copy(statusMessage = "错误的干员: ${item.name}") }
                return false
            }
        }
        return true
    }

    private fun supportsBattleList(tabIndex: Int): Boolean {
        return tabIndex == TAB_MAIN || tabIndex == TAB_PARADOX
    }

    private fun supportsRegularCopilotOptions(tabIndex: Int): Boolean {
        return tabIndex == TAB_MAIN || tabIndex == TAB_OTHER_ACTIVITY
    }

    private fun supportsCopilotSetImport(tabIndex: Int): Boolean {
        return tabIndex == TAB_MAIN || tabIndex == TAB_PARADOX
    }

    private fun resolveSingleTaskType(snapshot: CopilotUiState): MaaTaskType {
        if (snapshot.tabIndex == TAB_PARADOX) {
            return MaaTaskType.PARADOX_COPILOT
        }
        if (snapshot.currentTaskType == MaaTaskType.SSS_COPILOT || snapshot.tabIndex == TAB_SSS) {
            return MaaTaskType.SSS_COPILOT
        }
        return MaaTaskType.COPILOT
    }

    private fun buildEffectiveConfig(snapshot: CopilotUiState): CopilotConfig {
        var config = snapshot.config
        val regularCopilotOptionsAllowed = supportsRegularCopilotOptions(snapshot.tabIndex)
        if (!regularCopilotOptionsAllowed) {
            config = config.copy(
                formation = false,
                useFormation = false,
                useSupportUnit = false,
                addTrust = false,
                ignoreRequirements = false,
                addUserAdditional = false,
                userAdditional = ""
            )
        }
        if (!(snapshot.useCopilotList && snapshot.tabIndex == TAB_MAIN)) {
            config = config.copy(useSanityPotion = false)
        }
        return config
    }

    private suspend fun onCopilotTaskSuccess() {
        val current = _state.value
        if (!current.useCopilotList) return

        val index = current.taskList.indexOfFirst { it.isChecked }
        if (index !in current.taskList.indices) return

        val completed = current.taskList[index]
        val updated = current.taskList.toMutableList().also {
            it[index] = completed.copy(isChecked = false)
        }
        _state.update {
            it.copy(taskList = updated, statusMessage = "已完成: ${completed.name}")
        }
        repository.saveTaskList(updated)

        val id = completed.copilotId
        if (id <= 0 || id in recentlyRatedCopilotIds) return
        val removed = pendingCopilotIds.remove(id)
        val noMoreSameId = id !in pendingCopilotIds
        if (removed && noMoreSameId && !runtimeStateStore.hasRequirementIgnored.value) {
            launchRateCopilot(id = id, isLike = true, updateStatusMessage = true)
        }
    }

    private fun launchRateCopilot(id: Int, isLike: Boolean, updateStatusMessage: Boolean) {
        if (id <= 0 || id in recentlyRatedCopilotIds || id in ratingInFlightCopilotIds) return
        ratingInFlightCopilotIds.add(id)

        viewModelScope.launch {
            try {
                val success = copilotManager.rateCopilot(id, isLike)
                if (success) {
                    recentlyRatedCopilotIds.add(id)
                    if (updateStatusMessage) {
                        _state.update { it.copy(statusMessage = MSG_RATE_SUCCESS) }
                    }
                } else if (updateStatusMessage) {
                    _state.update { it.copy(statusMessage = MSG_RATE_FAILED) }
                }
            } finally {
                ratingInFlightCopilotIds.remove(id)
            }
        }
    }


    private fun getCopilotTabName(tabIndex: Int): String {
        return when (tabIndex) {
            TAB_MAIN -> "主线/故事集/SideStory"
            TAB_SSS -> "保全派驻"
            TAB_PARADOX -> "悖论模拟"
            TAB_OTHER_ACTIVITY -> "其他活动"
            else -> tabIndex.toString()
        }
    }

    private fun extractVideoUrl(details: String): String {
        if (details.isBlank()) return ""
        val match = Regex("[aAbB][vV]\\d+").find(details) ?: return ""
        return "https://www.bilibili.com/video/${match.value}"
    }

    private fun persistTaskList() {
        val list = _state.value.taskList
        viewModelScope.launch {
            repository.saveTaskList(list)
        }
    }

    private fun persistConfig() {
        val config = _state.value.config
        viewModelScope.launch {
            repository.saveConfig(config)
        }
    }
}
