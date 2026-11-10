package com.aliothmoon.maameow.presentation.viewmodel

import android.os.SystemClock
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.CopilotApiService
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotPage
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotQueryResponse
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetData
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetPage
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetQueryResponse
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetResponse
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSetSummary
import com.aliothmoon.maameow.data.model.copilot.PrtsCopilotSummary
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.domain.service.CopilotOperatorMatching
import com.aliothmoon.maameow.domain.service.OperatorMatchMode
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CopilotSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = mockk<CopilotApiService>()
    private val resources = mockk<ResourceDataManager> { every { findMap(any()) } returns null }
    private val queries = mutableListOf<Map<String, String>>()
    private val responses = ArrayDeque<suspend () -> Result<PrtsCopilotQueryResponse>>()
    private val setKeywords = mutableListOf<String>()
    private val matcherFlow = MutableStateFlow<CopilotOperatorMatching.Matcher?>(null)
    private lateinit var vm: CopilotSearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
        coEvery { api.queryCopilots(any()) } coAnswers {
            queries += firstArg<Map<String, String>>()
            responses.removeFirst()()
        }
        val matching = mockk<CopilotOperatorMatching>(relaxed = true) {
            every { settings } returns MutableStateFlow(CopilotOperatorMatching.Settings())
            every { matcher } returns matcherFlow
        }
        vm = CopilotSearchViewModel(CopilotManager(api, mockk(relaxed = true)), resources, matching)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(SystemClock::class)
    }

    private fun page(
        vararg ids: Int,
        hasNext: Boolean = false,
        stage: String = "main_01-07",
        opers: List<String> = emptyList(),
    ) = Result.success(
        PrtsCopilotQueryResponse(
            statusCode = 200,
            data = PrtsCopilotPage(
                hasNext = hasNext,
                data = ids.map {
                    val operJson = opers.joinToString(",") { name -> """{"name":"$name"}""" }
                    PrtsCopilotSummary(
                        id = it,
                        content = """{"stage_name":"$stage","doc":{"title":"t$it"},"opers":[$operJson]}""",
                    )
                },
            ),
        )
    )

    private fun ownOnlyA() {
        val a = OperBoxOperator(id = "a", name = "a", rarity = 6, elite = 2, level = 90, potential = 1, own = true)
        matcherFlow.value = CopilotOperatorMatching.Matcher(mapOf("a" to a)) { it.name }
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun respond(results: List<Result<PrtsCopilotQueryResponse>>) {
        results.forEach { result -> responses += { result } }
    }

    // 解析摘要走真实的 Dispatchers.Default，只能轮询等它回来
    private fun settle() {
        repeat(400) {
            dispatcher.scheduler.advanceUntilIdle()
            if (!vm.state.value.results.loading && !vm.setState.value.results.loading) return
            Thread.sleep(5)
        }
        error("search did not settle")
    }

    private fun search(keyword: String) {
        vm.onKeywordChange(keyword)
        vm.onSearch()
        settle()
    }

    private fun ids() = vm.state.value.results.items.map { it.id }

    @Test
    fun newSearchReplacesInFlightOne() {
        val stuck = CompletableDeferred<Result<PrtsCopilotQueryResponse>>()
        responses += { stuck.await() }
        respond(listOf(page(123)))
        vm.onOpened()
        dispatcher.scheduler.advanceUntilIdle()

        search("prts://123")

        assertEquals("123", queries.last()["copilot_ids"])
        assertEquals(listOf(123), ids())
    }

    @Test
    fun orderChangeWhileLoadingIsNotDropped() {
        val stuck = CompletableDeferred<Result<PrtsCopilotQueryResponse>>()
        responses += { stuck.await() }
        respond(listOf(page(1)))
        vm.onOpened()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onOrderChange(CopilotSearchOrder.LATEST)
        settle()

        assertEquals("upload_time", queries.last()["orderBy"])
        assertEquals(listOf(1), ids())
    }

    @Test
    fun loadMoreUsesCommittedQueryNotUnsubmittedInput() {
        respond(listOf(page(1, hasNext = true), page(2)))
        search("A")

        vm.onKeywordChange("B")
        vm.onLoadMore()
        settle()

        assertEquals("A", queries.last()["document"])
        assertEquals("2", queries.last()["page"])
        assertEquals(listOf(1, 2), ids())
    }

    @Test
    fun firstPageFailureLeavesNoPagingEntry() {
        respond(listOf(page(1, hasNext = true), Result.failure(IOException("boom"))))
        search("A")

        search("B")

        val results = vm.state.value.results
        assertFalse(results.hasNext)
        assertNotEquals(UiText.Empty, results.error)
    }

    @Test
    fun loadMoreFailureKeepsRetryEntry() {
        respond(listOf(page(1, hasNext = true), Result.failure(IOException("boom")), page(2)))
        search("A")

        vm.onLoadMore()
        settle()
        assertTrue(vm.state.value.results.hasNext)
        assertFalse(vm.state.value.results.canLoadMore)

        vm.onLoadMore()
        settle()
        assertEquals("2", queries.last()["page"])
        assertEquals(listOf(1, 2), ids())
    }

    @Test
    fun shortCodeQueriesByIdOnlyAndNeverPages() {
        respond(listOf(page(123, hasNext = true)))

        search("prts://123")

        assertEquals("123", queries.single()["copilot_ids"])
        assertFalse("orderBy" in queries.single())
        assertFalse("document" in queries.single())
        assertFalse(vm.state.value.results.hasNext)
    }

    @Test
    fun plainDigitsStayKeyword() {
        respond(listOf(page(1)))

        search("2024")

        assertEquals("2024", queries.single()["document"])
    }

    @Test
    fun setCodeRedirectsAndLoadMoreDoesNotBounceBack() {
        coEvery { api.getCopilotSet(5) } returns Result.success(
            PrtsCopilotSetResponse(
                statusCode = 200,
                data = PrtsCopilotSetData(id = 5, name = "n", creator = "c", copilotIds = listOf(1)),
            )
        )
        respond(listOf(page(1, hasNext = true), page(2)))
        vm.onOpened()
        settle()

        search("prts://s5")
        assertEquals(CopilotSearchTab.SET, vm.state.value.tab)
        assertEquals(listOf(5), vm.setState.value.results.items.map { it.id })
        assertEquals("c", vm.setState.value.results.items.single().creator)

        vm.onTabChange(CopilotSearchTab.COPILOT)
        vm.onLoadMore()
        settle()
        assertEquals(CopilotSearchTab.COPILOT, vm.state.value.tab)
        assertEquals("2", queries.last()["page"])
        assertFalse("document" in queries.last())
    }

    @Test
    fun copilotCodeInSetTabRedirectsToCopilotTab() {
        respond(listOf(page(123)))
        vm.onSetKeywordChange("prts://123")

        vm.onSetSearch()
        settle()

        assertEquals(CopilotSearchTab.COPILOT, vm.state.value.tab)
        assertEquals(listOf(123), ids())
    }

    @Test
    fun missingSetIsEmptyNotError() {
        coEvery { api.getCopilotSet(9) } returns Result.success(PrtsCopilotSetResponse(statusCode = 400))
        vm.onSetKeywordChange("prts://s9")

        vm.onSetSearch()
        settle()

        val results = vm.setState.value.results
        assertTrue(results.searched)
        assertTrue(results.items.isEmpty())
        assertEquals(UiText.Empty, results.error)
    }

    @Test
    fun setLoadMoreUsesCommittedKeyword() {
        coEvery { api.querySets(any(), any(), any()) } coAnswers {
            setKeywords += thirdArg<String>()
            Result.success(
                PrtsCopilotSetQueryResponse(
                    statusCode = 200,
                    data = PrtsCopilotSetPage(
                        hasNext = firstArg<Int>() == 1,
                        data = listOf(PrtsCopilotSetSummary(id = firstArg(), name = "s")),
                    ),
                )
            )
        }
        vm.onSetKeywordChange("A")
        vm.onSetSearch()
        settle()

        vm.onSetKeywordChange("B")
        vm.onSetLoadMore()
        settle()

        assertEquals(listOf("A", "A"), setKeywords)
        assertEquals(listOf(1, 2), vm.setState.value.results.items.map { it.id })
    }

    @Test
    fun matchFilterHidesBlockedButNotShortCodeHits() {
        ownOnlyA()
        respond(listOf(page(1, opers = listOf("a")), page(2, opers = listOf("x", "y"))))

        search("A")
        assertEquals(listOf(1), vm.state.value.shownItems.map { it.id })
        search("B")
        assertEquals(listOf(2), ids())
        assertEquals(OperatorMatchMode.BLOCKED, vm.state.value.results.items.single().match?.mode)
        assertTrue(vm.state.value.shownItems.isEmpty())

        respond(listOf(page(2, opers = listOf("x", "y"))))
        search("prts://2")
        assertEquals(listOf(2), vm.state.value.shownItems.map { it.id })
    }

    @Test
    fun matchArrivingLaterTagsLoadedItems() {
        respond(listOf(page(1, opers = listOf("a"))))
        search("A")
        assertEquals(null, vm.state.value.results.items.single().match)

        ownOnlyA()

        assertEquals(OperatorMatchMode.READY, vm.state.value.results.items.single().match?.mode)
        assertTrue(vm.state.value.matchFilter.available)
    }

    @Test
    fun emptyFilteredPagesPauseAutoLoadUntilManualRequest() {
        ownOnlyA()
        respond((1..6).map { page(it, hasNext = true, opers = listOf("x", "y")) })

        search("A")
        repeat(4) {
            assertTrue(vm.state.value.results.canLoadMore)
            vm.onLoadMore()
            settle()
        }
        assertTrue(vm.state.value.results.paused)
        assertFalse(vm.state.value.results.canLoadMore)

        vm.onLoadMore()
        settle()
        assertEquals("6", queries.last()["page"])
        assertFalse(vm.state.value.results.paused)
    }

    @Test
    fun changingModesResumesAutoLoad() {
        ownOnlyA()
        respond((1..5).map { page(it, hasNext = true, opers = listOf("x", "y")) })
        search("A")
        repeat(4) { vm.onLoadMore(); settle() }
        assertTrue(vm.state.value.results.paused)

        vm.onToggleMatchMode(OperatorMatchMode.BLOCKED)

        assertFalse(vm.state.value.results.paused)
        assertEquals(5, vm.state.value.shownItems.size)
    }

    @Test
    fun paradoxStageShowsLocalizedLabel() {
        respond(listOf(page(1, stage = "mem_ardign_1")))

        search("A")

        assertEquals(uiTextOf(R.string.panel_autobattle_tab_paradox), vm.state.value.results.items.single().stageLabel)
    }
}
