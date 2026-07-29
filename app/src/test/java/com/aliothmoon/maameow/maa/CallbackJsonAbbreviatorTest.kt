package com.aliothmoon.maameow.maa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallbackJsonAbbreviatorTest {

    private val limit = CallbackJsonAbbreviator.MAX_LOGGED_CHARS

    @Test
    fun nullJson_isRenderedAsLiteralNull() {
        assertEquals("null", CallbackJsonAbbreviator.abbreviate(null))
    }

    @Test
    fun emptyJson_passesThrough() {
        assertEquals("", CallbackJsonAbbreviator.abbreviate(""))
    }

    @Test
    fun shortJson_passesThroughUnchanged() {
        val json = """{"taskchain":"Fight","taskid":3}"""
        assertEquals(json, CallbackJsonAbbreviator.abbreviate(json))
    }

    /** 边界：恰好等于上限不截断，多一个字符才截断 */
    @Test
    fun jsonAtExactlyLimit_isNotTruncated() {
        val json = "x".repeat(limit)
        assertEquals(json, CallbackJsonAbbreviator.abbreviate(json))
    }

    @Test
    fun jsonOneCharOverLimit_isTruncated() {
        val json = "x".repeat(limit + 1)

        val result = CallbackJsonAbbreviator.abbreviate(json)

        assertTrue(result.startsWith("x".repeat(limit)))
        assertTrue(result.endsWith("…<truncated, total ${limit + 1} chars>"))
    }

    /**
     * 截断必须标注原始长度 —— 否则读日志的人会以为内容就这么多，
     * 把「识别到 3 个材料」误判成识别失败。
     */
    @Test
    fun truncatedJson_reportsOriginalLength() {
        val json = "y".repeat(200_000)

        val result = CallbackJsonAbbreviator.abbreviate(json)

        assertTrue("必须写明原始长度", result.contains("total 200000 chars"))
    }

    /** 路由字段（what / taskchain / subtask）在 JSON 靠前，截断后必须仍可见 */
    @Test
    fun truncation_keepsLeadingRoutingFields() {
        val head = """{"taskchain":"Depot","subtask":"ProcessTask","what":"StageDrops","details":{"data":""""
        val json = head + "3".repeat(100_000) + "\"}}"

        val result = CallbackJsonAbbreviator.abbreviate(json)

        assertTrue(result.contains(""""taskchain":"Depot""""))
        assertTrue(result.contains(""""what":"StageDrops""""))
    }

    /** 截断后的长度有界，不随输入增长 */
    @Test
    fun truncatedLength_isBounded() {
        val small = CallbackJsonAbbreviator.abbreviate("a".repeat(limit + 10))
        val huge = CallbackJsonAbbreviator.abbreviate("a".repeat(5_000_000))

        assertTrue(small.length < limit + 64)
        // 仅后缀里的位数差异，不应量级增长
        assertTrue(huge.length < limit + 64)
    }
}
