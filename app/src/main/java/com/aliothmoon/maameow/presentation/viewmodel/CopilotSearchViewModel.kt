package com.aliothmoon.maameow.presentation.viewmodel

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotPage
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSummary
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.domain.service.OperatorSummaryData
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.Dispatchers
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
    val stageLabel: String,
    val uploader: String,
    val views: Int,
    val likes: Int,
    val summary: OperatorSummaryData,
)

data class StageSuggestion(val code: String, val name: String, val stageId: String)

data class CopilotSearchUiState(
    val keyword: String = "",
    val stageText: String = "",
    /** 选中联想项后才有；为空时按 stageText 现解析 */
    val stageId: String = "",
    val order: CopilotSearchOrder = CopilotSearchOrder.HOT,
    val suggestions: List<StageSuggestion> = emptyList(),
    val items: List<CopilotSearchItem> = emptyList(),
    val page: Int = 0,
    val total: Int = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: UiText = UiText.Empty,
)

class CopilotSearchViewModel(
    private val copilotManager: CopilotManager,
    private val resourceDataManager: ResourceDataManager,
) : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 10
        const val SUGGESTION_LIMIT = 8
        const val CACHE_TTL_MS = 60_000L
        const val CACHE_MAX = 30

        /** overview 里突袭变体的 stageId 带这个后缀，作业站只认原 id */
        const val RAID_VARIANT_SUFFIX = "#f#"
    }

    private val _state = MutableStateFlow(CopilotSearchUiState())
    val state: StateFlow<CopilotSearchUiState> = _state.asStateFlow()

    // 作业站是社区服务，同一查询短时间内不重复请求
    private val cache = HashMap<Map<String, String>, Pair<Long, PrtsCopilotPage>>()

    fun onOpened() {
        val s = _state.value
        if (!s.searched && !s.loading) search(page = 1)
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
        search(page = 1)
    }

    fun onOrderChange(order: CopilotSearchOrder) {
        if (order == _state.value.order) return
        _state.update { it.copy(order = order) }
        search(page = 1)
    }

    fun onSearch() = search(page = 1)

    fun onLoadMore() {
        val s = _state.value
        if (s.hasNext) search(page = s.page + 1)
    }

    private fun search(page: Int) {
        val s = _state.value
        if (s.loading) return
        val query = buildQuery(s, page)
        _state.update { it.copy(loading = true, error = UiText.Empty, suggestions = emptyList()) }
        viewModelScope.launch {
            val result = cachedPage(query)?.let { Result.success(it) }
                ?: copilotManager.searchCopilots(query).onSuccess { remember(query, it) }
            result.fold(
                onSuccess = { data ->
                    val items = withContext(Dispatchers.Default) { data.data.mapNotNull(::toItem) }
                    _state.update {
                        val merged = if (page == 1) items else (it.items + items).distinctBy { item -> item.id }
                        it.copy(
                            items = merged,
                            page = page,
                            total = data.total,
                            hasNext = data.hasNext,
                            loading = false,
                            searched = true,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            searched = true,
                            error = uiTextOf(R.string.copilot_search_failed, e.message.orEmpty()),
                        )
                    }
                }
            )
        }
    }

    private fun buildQuery(s: CopilotSearchUiState, page: Int): Map<String, String> = buildMap {
        put("page", page.toString())
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
        val stageLabel = resourceDataManager.findMap(data.stageName)?.code
            ?.takeIf { it.isNotBlank() } ?: data.stageName
        return CopilotSearchItem(
            id = summary.id,
            title = data.doc.title,
            details = data.doc.details,
            stageLabel = stageLabel,
            uploader = summary.uploader,
            views = summary.views,
            likes = summary.like,
            summary = copilotManager.getOperatorSummary(data),
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
