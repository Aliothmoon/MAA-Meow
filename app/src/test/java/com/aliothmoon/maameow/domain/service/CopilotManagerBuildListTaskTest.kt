package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.JsonUtils
import io.mockk.mockk
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 战斗列表提交契约：
 * - 只发勾选项，但 id 取的是完整列表下标（与 core 回传的 CopilotListLoadTaskFileSuccess.id 同坐标系）
 * - 主线与悖论是仅有的两种列表形态，保全不再有逐个 SSSCopilot 的特殊分支
 */
class CopilotManagerBuildListTaskTest {

    private val manager = CopilotManager(apiService = mockk(), repository = mockk())

    private val items = listOf(
        CopilotListItem(name = "1-7", filePath = "/c/1.json", isChecked = true),
        CopilotListItem(name = "1-8", filePath = "/c/2.json", isChecked = false),
        CopilotListItem(name = "1-9", filePath = "/c/3.json", isChecked = true, isRaid = true),
    )

    @Test
    fun mainTab_sendsSingleCopilotTaskWithCheckedItemsIndexedByFullList() {
        val tasks = manager.buildListTask(tabIndex = 0, items = items, config = CopilotConfig())

        assertEquals(1, tasks.size)
        assertEquals(MaaTaskType.COPILOT, tasks.single().type)
        val params = JsonUtils.common.parseToJsonElement(tasks.single().params).jsonObject
        val list = params.getValue("copilot_list").jsonArray.map { it.jsonObject }
        assertEquals(listOf(0, 2), list.map { it.getValue("id").jsonPrimitive.int })
        assertEquals(listOf("/c/1.json", "/c/3.json"), list.map { it.getValue("filename").jsonPrimitive.content })
        assertEquals(listOf(false, true), list.map { it.getValue("is_raid").jsonPrimitive.content.toBoolean() })
        assertEquals("列表模式固定单次消费", 1, params.getValue("loop_times").jsonPrimitive.int)
    }

    @Test
    fun paradoxTab_sendsParadoxListIndexedByFullList() {
        val tasks = manager.buildListTask(tabIndex = 2, items = items, config = CopilotConfig())

        assertEquals(1, tasks.size)
        assertEquals(MaaTaskType.PARADOX_COPILOT, tasks.single().type)
        val list = JsonUtils.common.parseToJsonElement(tasks.single().params)
            .jsonObject.getValue("list").jsonArray.map { it.jsonObject }
        assertEquals(listOf(0, 2), list.map { it.getValue("id").jsonPrimitive.int })
    }

    @Test
    fun sssTab_hasNoDedicatedListBranch() {
        val tasks = manager.buildListTask(tabIndex = 1, items = items, config = CopilotConfig())

        assertEquals(1, tasks.size)
        assertEquals(MaaTaskType.COPILOT, tasks.single().type)
    }
}
