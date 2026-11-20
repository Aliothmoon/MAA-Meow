package com.aliothmoon.maameow.data.model

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import io.mockk.every
import io.mockk.mockk
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

    /** 旧键 `@profile:N` 与旧硬编码 `配置-N` 都只作为迁移输入，不再产生新键 */
    @Test
    fun legacyProfileNamesAreStillParseable() {
        assertEquals(3, parseAutoProfileName("@profile:3"))
        assertNull(parseAutoProfileName("配置-3"))
        assertEquals(3, parseLegacyProfileName("配置-3"))
        assertNull(parseLegacyProfileName("Profile 3"))
    }

    /** 新档编号要认得「当前语言下已经落成字面量」的默认名 */
    @Test
    fun defaultProfileNameParsesLocalizedLiteral() {
        val context = mockk<Context> {
            every { getString(R.string.profile_default_name, 0) } returns "Profile 0"
        }
        assertEquals(7, parseDefaultProfileName(context, "Profile 7"))
        assertNull(parseDefaultProfileName(context, "我的配置"))
        assertEquals(3, parseDefaultProfileName(context, "配置-3"))
        assertEquals(5, parseDefaultProfileName(context, "@profile:5"))
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
