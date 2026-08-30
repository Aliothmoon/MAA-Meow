package com.aliothmoon.maameow.presentation.viewmodel

import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.model.copilot.CopilotTaskData
import com.aliothmoon.maameow.data.model.copilot.DifficultyFlags
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.CopilotRepository
import com.aliothmoon.maameow.data.resource.CopilotResourceProvider
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CopilotCodeType
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.domain.service.CopilotRequestException
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.OperatorSummaryData
import com.aliothmoon.maameow.domain.service.copilot.CopilotRequirementCorrector
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.CheckGameReadinessUseCase
import com.aliothmoon.maameow.domain.usecase.GameReadiness
import com.aliothmoon.maameow.domain.usecase.TaskStartContext
import com.aliothmoon.maameow.domain.usecase.TaskStartMode
import com.aliothmoon.maameow.maa.callback.CopilotRuntimeStateStore
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.resolve
import com.aliothmoon.maameow.utils.i18n.uiTextDynamic
import com.aliothmoon.maameow.utils.i18n.uiTextJoin
import com.aliothmoon.maameow.utils.i18n.uiTextLines
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TAB_MAIN = CopilotTabs.MAIN
private const val TAB_SSS = CopilotTabs.SSS
private const val TAB_PARADOX = CopilotTabs.PARADOX
private const val TAB_OTHER_ACTIVITY = CopilotTabs.OTHER_ACTIVITY
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
    val taskList: List<CopilotListItem> = emptyList(),
    val hasRequirementIgnored: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: UiText = UiText.Empty,
    val videoUrl: String = "",
    val operatorSummary: OperatorSummaryData? = null,
    /** 干员需求自动校正的提示，随作业加载刷新 */
    val requirementWarnings: List<UiText> = emptyList(),
    val builtinPickerExpanded: Boolean = false,
    val builtinLoaded: Boolean = false,
    val builtinTree: List<CopilotResourceProvider.Node> = emptyList(),
    val builtinExpandedFolders: Set<String> = emptySet(),
) {
    /** 用户偏好，随 config 落盘；是否真正生效看 listModeActive */
    val useCopilotList: Boolean get() = config.useCopilotList

    /** 列表模式是否生效：偏好开启且当前页签支持。切到不支持的页签不改写偏好，切回即恢复 */
    val listModeActive: Boolean
        get() = useCopilotList && CopilotTabs.supportsBattleList(tabIndex)
}

