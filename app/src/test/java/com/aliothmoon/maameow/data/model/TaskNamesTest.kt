package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 节点名/配置档名的存储键与显示渲染规则 */
class TaskNamesTest {

    @Test
    fun autoTaskNameRoundTrips() {
        assertEquals("@type:WAKE_UP", autoTaskName(TaskTypeInfo.WAKE_UP))
        assertEquals(TaskTypeInfo.WAKE_UP to "", parseAutoTaskName("@type:WAKE_UP"))
    }

    @Test
    fun autoTaskNameKeepsDuplicateNumber() {
        assertEquals(
            TaskTypeInfo.DEPOT_MAINTAIN to " 3",
            parseAutoTaskName("@type:DEPOT_MAINTAIN 3"),
        )
    }

    @Test
    fun customNameIsNotAnAutoName() {
        assertNull(parseAutoTaskName("库存保持"))
        assertNull(parseAutoTaskName("@type:NOT_A_TYPE"))
    }

    @Test
    fun profileNamesRoundTrip() {
        assertEquals("@profile:3", autoProfileName(3))
        assertEquals(3, parseAutoProfileName("@profile:3"))
        assertNull(parseAutoProfileName("配置-3"))
        assertEquals(3, parseLegacyProfileName("配置-3"))
        assertNull(parseLegacyProfileName("Profile 3"))
    }

    /** 旧版英文默认名曾写作「DepotMaintain」（无空格），迁移时必须仍然认得出来 */
    @Test
    fun normalizeIgnoresWhitespaceAndCase() {
        assertEquals(normalizeTaskName("Depot Maintain"), normalizeTaskName("DepotMaintain"))
        assertEquals(normalizeTaskName("Depot Maintain"), normalizeTaskName("depot maintain"))
        assertNotEquals(normalizeTaskName("Depot Maintain"), normalizeTaskName("Depot Maintainer"))
    }

    @Test
    fun autoNodeNameRendersFromResource() {
        val node = node(name = autoTaskName(TaskTypeInfo.WAKE_UP))
        assertEquals(uiTextOf(TaskTypeInfo.WAKE_UP.nameRes), node.nameUiText())
    }

    @Test
    fun customNodeNameIsLiteral() {
        assertEquals(UiText.Dynamic("夜间库存"), node(name = "夜间库存").nameUiText())
    }

    @Test
    fun autoNodeNameWithSuffixKeepsBothParts() {
        val node = node(name = autoTaskName(TaskTypeInfo.DEPOT_MAINTAIN))
        assertEquals(
            UiText.Joined(
                parts = listOf(uiTextOf(TaskTypeInfo.DEPOT_MAINTAIN.nameRes), UiText.Dynamic("#3")),
                separator = UiText.Dynamic(" "),
            ),
            node.nameUiText("#3"),
        )
    }

    @Test
    fun customNodeNameWithSuffixIsPlainString() {
        assertEquals(UiText.Dynamic("夜间库存 #3"), node(name = "夜间库存").nameUiText("#3"))
    }

    private fun node(name: String) = TaskChainNode(name = name, config = DepotMaintainConfig())
}
