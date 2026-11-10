package com.aliothmoon.maameow.presentation.viewmodel

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.copilot.CopilotGroup
import com.aliothmoon.maameow.data.model.copilot.CopilotOperator
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotPage
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSummary
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CopilotCode
import com.aliothmoon.maameow.domain.service.CopilotCodeType
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.domain.service.CopilotOperatorMatching
import com.aliothmoon.maameow.domain.service.OperatorMatch
import com.aliothmoon.maameow.domain.service.OperatorMatchMode
import com.aliothmoon.maameow.domain.service.CopilotRequestException
import com.aliothmoon.maameow.domain.service.CopilotSetInfo
import com.aliothmoon.maameow.domain.service.OperatorSummaryData
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextDynamic
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CopilotSearchOrder(val apiValue: String, @param:StringRes val labelRes: Int) {
    HOT("hot", R.string.copilot_search_order_hot),
    LATEST("upload_time", R.string.copilot_search_order_latest),
    VIEWS("views", R.string.copilot_search_order_views),
}

data class CopilotSearchItem(
    val id: Int,
    val title: String,
    val details: String,
    val stageLabel: UiText,
    val uploader: String,
    val views: Int,
    val likes: Int,
    val summary: OperatorSummaryData,
    val opers: List<CopilotOperator>,
    val groups: List<CopilotGroup>,
    /** 没有干员数据时为空 */
    val match: OperatorMatch? = null,
)

data class OperatorMatchFilter(
    /** 有干员数据才显示筛选 */
    val available: Boolean = false,
    /** 干员数据不含技能与模组 */
    val partial: Boolean = false,
    val enabled: Boolean = true,
    val modes: Set<OperatorMatchMode> = CopilotOperatorMatching.DEFAULT_MODES,
) {
    val active: Boolean get() = available && enabled

    fun accepts(match: OperatorMatch?): Boolean = match == null || match.mode in modes
}

data class StageSuggestion(val code: String, val name: String, val stageId: String)

enum class CopilotSearchTab { COPILOT, SET }

data class SearchResults<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    /** 首页重查中，旧结果仍在 */
    val refreshing: Boolean = false,
    val searched: Boolean = false,
    val error: UiText = UiText.Empty,
    /** 连续几页被筛光，停自动翻页 */
    val paused: Boolean = false,
) {
    val canLoadMore: Boolean get() = hasNext && !loading && !paused && error == UiText.Empty

    fun started(page: Int) = copy(loading = true, refreshing = page == 1, paused = false, error = UiText.Empty)

    fun loaded(new: List<T>, page: Int, hasNext: Boolean, key: (T) -> Int) = copy(
        items = if (page == 1) new else (items + new).distinctBy(key),
        page = page,
        hasNext = hasNext,
        loading = false,
        refreshing = false,
        searched = true,
    )

    /** 首页失败时旧结果属于上一次查询，不能接着翻 */
    fun failed(page: Int, error: UiText) = copy(
        hasNext = hasNext && page > 1,
        loading = false,
        refreshing = false,
        searched = true,
        error = error,
    )
}

data class CopilotSetSearchUiState(
    val keyword: String = "",
    val results: SearchResults<CopilotSetInfo> = SearchResults(),
    val expanded: Set<Int> = emptySet(),
    val contents: Map<Int, List<CopilotSearchItem>> = emptyMap(),
    val contentLoading: Set<Int> = emptySet(),
    val contentErrors: Map<Int, UiText> = emptyMap(),
)

data class CopilotSearchUiState(
    val tab: CopilotSearchTab = CopilotSearchTab.COPILOT,
    val keyword: String = "",
    val stageText: String = "",
    /** 选中联想项后才有；为空时按 stageText 现解析 */
    val stageId: String = "",
    val order: CopilotSearchOrder = CopilotSearchOrder.HOT,
    val suggestions: List<StageSuggestion> = emptyList(),
    val results: SearchResults<CopilotSearchItem> = SearchResults(),
    val matchFilter: OperatorMatchFilter = OperatorMatchFilter(),
    /** 神秘代码查的是指定作业，不按干员筛 */
    val byCode: Boolean = false,
) {
    val filtering: Boolean get() = matchFilter.active && !byCode

    val shownItems: List<CopilotSearchItem>
        get() = if (filtering) results.items.filter { matchFilter.accepts(it.match) } else results.items
}

