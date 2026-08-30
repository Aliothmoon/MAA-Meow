package com.aliothmoon.maameow.data.model.copilot

import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 战斗列表项的 id 是拖拽与列表 key 的唯一依据：
 * 必须落盘、必须能从无 id 的旧列表文件恢复、copy 后必须保持不变
 */
class CopilotListItemIdTest {

    @Test
    fun idIsEncodedAndRoundTrips() {
        val item = CopilotListItem(name = "1-7", filePath = "/a/1.json")

        val json = JsonUtils.common.encodeToString(item)
        val decoded = JsonUtils.common.decodeFromString<CopilotListItem>(json)

        assertTrue(json.contains("\"id\""))
        assertEquals(item.id, decoded.id)
    }

    @Test
    fun legacyJsonWithoutId_getsDistinctIds() {
        // 同一作业的普通/突袭两项除 isRaid 外完全相同，旧文件里没有 id
        val legacy = """
            [
              {"name":"1-7","filePath":"/a/1.json","isRaid":false,"copilotId":1,"isChecked":true,"source":"web"},
              {"name":"1-7","filePath":"/a/1.json","isRaid":true,"copilotId":1,"isChecked":true,"source":"web"}
            ]
        """.trimIndent()

        val items = JsonUtils.common.decodeFromString<List<CopilotListItem>>(legacy)

        assertEquals(2, items.size)
        assertTrue(items.all { it.id.isNotBlank() })
        assertNotEquals(items[0].id, items[1].id)
    }

    @Test
    fun copyKeepsId() {
        val item = CopilotListItem(name = "1-7", filePath = "/a/1.json")

        assertEquals(item.id, item.copy(isChecked = false).id)
    }
}
