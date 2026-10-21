package com.aliothmoon.maameow.maa.callback

import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.model.toolbox.DepotItem
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.DepotSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 仓库识别结果落库：只收最终且非空的结果 */
class ToolboxResultCollectorDepotTest {

    private val depotRepository = mockk<DepotRepository>()
    private val collector = ToolboxResultCollector(
        resourceDataManager = mockk(relaxed = true),
        achievementRepository = mockk(relaxed = true),
        depotRepository = depotRepository,
        operBoxRepository = mockk(relaxed = true),
    )

    private fun result(done: Boolean, data: String) = JSONObject.of("done", done, "data", data)

    @Test
    fun finalResult_returnsStoredSnapshot() {
        val stored = DepotSnapshot(items = mapOf("30011" to 5), syncTimeMillis = 1L)
        every { depotRepository.set(any()) } returns stored

        assertEquals(stored, collector.onDepotResult(result(true, """{"30011":5,"30012":0}""")))
        verify { depotRepository.set(listOf(DepotItem("30011", 5))) }
    }

    @Test
    fun partialResult_isIgnored() {
        assertNull(collector.onDepotResult(result(false, """{"30011":5}""")))
        verify(exactly = 0) { depotRepository.set(any()) }
    }

    @Test
    fun emptyResult_keepsPreviousDepot() {
        assertNull(collector.onDepotResult(result(true, """{"30011":0}""")))
        verify(exactly = 0) { depotRepository.set(any()) }
    }

    @Test
    fun notWritten_returnsNull() {
        every { depotRepository.set(any()) } returns null

        assertNull(collector.onDepotResult(result(true, """{"30011":5}""")))
    }
}
