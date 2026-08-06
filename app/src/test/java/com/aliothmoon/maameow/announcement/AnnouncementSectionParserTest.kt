package com.aliothmoon.maameow.announcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementSectionParserTest {

    private val markdown = """
        前言图片行

        ## 重要说明

        第一节内容

        ---

        ## 安全下载提示 (NEW!!!)

        第二节内容

        ---
    """.trimIndent()

    @Test
    fun parse_splitsByH2AndDetectsNew() {
        val sections = AnnouncementSectionParser.parse(markdown)
        assertEquals(2, sections.size)

        assertEquals("重要说明", sections[0].title)
        assertFalse(sections[0].isNew)
        assertTrue(sections[0].content.startsWith("## 重要说明"))
        assertTrue(sections[0].content.contains("第一节内容"))
        // 尾部分隔线被裁掉
        assertFalse(sections[0].content.endsWith("-"))

        assertEquals("安全下载提示", sections[1].title)
        assertTrue(sections[1].isNew)
        assertFalse(sections[1].content.contains("(NEW!!!)"))
    }

    @Test
    fun parse_preambleExcludedFromSections() {
        val sections = AnnouncementSectionParser.parse(markdown)
        assertTrue(sections.none { it.content.contains("前言图片行") })
    }

    @Test
    fun parse_noHeader_returnsEmpty() {
        assertTrue(AnnouncementSectionParser.parse("没有标题的纯文本").isEmpty())
    }

    @Test
    fun fullContent_replacesNewMark() {
        val full = AnnouncementSectionParser.fullContent(markdown)
        assertFalse(full.contains("(NEW!!!)"))
        assertTrue(full.contains("安全下载提示 *"))
        assertTrue(full.contains("前言图片行"))
    }
}