class CopilotSearchViewModel(
    private val copilotManager: CopilotManager,
    private val resourceDataManager: ResourceDataManager,
    private val matching: CopilotOperatorMatching,
) : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 10
        const val SET_PAGE_SIZE = 20
        const val SUGGESTION_LIMIT = 8
        const val CACHE_TTL_MS = 60_000L
        const val CACHE_MAX = 30
        const val ID_QUERY_KEY = "copilot_ids"

        /** 免得把作业站翻穿 */
        const val MAX_EMPTY_PAGES = 5

        /** overview 里突袭变体的 stageId 带这个后缀，作业站只认原 id */
        const val RAID_VARIANT_SUFFIX = "#f#"
    }

    private val _state = MutableStateFlow(CopilotSearchUiState())
    val state: StateFlow<CopilotSearchUiState> = _state.asStateFlow()

    private val _setState = MutableStateFlow(CopilotSetSearchUiState())
    val setState: StateFlow<CopilotSetSearchUiState> = _setState.asStateFlow()

    // 作业站是社区服务，同一查询短时间内不重复请求
    private val cache = HashMap<Map<String, String>, Pair<Long, PrtsCopilotPage>>()

    private var searchJob: Job? = null
    private var setSearchJob: Job? = null

    // 翻页沿用提交时的条件，不读还没提交的输入框
    private var committedQuery: Map<String, String> = emptyMap()
    private var committedSetKeyword = ""

    private var matcher: CopilotOperatorMatching.Matcher? = null
    private var emptyPageStreak = 0

    init {
        viewModelScope.launch {
            matching.settings.collect { settings ->
                _state.update {
                    it.copy(matchFilter = it.matchFilter.copy(enabled = settings.enabled, modes = settings.modes))
                }
                resumeAutoLoad()
            }
        }
        viewModelScope.launch {
            matching.matcher.collect { m ->
                matcher = m
                _state.update {
                    it.copy(
                        matchFilter = it.matchFilter.copy(available = m != null, partial = m?.partial == true),
                        results = it.results.copy(items = it.results.items.map(::withMatch)),
                    )
                }
                _setState.update { s -> s.copy(contents = s.contents.mapValues { (_, items) -> items.map(::withMatch) }) }
                resumeAutoLoad()
            }
        }
    }

    fun onOpened() = searchTabIfFresh(_state.value.tab)

    fun onTabChange(tab: CopilotSearchTab) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab, suggestions = emptyList()) }
        searchTabIfFresh(tab)
    }

    private fun searchTabIfFresh(tab: CopilotSearchTab) {
        when (tab) {
            CopilotSearchTab.COPILOT -> _state.value.results.let { if (!it.searched && !it.loading) onSearch() }
            CopilotSearchTab.SET -> _setState.value.results.let { if (!it.searched && !it.loading) onSetSearch() }
        }
    }

    fun onKeywordChange(text: String) {
        _state.update { it.copy(keyword = text) }
    }

    fun onStageTextChange(text: String) {
        _state.update { it.copy(stageText = text, stageId = "", suggestions = matchStages(text)) }
    }

    fun onPickStage(suggestion: StageSuggestion) {
        _state.update {
            it.copy(stageText = suggestion.code, stageId = suggestion.stageId, suggestions = emptyList())
        }
        onSearch()
    }

    fun onOrderChange(order: CopilotSearchOrder) {
        if (order == _state.value.order) return
        _state.update { it.copy(order = order) }
        onSearch()
    }

    fun onSearch() {
        val s = _state.value
        val code = shortCodeOf(s.keyword)
        if (code?.type == CopilotCodeType.COPILOT_SET) {
            _setState.update { it.copy(keyword = s.keyword.trim()) }
            _state.update { it.copy(tab = CopilotSearchTab.SET) }
            onSetSearch()
            return
        }
        // 神秘代码只按 id 查，其余条件都不带，与作业站一致
        committedQuery = code?.let { mapOf("limit" to PAGE_SIZE.toString(), ID_QUERY_KEY to it.id.toString()) }
            ?: buildQuery(s)
        _state.update { it.copy(byCode = code != null) }
        emptyPageStreak = 0
        fetchPage(page = 1)
    }

    fun onLoadMore() {
        val results = _state.value.results
        if (!results.hasNext || results.loading) return
        // 暂停后只有手动点「加载更多」会走到这里
        if (results.paused) emptyPageStreak = 0
        fetchPage(results.page + 1)
    }

    fun onToggleMatch(enabled: Boolean) {
        _state.update { it.copy(matchFilter = it.matchFilter.copy(enabled = enabled)) }
        resumeAutoLoad()
        viewModelScope.launch { matching.setEnabled(enabled) }
    }

    /** 至少留一档，全不选等于清空列表 */
    fun onToggleMatchMode(mode: OperatorMatchMode) {
        val modes = _state.value.matchFilter.modes
        val next = if (mode in modes) modes - mode else modes + mode
        if (next.isEmpty()) return
        _state.update { it.copy(matchFilter = it.matchFilter.copy(modes = next)) }
        resumeAutoLoad()
        viewModelScope.launch { matching.setModes(next) }
    }

    private fun resumeAutoLoad() {
        emptyPageStreak = 0
        _state.update { it.copy(results = it.results.copy(paused = false)) }
    }

    private fun withMatch(item: CopilotSearchItem): CopilotSearchItem =
        item.copy(match = matcher?.match(item.opers, item.groups))

    private fun fetchPage(page: Int) {
        val query = committedQuery + ("page" to page.toString())
        searchJob?.cancel()
        _state.update { it.copy(results = it.results.started(page), suggestions = emptyList()) }
        searchJob = viewModelScope.launch {
            val result = cachedPage(query)?.let { Result.success(it) }
                ?: copilotManager.searchCopilots(query).onSuccess { remember(query, it) }
            result.fold(
                onSuccess = { data ->
                    val items = withContext(Dispatchers.Default) { data.data.mapNotNull(::toItem) }.map(::withMatch)
                    // 按 id 查时后端按条数估 has_next，会一直翻页
                    val hasNext = data.hasNext && ID_QUERY_KEY !in query
                    val s = _state.value
                    val gained = if (s.filtering) items.count { s.matchFilter.accepts(it.match) } else items.size
                    emptyPageStreak = if (gained > 0) 0 else emptyPageStreak + 1
                    val paused = hasNext && emptyPageStreak >= MAX_EMPTY_PAGES
                    _state.update {
                        it.copy(results = it.results.loaded(items, page, hasNext, CopilotSearchItem::id).copy(paused = paused))
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(results = it.results.failed(page, failedText(e))) }
                }
            )
        }
    }

    fun onSetKeywordChange(text: String) {
        _setState.update { it.copy(keyword = text) }
    }

    fun onSetSearch() {
        val keyword = _setState.value.keyword.trim()
        val code = shortCodeOf(keyword)
        when {
            code == null -> {
                committedSetKeyword = keyword
                fetchSetPage(page = 1)
            }

            // 旧 maa:// 不分类型，在作业集页签按作业集认
            code.type == CopilotCodeType.COPILOT_SET || code.ambiguous -> fetchSetById(code.id)

            else -> {
                _state.update { it.copy(keyword = keyword, tab = CopilotSearchTab.COPILOT) }
                onSearch()
            }
        }
    }

    fun onSetLoadMore() {
        val results = _setState.value.results
        if (results.hasNext && !results.loading) fetchSetPage(results.page + 1)
    }

    private fun fetchSetPage(page: Int) {
        val keyword = committedSetKeyword
        setSearchJob?.cancel()
        _setState.update { it.copy(results = it.results.started(page)) }
        setSearchJob = viewModelScope.launch {
            copilotManager.searchCopilotSets(page, SET_PAGE_SIZE, keyword).fold(
                onSuccess = { data ->
                    val items = data.data.filter { it.id > 0 }.map {
                        CopilotSetInfo(it.id, it.name, it.description, it.copilotIds, it.creator)
                    }
                    _setState.update {
                        it.copy(
                            results = it.results.loaded(items, page, data.hasNext, CopilotSetInfo::id),
                            expanded = if (page == 1) emptySet() else it.expanded,
                        )
                    }
                },
                onFailure = { e ->
                    _setState.update { it.copy(results = it.results.failed(page, failedText(e))) }
                }
            )
        }
    }

    private fun fetchSetById(id: Int) {
        setSearchJob?.cancel()
        _setState.update { it.copy(results = it.results.started(1)) }
        setSearchJob = viewModelScope.launch {
            val result = copilotManager.getCopilotSetInfo("s$id")
            val error = result.exceptionOrNull()
            _setState.update {
                it.copy(
                    results = it.results
                        .loaded(listOfNotNull(result.getOrNull()), 1, false, CopilotSetInfo::id)
                        .copy(
                            error = if (error == null || error is CopilotRequestException.NotFound) {
                                UiText.Empty
                            } else {
                                failedText(error)
                            }
                        ),
                    expanded = emptySet(),
                )
            }
        }
    }

    /** 只认带协议头的神秘代码；纯数字仍按关键词搜，与作业站一致 */
    private fun shortCodeOf(keyword: String): CopilotCode? =
        keyword.trim().takeIf { "://" in it }?.let(copilotManager::parseCopilotCode)

    private fun failedText(e: Throwable): UiText =
        uiTextOf(R.string.copilot_search_failed, e.message.orEmpty())

    fun onToggleSet(set: CopilotSetInfo) {
        val s = _setState.value
        if (set.id in s.expanded) {
            _setState.update { it.copy(expanded = it.expanded - set.id) }
            return
        }
        _setState.update { it.copy(expanded = it.expanded + set.id) }
        if (set.id in s.contents || set.id in s.contentLoading) return
        _setState.update {
            it.copy(contentLoading = it.contentLoading + set.id, contentErrors = it.contentErrors - set.id)
        }
        viewModelScope.launch {
            val result = copilotManager.getCopilotSummaries(set.copilotIds)
                .map { list -> withContext(Dispatchers.Default) { list.mapNotNull(::toItem) }.map(::withMatch) }
            _setState.update {
                result.fold(
                    onSuccess = { items ->
                        it.copy(contentLoading = it.contentLoading - set.id, contents = it.contents + (set.id to items))
                    },
                    onFailure = { e ->
                        it.copy(
                            contentLoading = it.contentLoading - set.id,
                            contentErrors = it.contentErrors + (set.id to failedText(e)),
                        )
                    }
                )
            }
        }
    }

    private fun buildQuery(s: CopilotSearchUiState): Map<String, String> = buildMap {
        put("limit", PAGE_SIZE.toString())
        put("orderBy", s.order.apiValue)
        put("desc", "true")
        s.keyword.trim().takeIf { it.isNotEmpty() }?.let { put("document", it) }
        // level_keyword 只按内部 id 子串匹配，传 1-7 这种编号等于不过滤
        s.stageId.ifBlank { resolveStageId(s.stageText) }
            .takeIf { it.isNotEmpty() }
            ?.let { put("level_keyword", it) }
    }

    private fun resolveStageId(text: String): String {
        val q = text.trim()
        if (q.isEmpty()) return ""
        return resourceDataManager.mapData.value
            .firstOrNull { it.code.equals(q, ignoreCase = true) }
            ?.stageId?.removeSuffix(RAID_VARIANT_SUFFIX)
            ?: q
    }

    private fun matchStages(text: String): List<StageSuggestion> {
        val q = text.trim()
        if (q.isEmpty()) return emptyList()
        return resourceDataManager.mapData.value.asSequence()
            .filter {
                it.code?.startsWith(q, ignoreCase = true) == true ||
                        it.name?.contains(q, ignoreCase = true) == true ||
                        it.stageId?.contains(q, ignoreCase = true) == true
            }
            .mapNotNull { map ->
                val stageId = map.stageId?.removeSuffix(RAID_VARIANT_SUFFIX)
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                StageSuggestion(map.code.orEmpty(), map.name.orEmpty(), stageId)
            }
            .distinctBy { it.stageId }
            .sortedBy { !it.code.equals(q, ignoreCase = true) }
            .take(SUGGESTION_LIMIT)
            .toList()
    }

    private fun toItem(summary: PrtsCopilotSummary): CopilotSearchItem? {
        val data = copilotManager.parseJson(summary.content).getOrNull() ?: return null
        val paradox = data.stageName.startsWith("mem_", ignoreCase = true)
        return CopilotSearchItem(
            id = summary.id,
            // 悖论模拟只有一名干员，没标题时拿它顶上
            title = data.doc.title.ifBlank { if (paradox) data.opers.firstOrNull()?.name.orEmpty() else "" },
            details = data.doc.details,
            // 悖论关卡表里 code 就是内部 id
            stageLabel = if (paradox) {
                uiTextOf(R.string.panel_autobattle_tab_paradox)
            } else {
                uiTextDynamic(resourceDataManager.findMap(data.stageName)?.code?.takeIf { it.isNotBlank() } ?: data.stageName)
            },
            uploader = summary.uploader,
            views = summary.views,
            likes = summary.like,
            summary = copilotManager.getOperatorSummary(data),
            opers = data.opers,
            groups = data.groups,
        )
    }

    private fun cachedPage(query: Map<String, String>): PrtsCopilotPage? {
        val (at, page) = cache[query] ?: return null
        return page.takeIf { SystemClock.elapsedRealtime() - at < CACHE_TTL_MS }
    }

    private fun remember(query: Map<String, String>, page: PrtsCopilotPage) {
        if (cache.size >= CACHE_MAX) cache.clear()
        cache[query] = SystemClock.elapsedRealtime() to page
    }
}
