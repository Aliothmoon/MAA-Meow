package com.aliothmoon.maameow.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.model.copilot.CopilotTaskData
import com.aliothmoon.maameow.data.model.copilot.DifficultyFlags
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.CopilotRepository
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.AppAliveChecker
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.domain.service.CopilotRequestException
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.OperatorSummaryData
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.remote.AppAliveStatus
import com.aliothmoon.maameow.maa.callback.CopilotRuntimeStateStore
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
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
private val SIDE_STORY_STAGE_ID_REGEX = Regex("""^(act\d+(side|mini)|a00\d+)_""")
private val MAIN_STAGE_ID_REGEX = Regex("""^(main|sub|tough|hard)_""")

private enum class CopilotType(val tabIndex: Int) {
    UNKNOWN(-1),
    MAIN_AND_SIDE_STORY(TAB_MAIN),
    SSS(TAB_SSS),
    PARADOX(TAB_PARADOX),
    OTHER(TAB_OTHER_ACTIVITY),
}

private fun getCopilotTypeFromStageId(stageId: String?): CopilotType {
    if (stageId.isNullOrBlank()) return CopilotType.UNKNOWN
    return when {
        stageId.startsWith("mem_", ignoreCase = true) -> CopilotType.PARADOX
        stageId.startsWith("lt_", ignoreCase = true) -> CopilotType.SSS
        MAIN_STAGE_ID_REGEX.containsMatchIn(stageId) -> CopilotType.MAIN_AND_SIDE_STORY
        SIDE_STORY_STAGE_ID_REGEX.containsMatchIn(stageId) -> CopilotType.MAIN_AND_SIDE_STORY
        else -> CopilotType.UNKNOWN
    }
}

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
    val operatorSummary: OperatorSummaryData? = null,
)

