package com.aliothmoon.maameow.domain.service

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `maa://` 旧格式需长期保留解析兼容。
 * 这些用例固化各格式的解析契约，防止后续误删旧格式分支。
 */
class CopilotCodeParsingTest {

    private val manager = CopilotManager(apiService = mockk(), repository = mockk())

    @Test
    fun legacyPrefix_parsedAsCopilot_andMarkedAmbiguous() {
        val code = manager.parseCopilotCode("maa://12345")!!

        assertEquals(CopilotCodeType.COPILOT, code.type)
        assertEquals(12345, code.id)
        assertTrue("旧格式无法区分作业/作业集，须标记 ambiguous", code.ambiguous)
    }

    @Test
    fun legacyPrefixWithListParam_parsedAsCopilotSet() {
        val code = manager.parseCopilotCode("maa://12345?list=1")!!

        assertEquals(CopilotCodeType.COPILOT_SET, code.type)
        assertEquals(12345, code.id)
    }

    @Test
    fun legacyPrefix_isCaseInsensitive() {
        assertEquals(12345, manager.parseCopilotCode("MAA://12345")?.id)
    }

    @Test
    fun newPrefix_parsedAsCopilot_withoutAmbiguity() {
        val code = manager.parseCopilotCode("prts://12345")!!

        assertEquals(CopilotCodeType.COPILOT, code.type)
        assertEquals(12345, code.id)
        assertEquals(false, code.ambiguous)
    }

    @Test
    fun newSetPrefix_winsOverPlainNewPrefix() {
        val code = manager.parseCopilotCode("prts://s12345")!!

        assertEquals(CopilotCodeType.COPILOT_SET, code.type)
        assertEquals(12345, code.id)
    }

    @Test
    fun bareSetCode_parsedAsCopilotSet() {
        assertEquals(CopilotCodeType.COPILOT_SET, manager.parseCopilotCode("s12345")?.type)
    }

    @Test
    fun bareNumber_parsedAsCopilot_andMarkedAmbiguous() {
        val code = manager.parseCopilotCode("12345")!!

        assertEquals(CopilotCodeType.COPILOT, code.type)
        assertTrue(code.ambiguous)
    }

    @Test
    fun surroundingWhitespace_isTrimmed() {
        assertEquals(12345, manager.parseCopilotCode("  prts://12345  ")?.id)
    }

    @Test
    fun blankAndMalformedInput_returnsNull() {
        assertNull(manager.parseCopilotCode(""))
        assertNull(manager.parseCopilotCode("   "))
        assertNull(manager.parseCopilotCode("maa://abc"))
        assertNull(manager.parseCopilotCode("prts://"))
    }
}