class CopilotViewModel(
    private val appContext: Context,
    private val copilotManager: CopilotManager,
    private val compositionService: MaaCompositionService,
    private val repository: CopilotRepository,
    private val resourceDataManager: ResourceDataManager,
    private val copilotResourceProvider: CopilotResourceProvider,
    private val runtimeStateStore: CopilotRuntimeStateStore,
    private val checkGameReadiness: CheckGameReadinessUseCase,
    private val chainState: TaskChainState,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "CopilotViewModel"
    }

    private val _state = MutableStateFlow(CopilotUiState())
    val state: StateFlow<CopilotUiState> = _state.asStateFlow()

    private val _dialog = MutableStateFlow<PanelDialogUiState?>(null)
    val dialog: StateFlow<PanelDialogUiState?> = _dialog.asStateFlow()

    val maaState: StateFlow<MaaExecutionState> = compositionService.state

    private var pendingStartContext: TaskStartContext? = null

    /** 列表被忽略的提醒已确认，确认后重入 onStart 时跳过该检查 */
    private var listGuardAcknowledged = false
    private val pendingCopilotIds = mutableListOf<Int>()
    private val recentlyRatedCopilotIds = mutableSetOf<Int>()
    private val ratingInFlightCopilotIds = mutableSetOf<Int>()

    init {
        Timber.i("CopilotViewModel inited")
        restoreState()
        observeRuntimeState()
    }

    private fun string(@StringRes resId: Int, vararg args: Any): String =
        appContext.getString(resId, *args)

    private fun text(@StringRes resId: Int, vararg args: Any?): UiText =
        uiTextOf(resId, *args)

    private fun navigationNameMismatchMessage(): UiText =
        text(R.string.copilot_navigation_name_mismatch)

    private fun currentTabDisplayName(tabIndex: Int): UiText {
        return when (tabIndex) {
            TAB_MAIN -> text(R.string.copilot_tab_main_story_side_story)
            TAB_SSS -> text(R.string.panel_autobattle_tab_security)
            TAB_PARADOX -> text(R.string.panel_autobattle_tab_paradox)
            TAB_OTHER_ACTIVITY -> text(R.string.panel_autobattle_tab_other)
            else -> uiTextDynamic(tabIndex.toString())
        }
    }

    private fun formatCopilotNotFoundMessage(input: String): UiText =
        text(R.string.copilot_not_found, input)

    private fun formatCopilotSetNotFoundMessage(input: String): UiText =
        text(R.string.copilot_set_not_found, input)

    private fun formatStartStatus(result: MaaCompositionService.StartResult): UiText {
        return when (result) {
            is MaaCompositionService.StartResult.Success -> text(R.string.copilot_status_started)
            is MaaCompositionService.StartResult.StartError -> text(R.string.copilot_file_read_error)
            else -> appContext.resolveTaskStartFailureMessage(result)
                ?: text(R.string.copilot_status_started)
        }
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

    fun onPasteAndParse() {
        val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(appContext)
            ?.toString()
            .orEmpty()
            .trim()
        if (text.isBlank()) {
            _state.update { it.copy(statusMessage = text(R.string.copilot_clipboard_empty)) }
            return
        }
        onInputChanged(text)
        onParseInput()
    }

    fun onParseInput() {
        parseInput()
    }

    /**
     * 从本地文件导入作业
     * @param files 文件名与 JSON 内容的列表
     */
    fun onImportLocalFiles(files: List<Pair<String, String>>) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.startingParse() }
            var successCount = 0
            var lastData: CopilotTaskData? = null
            var lastFilePath = ""
            var lastJson = ""

            for ((fileName, json) in files) {
                val data = copilotManager.parseJson(json).getOrElse { e ->
                    Timber.w(e, "$TAG: 解析本地文件失败: $fileName")
                    continue
                }
                val fixed = correctRequirements(data, json, copilotId = 0)
                val filePath = repository.saveCopilotJsonByName(fileName, fixed.json)
                successCount++
                lastData = fixed.data
                lastFilePath = filePath
                lastJson = fixed.json

                if (files.size > 1 || _state.value.listModeActive) {
                    autoAddLoadedCopilotToListIfNeeded(
                        data = fixed.data,
                        filePath = filePath,
                        copilotId = 0,
                        source = "local"
                    )
                }
            }

            if (successCount == 0) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = text(R.string.copilot_import_failed)
                    )
                }
                return@launch
            }

            if (files.size == 1 && !_state.value.listModeActive) {
                applyLoadedCopilot(
                    data = lastData!!,
                    json = lastJson,
                    filePath = lastFilePath,
                    copilotId = 0,
                    fromWeb = false
                )
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = text(R.string.copilot_import_success, successCount)
                    )
                }
            }
        }
    }

    fun onToggleBuiltinPicker() {
        val expand = !_state.value.builtinPickerExpanded
        _state.update { it.copy(builtinPickerExpanded = expand) }
        if (expand && !_state.value.builtinLoaded) {
            loadBuiltinTree()
        }
    }

    private fun loadBuiltinTree() {
        viewModelScope.launch {
            val tree = copilotResourceProvider.loadTree()
            _state.update { it.copy(builtinTree = tree, builtinLoaded = true) }
        }
    }

    fun onToggleBuiltinFolder(relativePath: String) {
        _state.update {
            val folders = it.builtinExpandedFolders
            val next =
                if (relativePath in folders) folders - relativePath else folders + relativePath
            it.copy(builtinExpandedFolders = next)
        }
    }

    fun onSelectBuiltinFile(node: CopilotResourceProvider.Node) {
        val path = node.fullPath ?: return
        viewModelScope.launch {
            _state.update { it.startingParse() }
            copilotManager.parseFromFile(path).fold(
                onSuccess = { (data, json) ->
                    val fixed = correctRequirements(data, json, copilotId = 0)
                    // 内置资源目录只读，改过的作业另存一份到 copilot 目录
                    val filePath = if (fixed.json != json) {
                        repository.saveCopilotJsonByName(node.name, fixed.json)
                    } else {
                        path
                    }
                    applyLoadedCopilot(
                        data = fixed.data,
                        json = fixed.json,
                        filePath = filePath,
                        copilotId = 0,
                        fromWeb = false
                    )
                    autoAddLoadedCopilotToListIfNeeded(
                        data = fixed.data,
                        filePath = filePath,
                        copilotId = 0,
                        source = "resource"
                    )
                    _state.update { it.copy(builtinPickerExpanded = false) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLoading = false, statusMessage = fileReadErrorMessage(e))
                    }
                    Timber.e(e, "$TAG: failed to load builtin copilot: %s", path)
                }
            )
        }
    }

    /** 进入「开始解析」时对 UI 状态的统一重置。 */
    private fun CopilotUiState.startingParse(): CopilotUiState = copy(
        isLoading = true,
        statusMessage = text(R.string.copilot_status_parsing),
        currentCopilot = null,
        operatorSummary = null,
        videoUrl = "",
        requirementWarnings = emptyList(),
    )

    /** 文件读取失败时的状态消息：有明细则附带明细，否则用通用文案。 */
    private fun fileReadErrorMessage(e: Throwable): UiText {
        val detail = e.message.orEmpty().trim()
        return if (detail.isEmpty()) {
            text(R.string.copilot_file_read_error)
        } else {
            text(R.string.copilot_file_read_error_with_detail, detail)
        }
    }

    private fun parseInput() {
        val input = _state.value.inputText.trim()
        if (input.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.startingParse() }
            // 神秘代码自带类型信息（prts:// 单作业、prts://s / s 前缀作业集），
            // 旧格式（maa://、纯数字）无法区分，默认当单个作业（与 WPF 6.16 对齐）
            val code = copilotManager.parseCopilotCode(input)
            val asSet = code?.type == CopilotCodeType.COPILOT_SET
            if (asSet) {
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
                val fixed = correctRequirements(data, json, id)
                val filePath = repository.saveCopilotJson(id, fixed.json)
                applyLoadedCopilot(
                    data = fixed.data,
                    json = fixed.json,
                    filePath = filePath,
                    copilotId = fixed.copilotId,
                    fromWeb = true
                )
                autoAddLoadedCopilotToListIfNeeded(
                    data = fixed.data,
                    filePath = filePath,
                    copilotId = fixed.copilotId,
                    source = "web"
                )
            },
            onFailure = { remoteErr ->
                // 仅当输入连神秘代码都解析不出、且形似路径时才提示本地路径不支持，
                // 避免 maa:// / prts:// 代码的网络失败被误报
                val unsupportedLocalPath = remoteErr is CopilotRequestException.InvalidInput &&
                        (input.contains("\\") || input.contains("/"))
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = if (unsupportedLocalPath) {
                            text(R.string.copilot_local_path_unsupported)
                        } else {
                            mapSingleCopilotRequestError(input, remoteErr)
                        }
                    )
                }
                Timber.e(remoteErr, "$TAG: failed to parse Copilot")
            }
        )
    }

    private suspend fun importCopilotSet(input: String) {
        val result = copilotManager.getCopilotSetInfo(input)
        result.fold(
            onSuccess = { setInfo ->
                val ids = setInfo.copilotIds
                _state.update {
                    it.copy(statusMessage = text(R.string.copilot_status_importing_set, ids.size))
                }

                if (ids.isEmpty()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = buildSetStatusMessage(
                                setName = setInfo.name,
                                setDescription = setInfo.description,
                                summary = text(R.string.copilot_set_empty)
                            )
                        )
                    }
                    return@fold
                }

                var workingTabIndex = _state.value.tabIndex
                val newItems = mutableListOf<CopilotListItem>()
                var failedCount = 0
                var firstFailureReason: UiText? = null
                ids.forEach { id ->
                    val copilotResult = copilotManager.parseFromId(id.toString())
                    copilotResult.fold(
                        onSuccess = { (copilotId, data, json) ->
                            val fixed = correctRequirements(data, json, copilotId)
                            val filePath = repository.saveCopilotJson(copilotId, fixed.json)
                            val resolvedTabIndex =
                                resolveLoadedTabIndex(fixed.data, workingTabIndex)
                            newItems.addAll(
                                createListItemsForLoadedCopilot(
                                    data = fixed.data,
                                    filePath = filePath,
                                    copilotId = fixed.copilotId,
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
                    uiTextLines(
                        text(
                            R.string.copilot_status_imported_set_partial,
                            newItems.size,
                            ids.size,
                            failedCount
                        ),
                        firstFailureReason ?: UiText.Empty
                    )
                } else {
                    text(R.string.copilot_status_imported_set, newItems.size, ids.size)
                }
                _state.update { current ->
                    val base = applyTabConstraints(current, workingTabIndex)
                    val listModeEnabled = supportsBattleList(workingTabIndex)
                    base.copy(
                        taskList = base.taskList + newItems,
                        // 落到支持列表的页签就开启列表模式；不支持时保留用户偏好，不再清掉
                        config = if (listModeEnabled) {
                            base.config.copy(useCopilotList = true, formation = true)
                        } else {
                            base.config
                        },
                        isLoading = false,
                        statusMessage = buildSetStatusMessage(
                            setName = setInfo.name,
                            setDescription = setInfo.description,
                            summary = summary
                        )
                    )
                }
                persistConfig()
                persistTaskList()
            },
            onFailure = { e ->
                Timber.e(e, "$TAG: failed to import Copilot set")
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = mapCopilotSetRequestError(input, e)
                    )
                }
            }
        )
    }

    private fun mapSingleCopilotRequestError(input: String, error: Throwable): UiText {
        return when (error) {
            is CopilotRequestException.InvalidInput -> formatCopilotNotFoundMessage(error.rawInput)
            is CopilotRequestException.NotFound -> formatCopilotNotFoundMessage(error.id.toString())
            is CopilotRequestException.Network -> buildNetworkErrorMessage(error.detail)
            is CopilotRequestException.JsonError -> text(R.string.copilot_json_error)
            else -> formatCopilotNotFoundMessage(input.trim())
        }
    }

    private fun mapCopilotSetRequestError(input: String, error: Throwable): UiText {
        return when (error) {
            is CopilotRequestException.InvalidInput -> formatCopilotSetNotFoundMessage(error.rawInput)
            is CopilotRequestException.NotFound -> formatCopilotSetNotFoundMessage(error.id.toString())
            is CopilotRequestException.Network -> buildNetworkErrorMessage(error.detail)
            is CopilotRequestException.JsonError -> text(R.string.copilot_json_error)
            else -> formatCopilotSetNotFoundMessage(input.trim())
        }
    }

    private fun buildNetworkErrorMessage(detail: String?): UiText {
        return if (detail.isNullOrBlank()) {
            text(R.string.copilot_network_service_error)
        } else {
            uiTextLines(
                text(R.string.copilot_network_service_error),
                uiTextDynamic(detail)
            )
        }
    }

    private fun buildSetStatusMessage(
        setName: String,
        setDescription: String,
        summary: UiText
    ): UiText = uiTextLines(uiTextDynamic(setName), uiTextDynamic(setDescription), summary)

    /** 校正结果，[copilotId] 在作业被改动后清零 */
    private data class CorrectedCopilot(
        val data: CopilotTaskData,
        val json: String,
        val copilotId: Int,
    )

    /**
     * 落盘前统一做一次干员需求校正
     * 改动过的作业不再带原作业 id，免得把改后的跑法算到原作者头上
     */
    private fun correctRequirements(
        data: CopilotTaskData,
        json: String,
        copilotId: Int,
    ): CorrectedCopilot {
        val result = CopilotRequirementCorrector.correct(json) { name ->
            resourceDataManager.getCharacterByNameOrAlias(name)?.rarity ?: -1
        }
        if (result.corrections.isEmpty()) {
            return CorrectedCopilot(data, json, copilotId)
        }
        // 作业集会连着导入多份，逐条累加而不是覆盖；每次解析开始时由 startingParse 清空
        val messages = result.corrections.map(::describeCorrection)
        _state.update { it.copy(requirementWarnings = it.requirementWarnings + messages) }
        // 失败了内存和落盘会不一致，得留痕
        val reparsed = copilotManager.parseJson(result.json).fold(
            onSuccess = { it },
            onFailure = {
                Timber.w(it, "$TAG: 校正后重解析失败，沿用校正前的解析结果")
                data
            },
        )
        return CorrectedCopilot(
            data = reparsed,
            json = result.json,
            copilotId = if (result.altered) 0 else copilotId,
        )
    }

    private fun describeCorrection(correction: CopilotRequirementCorrector.Correction): UiText =
        when (correction.kind) {
            CopilotRequirementCorrector.Kind.UNSUPPORTED_SKILL ->
                text(R.string.copilot_unsupported_skill, correction.operatorName, correction.from)

            CopilotRequirementCorrector.Kind.ELITE_FILLED ->
                text(R.string.copilot_elite_filled, correction.operatorName, correction.to)

            CopilotRequirementCorrector.Kind.ELITE_RAISED ->
                text(
                    R.string.copilot_elite_raised,
                    correction.operatorName,
                    correction.from,
                    correction.to
                )
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
        val videoUrl = copilotManager.extractVideoUrl(data.doc.details)
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
                statusMessage = text(R.string.copilot_status_loaded, inferredName),
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
        if (!snapshot.listModeActive) {
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
            text(R.string.copilot_status_added_multi, newItems.size)
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
        return candidates.firstOrNull { !it.isNullOrBlank() }
            ?: string(R.string.copilot_unknown_operator)
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
    ): UiText {
        val addedLine = if (isRaid) {
            uiTextJoin(
                text(R.string.copilot_status_added, name),
                text(R.string.panel_autobattle_raid_suffix)
            )
        } else {
            text(R.string.copilot_status_added, name)
        }
        return if (hasNavigateNameOverride) {
            uiTextLines(addedLine, navigationNameMismatchMessage())
        } else {
            addedLine
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
            _state.update {
                it.copy(statusMessage = text(R.string.copilot_current_tab_battle_list_unsupported))
            }
            return
        }
        _state.update {
            it.copy(
                config = it.config.copy(
                    useCopilotList = enabled,
                    formation = enabled || it.config.formation
                )
            )
        }
        persistConfig()
    }

    fun onAddToList(isRaid: Boolean = false) {
        if (!_state.value.useCopilotList) {
            _state.update { it.copy(statusMessage = text(R.string.copilot_enable_battle_list_first)) }
            return
        }
        val current = _state.value.currentCopilot
        val filePath = _state.value.currentFilePath
        if (current == null || filePath.isBlank()) {
            _state.update { it.copy(statusMessage = text(R.string.copilot_empty_load_first)) }
            return
        }
        val tabIndex = _state.value.tabIndex
        if (!supportsBattleList(tabIndex)) {
            _state.update {
                it.copy(
                    statusMessage = text(
                        R.string.copilot_battle_list_unsupported,
                        currentTabDisplayName(tabIndex)
                    )
                )
            }
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
            _state.update {
                it.copy(statusMessage = text(R.string.copilot_invalid_stage_name_for_navigation))
            }
            return
        }
        if (tabIndex != TAB_PARADOX && navigation.hasNavigateNameOverride) {
            Timber.w(
                "$TAG: %s, stageCode=%s, navigateName=%s",
                navigationNameMismatchMessage().resolve(appContext),
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
                            config = if (disableListMode) {
                                base.config.copy(useCopilotList = false)
                            } else {
                                base.config
                            },
                            statusMessage = text(
                                R.string.copilot_status_selected_list_item,
                                item.name
                            )
                        )
                    }
                    if (previousTabIndex != targetTabIndex || disableListMode) {
                        persistConfig()
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(statusMessage = fileReadErrorMessage(e))
                    }
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

    /** 拖拽过程中按 id 移动；落盘交给 onReorderSettled，避免每次交换都写文件 */
    fun onReorderList(fromId: String, toId: String) {
        if (fromId == toId) return
        _state.update {
            val list = it.taskList.toMutableList()
            val from = list.indexOfFirst { item -> item.id == fromId }
            val to = list.indexOfFirst { item -> item.id == toId }
            if (from < 0 || to < 0) return@update it
            list.add(to, list.removeAt(from))
            it.copy(taskList = list)
        }
    }

    fun onReorderSettled() {
        persistTaskList()
    }

    fun onDialogConfirm() {
        when (_dialog.value?.confirmAction) {
            PanelDialogConfirmAction.CONFIRM_PENDING_START -> {
                val pending = pendingStartContext
                _dialog.value = null
                pendingStartContext = null
                if (pending != null) onStart(pending)
            }

            else -> {
                _dialog.value = null
            }
        }
    }

    fun onDialogDismiss() {
        _dialog.value = null
        clearPendingStart()
    }

    private fun clearPendingStart() {
        pendingStartContext = null
        listGuardAcknowledged = false
    }

    fun onStart() = onStart(TaskStartContext(TaskStartMode.MANUAL))

    private fun onStart(context: TaskStartContext) {
        viewModelScope.launch {
            val snapshot = _state.value
            if (!validateStart(snapshot)) return@launch

            // 反馈场景：列表模式被关掉（或从未开启）但列表里还有勾选项，用户以为会接着列表打
            if (!listGuardAcknowledged && shouldWarnListIgnored(snapshot)) {
                listGuardAcknowledged = true
                pendingStartContext = context
                _dialog.value = appContext.createStartWarningDialog(
                    text(
                        R.string.copilot_list_ignored_warning,
                        snapshot.taskList.count { it.isChecked }
                    )
                )
                return@launch
            }

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
                    clearPendingStart()
                    _dialog.value = appContext.createStartBlockedDialog(
                        appContext.resolveTaskStartBlockedMessage(readiness.reason)
                    )
                    return@launch
                }

                is GameReadiness.Ready -> clearPendingStart()
            }

            val config = buildEffectiveConfig(snapshot)
            val tasks = if (snapshot.listModeActive) {
                val checked = snapshot.taskList.filter { it.isChecked }
                pendingCopilotIds.clear()
                pendingCopilotIds.addAll(checked.map { it.copilotId }.filter { it > 0 })
                // 传完整列表: buildListTask 内部按全列表下标分配 id(与 onCopilotTaskSuccess 同坐标系)
                copilotManager.buildListTask(snapshot.tabIndex, snapshot.taskList, config)
            } else {
                val type = resolveSingleTaskType(snapshot)
                listOf(copilotManager.buildSingleTask(type, snapshot.currentFilePath, config))
            }

            runtimeStateStore.resetRequirementIgnored()
            runtimeStateStore.resetCurrentCopilotIndex()
            _state.update { it.copy(statusMessage = text(R.string.toolbox_status_starting)) }
            val result = compositionService.startCopilot(tasks)
            _state.update { it.copy(statusMessage = formatStartStatus(result)) }
        }
    }

    fun onStop() {
        viewModelScope.launch {
            _state.update { it.copy(statusMessage = text(R.string.toolbox_status_stopping)) }
            compositionService.stop()
            runtimeStateStore.resetCurrentCopilotIndex()
            _state.update { it.copy(statusMessage = text(R.string.toolbox_status_stopped)) }
        }
    }

    fun onRate(isLike: Boolean) {
        val id = _state.value.copilotId
        if (id <= 0) return
        _state.update { it.copy(canLike = false) }
        launchRateCopilot(id = id, isLike = isLike, updateStatusMessage = true)
    }

    private fun shouldWarnListIgnored(snapshot: CopilotUiState): Boolean {
        return !snapshot.listModeActive &&
                CopilotTabs.supportsBattleList(snapshot.tabIndex) &&
                snapshot.taskList.any { it.isChecked }
    }

    private suspend fun validateStart(snapshot: CopilotUiState): Boolean {
        if (snapshot.listModeActive) {
            return validateTaskListStrict(snapshot.taskList)
        }

        if (snapshot.currentCopilot == null || snapshot.currentFilePath.isBlank()) {
            _dialog.value = PanelDialogUiState(
                type = PanelDialogType.WARNING,
                title = text(R.string.task_start_dialog_info_title),
                message = text(R.string.copilot_empty_load_first),
                confirmText = text(R.string.common_got_it),
                confirmAction = PanelDialogConfirmAction.DISMISS_ONLY,
            )
            return false
        }

        val taskType = resolveSingleTaskType(snapshot)
        if ((taskType == MaaTaskType.SSS_COPILOT && snapshot.tabIndex != TAB_SSS) ||
            (taskType != MaaTaskType.SSS_COPILOT && snapshot.tabIndex == TAB_SSS)
        ) {
            _state.update { it.copy(statusMessage = text(R.string.copilot_type_mismatch)) }
            return false
        }

        return true
    }

    private suspend fun validateTaskListStrict(
        taskList: List<CopilotListItem>
    ): Boolean {
        val selected = taskList.filter { it.isChecked }
        if (selected.isEmpty()) {
            _state.update { it.copy(statusMessage = text(R.string.copilot_empty_list)) }
            return false
        }

        // 基于文件内容检测作业类型，而非 tabIndex
        val types = mutableSetOf<CopilotType>()
        for (item in selected) {
            val parsed = copilotManager.parseFromFile(item.filePath)
            if (parsed.isFailure) {
                Timber.w("validateTaskListStrict: failed to parse file %s", item.filePath)
                continue
            }
            val type = getCopilotType(parsed.getOrThrow().first)
            if (type != CopilotType.UNKNOWN) {
                types.add(type)
            }
        }

        if (types.size > 1) {
            val typeNames = uiTextJoin(
                *types.map { currentTabDisplayName(it.tabIndex) }.toTypedArray(),
                separator = uiTextDynamic(", ")
            )
            _state.update { it.copy(statusMessage = text(R.string.copilot_mixed_list, typeNames)) }
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
                _state.update { it.copy(statusMessage = text(R.string.copilot_empty_list)) }
                return false
            }

            1 -> {
                _state.update { it.copy(statusMessage = text(R.string.copilot_single_list_warning)) }
            }
        }

        if (items.any { it.name.trim().isEmpty() }) {
            _state.update { it.copy(statusMessage = text(R.string.copilot_task_name_empty)) }
            return false
        }

        val uniquePaths = items.map { it.filePath }.toSet()
        for (path in uniquePaths) {
            val parsed = copilotManager.parseFromFile(path)
            if (parsed.isFailure) {
                _state.update { it.copy(statusMessage = formatCopilotNotFoundMessage(path)) }
                return false
            }
            val stageName = parsed.getOrThrow().first.stageName
            if (stageName.isBlank() || resourceDataManager.findMap(stageName) == null) {
                // TODO: 自动触发资源更新 (参考 WPF: UpdateResource -> 重新验证)
                //  可调用 UpdateViewModel.checkResourceUpdate() 后重新执行验证
                _state.update {
                    it.copy(
                        statusMessage = text(
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
                _state.update {
                    it.copy(statusMessage = text(R.string.copilot_invalid_operator, item.name))
                }
                return false
            }
        }
        return true
    }

    private fun supportsBattleList(tabIndex: Int): Boolean =
        CopilotTabs.supportsBattleList(tabIndex)

    private fun supportsRegularCopilotOptions(tabIndex: Int): Boolean =
        CopilotTabs.supportsRegularCopilotOptions(tabIndex)

    private fun supportsLoopCount(tabIndex: Int): Boolean =
        CopilotTabs.supportsLoopCount(tabIndex)


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
        if (!(snapshot.listModeActive && snapshot.tabIndex == TAB_MAIN)) {
            config = config.copy(useSanityPotion = false)
        }
        return config
    }

    private suspend fun onCopilotTaskSuccess() {
        achievementRepository.report {
            event = AchievementEvents.COPILOT_SUCCESS
        }

        val current = _state.value
        if (!current.listModeActive) return

        // 上游 #16985: 优先用 core 回传的当前作业下标(跳过失败后下标可能不是第一个勾选项);
        // 回传缺失(-1)或越界/已取消时回退旧行为(第一个勾选项)。
        val tracked = runtimeStateStore.currentCopilotIndex.value
        val index =
            if (tracked in current.taskList.indices && current.taskList[tracked].isChecked) {
                tracked
            } else {
                current.taskList.indexOfFirst { it.isChecked }
            }
        if (index !in current.taskList.indices) return

        val completed = current.taskList[index]
        val updated = current.taskList.toMutableList().also {
            it[index] = completed.copy(isChecked = false)
        }
        _state.update {
            it.copy(
                taskList = updated,
                statusMessage = text(R.string.copilot_status_completed, completed.name)
            )
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
                    achievementRepository.report {
                        event = AchievementEvents.COPILOT_LIKED
                    }
                    if (updateStatusMessage) {
                        _state.update { it.copy(statusMessage = text(R.string.copilot_rate_success)) }
                    }
                } else if (updateStatusMessage) {
                    _state.update { it.copy(statusMessage = text(R.string.copilot_rate_failed)) }
                }
            } finally {
                ratingInFlightCopilotIds.remove(id)
            }
        }
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