class CopilotViewModel(
    private val app: Application,
    private val copilotManager: CopilotManager,
    private val compositionService: MaaCompositionService,
    private val repository: CopilotRepository,
    private val resourceDataManager: ResourceDataManager,
    private val runtimeStateStore: CopilotRuntimeStateStore,
    private val appAliveChecker: AppAliveChecker,
    private val chainState: TaskChainState,
) : ViewModel() {

    companion object {
        private const val TAG = "CopilotViewModel"
    }

    private val _state = MutableStateFlow(CopilotUiState())
    val state: StateFlow<CopilotUiState> = _state.asStateFlow()

    private val _dialog = MutableStateFlow<PanelDialogUiState?>(null)
    val dialog: StateFlow<PanelDialogUiState?> = _dialog.asStateFlow()

    val maaState: StateFlow<MaaExecutionState> = compositionService.state

    private var gameNotRunningAcknowledged = false
    private val pendingCopilotIds = mutableListOf<Int>()
    private val recentlyRatedCopilotIds = mutableSetOf<Int>()
    private val ratingInFlightCopilotIds = mutableSetOf<Int>()

    init {
        Timber.i("CopilotViewModel inited")
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
                    statusMessage = app.getString(R.string.copilot_parsing),
                    currentCopilot = null,
                    operatorSummary = null,
                    videoUrl = "",
                )
            }
            if (forceSet || copilotManager.isSetId(input)) {
                val tabIndex = _state.value.tabIndex
                if (!supportsCopilotSetImport(tabIndex)) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = app.getString(R.string.copilot_tab_no_set_import, getCopilotTabName(tabIndex))
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
                            app.getString(R.string.copilot_local_file_unsupported)
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
                _state.update { it.copy(statusMessage = app.getString(R.string.copilot_importing_set, ids.size)) }

                if (ids.isEmpty()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = buildSetStatusMessage(
                                setName = setInfo.name,
                                setDescription = setInfo.description,
                                summary = app.getString(R.string.copilot_set_empty)
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
                        append(app.getString(R.string.copilot_imported_with_failures, newItems.size, ids.size, failedCount))
                        if (!firstFailureReason.isNullOrBlank()) {
                            append("\n")
                            append(firstFailureReason)
                        }
                    }
                } else {
                    app.getString(R.string.copilot_imported, newItems.size, ids.size)
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
            is CopilotRequestException.InvalidInput -> app.getString(R.string.copilot_not_found_id, error.rawInput)
            is CopilotRequestException.NotFound -> app.getString(R.string.copilot_not_found_id, error.id)
            is CopilotRequestException.Network -> buildNetworkErrorMessage(error.detail)
            is CopilotRequestException.JsonError -> app.getString(R.string.copilot_json_error)
            else -> app.getString(R.string.copilot_not_found_id, input.trim())
        }
    }

    private fun mapCopilotSetRequestError(input: String, error: Throwable): String {
        return when (error) {
            is CopilotRequestException.InvalidInput -> app.getString(R.string.copilot_set_not_found_id, error.rawInput)
            is CopilotRequestException.NotFound -> app.getString(R.string.copilot_set_not_found_id, error.id)
            is CopilotRequestException.Network -> buildNetworkErrorMessage(error.detail)
            is CopilotRequestException.JsonError -> app.getString(R.string.copilot_json_error)
            else -> app.getString(R.string.copilot_set_not_found_id, input.trim())
        }
    }

    private fun buildNetworkErrorMessage(detail: String?): String {
        val error = app.getString(R.string.copilot_network_error)
        return if (detail.isNullOrBlank()) {
            error
        } else {
            "$error\n$detail"
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
                statusMessage = app.getString(R.string.copilot_loaded, inferredName),
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
            source = source
        )
        if (newItems.isEmpty()) return

        val status = if (newItems.size == 1) {
            val item = newItems.first()
            buildAddToListStatus(name = item.name, isRaid = item.isRaid)
        } else {
            app.getString(R.string.copilot_added_to_list, newItems.size)
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
        source: String
    ): List<CopilotListItem> {
        val isParadox = getCopilotType(data) == CopilotType.PARADOX
        return if (isParadox) {
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

    private fun getCopilotType(data: CopilotTaskData): CopilotType {
        if (data.type.equals("SSS", ignoreCase = true)) return CopilotType.SSS
        val stageId = resolveStageNavigation(data).stageId
        return getCopilotTypeFromStageId(stageId)
    }

    private fun resolveLoadedTabIndex(data: CopilotTaskData, currentTabIndex: Int): Int {
        val type = getCopilotType(data)
        return if (type != CopilotType.UNKNOWN) type.tabIndex else currentTabIndex
    }

    private fun inferTaskType(data: CopilotTaskData): MaaTaskType {
        return when (getCopilotType(data)) {
            CopilotType.SSS -> MaaTaskType.SSS_COPILOT
            CopilotType.PARADOX -> MaaTaskType.PARADOX_COPILOT
            else -> MaaTaskType.COPILOT
        }
    }

    private fun inferLoadedCopilotName(data: CopilotTaskData): String {
        return if (getCopilotType(data) == CopilotType.PARADOX) {
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
        return candidates.firstOrNull { !it.isNullOrBlank() } ?: app.getString(R.string.copilot_unknown_operator)
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
            append(app.getString(R.string.copilot_added_prefix))
            append(name)
            if (isRaid) {
                append(app.getString(R.string.copilot_added_raid_suffix))
            }
            if (hasNavigateNameOverride) {
                append("\n")
                append(app.getString(R.string.copilot_nav_name_mismatch))
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
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_tab_no_list, getCopilotTabName(tab))) }
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
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_enable_list_first)) }
            return
        }
        val current = _state.value.currentCopilot
        val filePath = _state.value.currentFilePath
        if (current == null || filePath.isBlank()) {
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_empty)) }
            return
        }
        val tabIndex = _state.value.tabIndex
        if (!supportsBattleList(tabIndex)) {
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_tab_no_list, getCopilotTabName(tabIndex))) }
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
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_invalid_stage)) }
            return
        }
        if (tabIndex != TAB_PARADOX && navigation.hasNavigateNameOverride) {
            Timber.w(
                "$TAG: " + app.getString(R.string.copilot_nav_name_mismatch) + ", stageCode=%s, navigateName=%s",
                navigation.stageCode,
                navigation.navigateName
            )
        }
        val item = CopilotListItem(
            name = name,
            filePath = filePath,
            isRaid = if (tabIndex == TAB_PARADOX) false else isRaid,
            copilotId = _state.value.copilotId,
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
                        resolveLoadedTabIndex(data, previousTabIndex)
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
                            statusMessage = app.getString(R.string.copilot_selected_item, item.name)
                        )
                    }
                    if (previousTabIndex != targetTabIndex) {
                        persistConfig()
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(statusMessage = app.getString(R.string.copilot_file_read_failed, e.message)) }
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

    fun onDialogConfirm() {
        when (_dialog.value?.confirmAction) {
            PanelDialogConfirmAction.CONFIRM_PENDING_START -> {
                _dialog.value = null
                gameNotRunningAcknowledged = true
                onStart()
            }
            else -> {
                _dialog.value = null
            }
        }
    }

    fun onDialogDismiss() {
        _dialog.value = null
    }

    fun onStart() {
        viewModelScope.launch {
            val snapshot = _state.value
            if (!validateStart(snapshot)) return@launch

            if (!gameNotRunningAcknowledged) {
                val pkg = Packages[chainState.getClientType()]
                if (pkg != null && appAliveChecker.isAppAlive(pkg) == AppAliveStatus.DEAD) {
                    _dialog.value = PanelDialogUiState(
                        type = PanelDialogType.WARNING,
                        title = app.getString(R.string.copilot_start_warning_title),
                        message = app.gameNotRunningWarning(),
                        confirmText = app.getString(R.string.copilot_start_anyway),
                        dismissText = app.getString(R.string.cancel),
                        confirmAction = PanelDialogConfirmAction.CONFIRM_PENDING_START,
                    )
                    return@launch
                }
            }
            gameNotRunningAcknowledged = false

            val config = buildEffectiveConfig(snapshot)
            val tasks = if (snapshot.useCopilotList) {
                val checked = snapshot.taskList.filter { it.isChecked }
                pendingCopilotIds.clear()
                pendingCopilotIds.addAll(checked.map { it.copilotId }.filter { it > 0 })
                copilotManager.buildListTask(snapshot.tabIndex, checked, config)
            } else {
                val type = resolveSingleTaskType(snapshot)
                listOf(copilotManager.buildSingleTask(type, snapshot.currentFilePath, config))
            }

            runtimeStateStore.resetRequirementIgnored()
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_starting)) }
            when (val result = compositionService.startCopilot(tasks)) {
                is MaaCompositionService.StartResult.Success -> {
                    _state.update { it.copy(statusMessage = app.getString(R.string.copilot_started)) }
                }

                is MaaCompositionService.StartResult.ResourceError -> {
                    _state.update { it.copy(statusMessage = app.getString(R.string.copilot_resource_load_failed)) }
                }

                is MaaCompositionService.StartResult.InitializationError -> {
                    _state.update { it.copy(statusMessage = app.getString(R.string.copilot_init_failed, result.phase)) }
                }

                is MaaCompositionService.StartResult.ConnectionError -> {
                    _state.update { it.copy(statusMessage = app.getString(R.string.copilot_connect_failed, result.phase)) }
                }

                is MaaCompositionService.StartResult.StartError -> {
                    _state.update { it.copy(statusMessage = app.getString(R.string.copilot_file_read_error)) }
                }

                is MaaCompositionService.StartResult.PortraitOrientationError -> {
                    _state.update { it.copy(statusMessage = app.getString(R.string.copilot_portrait_mode_error)) }
                }
            }
        }
    }

    fun onStop() {
        viewModelScope.launch {
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_stopping)) }
            compositionService.stop()
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_stopped)) }
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
            return validateTaskListStrict(snapshot.taskList)
        }

        if (snapshot.currentCopilot == null || snapshot.currentFilePath.isBlank()) {
            _dialog.value = PanelDialogUiState(
                type = PanelDialogType.WARNING,
                title = app.getString(R.string.hint),
                message = app.getString(R.string.copilot_empty_task_msg),
                confirmText = app.getString(R.string.got_it),
                confirmAction = PanelDialogConfirmAction.DISMISS_ONLY,
            )
            return false
        }

        val taskType = resolveSingleTaskType(snapshot)
        if ((taskType == MaaTaskType.SSS_COPILOT && snapshot.tabIndex != TAB_SSS) ||
            (taskType != MaaTaskType.SSS_COPILOT && snapshot.tabIndex == TAB_SSS)
        ) {
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_type_mismatch)) }
            return false
        }

        return true
    }

    private suspend fun validateTaskListStrict(
        taskList: List<CopilotListItem>
    ): Boolean {
        val selected = taskList.filter { it.isChecked }
        if (selected.isEmpty()) {
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_empty_list)) }
            return false
        }

        // 基于文件内容检测作业类型，而非 tabIndex
        val types = mutableSetOf<CopilotType>()
        for (item in selected) {
            val parsed = copilotManager.parseFromFile(item.filePath)
            if (parsed.isFailure) {
                Timber.w("validateTaskListStrict: 无法解析文件 %s", item.filePath)
                continue
            }
            val type = getCopilotType(parsed.getOrThrow().first)
            if (type != CopilotType.UNKNOWN) {
                types.add(type)
            }
        }

        if (types.size > 1) {
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_mixed_list)) }
            return false
        }

        val detectedType = types.firstOrNull()
        if (detectedType == CopilotType.PARADOX) {
            return verifyParadoxTasks(selected)
        }
        if (detectedType == CopilotType.MAIN_AND_SIDE_STORY || detectedType == null) {
            return verifyCopilotListTask(selected)
        }
        // SSS 等类型跳过进一步校验
        return true
    }

    private suspend fun verifyCopilotListTask(items: List<CopilotListItem>): Boolean {
        when (items.size) {
            0 -> {
                _state.update { it.copy(statusMessage = app.getString(R.string.copilot_empty_list)) }
                return false
            }

            1 -> {
                _state.update { it.copy(statusMessage = app.getString(R.string.copilot_single_from_list)) }
            }
        }

        if (items.any { it.name.trim().isEmpty() }) {
            _state.update { it.copy(statusMessage = app.getString(R.string.copilot_task_name_empty)) }
            return false
        }

        val uniquePaths = items.map { it.filePath }.toSet()
        for (path in uniquePaths) {
            val parsed = copilotManager.parseFromFile(path)
            if (parsed.isFailure) {
                _state.update { it.copy(statusMessage = app.getString(R.string.copilot_not_found_path, path)) }
                return false
            }
            val stageName = parsed.getOrThrow().first.stageName
            if (stageName.isBlank() || resourceDataManager.findMap(stageName) == null) {
                // TODO: 自动触发资源更新 (参考 WPF: UpdateResource -> 重新验证)
                //  可调用 UpdateViewModel.checkResourceUpdate() 后重新执行验证
                _state.update {
                    it.copy(
                        statusMessage = app.getString(
                            R.string.copilot_unsupported_stage,
                            resourceDataManager.findMap(stageName)?.code ?: stageName
                        )
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
                _state.update { it.copy(statusMessage = app.getString(R.string.copilot_invalid_operator, item.name)) }
                return false
            }
        }
        return true
    }

    private fun supportsBattleList(tabIndex: Int): Boolean {
        return tabIndex == TAB_MAIN || tabIndex == TAB_PARADOX || tabIndex == TAB_SSS
    }

    private fun supportsRegularCopilotOptions(tabIndex: Int): Boolean {
        return tabIndex == TAB_MAIN || tabIndex == TAB_OTHER_ACTIVITY
    }

    private fun supportsCopilotSetImport(tabIndex: Int): Boolean {
        return tabIndex == TAB_MAIN || tabIndex == TAB_PARADOX || tabIndex == TAB_SSS
    }

    private fun supportsLoopCount(tabIndex: Int): Boolean {
        return tabIndex == TAB_SSS || tabIndex == TAB_OTHER_ACTIVITY
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
        if (!supportsLoopCount(snapshot.tabIndex)) {
            config = config.copy(loop = false, loopTimes = 1)
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
            it.copy(taskList = updated, statusMessage = app.getString(R.string.copilot_completed, completed.name))
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
                        _state.update { it.copy(statusMessage = app.getString(R.string.copilot_rate_success)) }
                    }
                } else if (updateStatusMessage) {
                    _state.update { it.copy(statusMessage = app.getString(R.string.copilot_rate_failed)) }
                }
            } finally {
                ratingInFlightCopilotIds.remove(id)
            }
        }
    }


    private fun getCopilotTabName(tabIndex: Int): String {
        return when (tabIndex) {
            TAB_MAIN -> app.getString(R.string.copilot_tab_main)
            TAB_SSS -> app.getString(R.string.copilot_tab_sss)
            TAB_PARADOX -> app.getString(R.string.copilot_tab_paradox)
            TAB_OTHER_ACTIVITY -> app.getString(R.string.copilot_tab_other)
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
