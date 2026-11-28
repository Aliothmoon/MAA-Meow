package com.aliothmoon.maameow.web.api

import com.aliothmoon.maameow.data.model.LogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogDeltaTest {

    private fun log(id: Long) = LogItem(id = id, content = "#$id")

    private fun ids(change: LogDelta.Change?) = change?.items?.map { it.id }

    @Test
    fun `first list is sent whole`() {
        val change = LogDelta().next(listOf(log(1), log(2)))

        assertEquals(true, change?.reset)
        assertEquals(listOf(1L, 2L), ids(change))
    }

    @Test
    fun `only items after the last sent one are appended`() {
        val delta = LogDelta()
        delta.next(listOf(log(1), log(2)))

        val change = delta.next(listOf(log(1), log(2), log(3), log(4)))

        assertEquals(false, change?.reset)
        assertEquals(listOf(3L, 4L), ids(change))
    }

    @Test
    fun `same list yields nothing`() {
        val delta = LogDelta()
        val logs = listOf(log(1))
        delta.next(logs)

        assertNull(delta.next(logs))
    }

    @Test
    fun `head trimmed by the cap still appends`() {
        val delta = LogDelta()
        delta.next(listOf(log(1), log(2), log(3)))

        val change = delta.next(listOf(log(3), log(4)))

        assertEquals(false, change?.reset)
        assertEquals(listOf(4L), ids(change))
    }

    @Test
    fun `cleared list resets to empty`() {
        val delta = LogDelta()
        delta.next(listOf(log(1)))

        val change = delta.next(emptyList())

        assertEquals(true, change?.reset)
        assertEquals(emptyList<Long>(), ids(change))
        assertNull(delta.next(emptyList()))
    }

    @Test
    fun `last sent item gone resets with the whole list`() {
        val delta = LogDelta()
        delta.next(listOf(log(1), log(2)))

        // 清空后又来了新日志，id 依旧递增
        val change = delta.next(listOf(log(5), log(6)))

        assertEquals(true, change?.reset)
        assertEquals(listOf(5L, 6L), ids(change))
    }
}
