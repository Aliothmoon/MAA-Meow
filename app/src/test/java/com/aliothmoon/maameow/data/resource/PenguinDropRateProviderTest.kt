package com.aliothmoon.maameow.data.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 掉率表合并：企鹅物流 matrix 只有内部 stageId，计划里存的是用户输入的 code
 * （`PR-C-1` ↔ `pro_c_1`、`CE-6` ↔ `wk_melee_6`），换算错就会查不到掉率。
 */
class PenguinDropRateProviderTest {

    private val stages = """
        [
          {"stageId":"pro_c_1","code":"PR-C-1","zoneId":"weekly_1"},
          {"stageId":"wk_melee_6","code":"CE-6","zoneId":"weekly_9"},
          {"stageId":"main_01-07","code":"1-7","zoneId":"main_1"},
          {"stageId":"mem_unknown","code":null,"zoneId":"main_1"}
        ]
    """.trimIndent()

    private fun matrix(vararg rows: String) = """{"matrix":[${rows.joinToString(",")}]}"""

    @Test
    fun mergesStageCodeWithMatrixQuantityOverTimes() {
        val result = mergeDropRates(
            stagesBody = stages,
            matrixBody = matrix(
                """{"stageId":"pro_c_1","itemId":"30012","times":1000,"quantity":400}""",
                """{"stageId":"wk_melee_6","itemId":"30012","times":1798,"quantity":420}""",
            ),
        )

        assertEquals(0.4, result["PR-C-1|30012"]!!, 1e-9)
        assertEquals(420.0 / 1798.0, result["CE-6|30012"]!!, 1e-9)
    }

    @Test
    fun skipsRowsWithUnknownStageOrNonPositiveSamples() {
        val result = mergeDropRates(
            stagesBody = stages,
            matrixBody = matrix(
                """{"stageId":"mem_unknown","itemId":"30012","times":10,"quantity":5}""",
                """{"stageId":"pro_c_1","itemId":"30012","times":0,"quantity":0}""",
                """{"stageId":"pro_c_1","itemId":"30013","times":100,"quantity":0}""",
                """{"stageId":"main_01-07","itemId":"30012","times":10,"quantity":2}""",
            ),
        )

        // 没有 code 的 stage、times=0、quantity=0 全部丢弃；只留有效样本
        assertEquals(setOf("1-7|30012"), result.keys)
        assertEquals(0.2, result["1-7|30012"]!!, 1e-9)
    }

    @Test
    fun returnsEmptyWhenStagesOrMatrixMissing() {
        assertTrue(mergeDropRates("[]", matrix("""{"stageId":"x","itemId":"y","times":1,"quantity":1}""")).isEmpty())
        assertTrue(mergeDropRates(stages, "{}").isEmpty())
        assertTrue(mergeDropRates(stages, "not json").isEmpty())
    }

    @Test
    fun keyFormatIsStable() {
        assertEquals("1-7|30011", dropRateKey("1-7", "30011"))
    }
}
