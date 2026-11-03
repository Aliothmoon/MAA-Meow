package com.aliothmoon.maameow.presentation.onboarding.demo

import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.SwitchThemeConfig
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 演示沙盒：新增候选预填夜间（同上游 AddGuideDemoTheme），重置回初始列表 */
class TaskDemoStateTest {

    private val demo = TaskDemoState("夜间") { it.name }

    private fun selectedThemes() = (demo.selectedNode!!.config as SwitchThemeConfig).themes

    @Test
    fun addThemeCandidate_prefillsNight() {
        demo.add(TaskTypeInfo.SWITCH_THEME)
        demo.updateConfig(SwitchThemeConfig(themes = listOf("")))
        assertEquals(listOf("夜间"), selectedThemes())
    }

    @Test
    fun editingCandidate_keepsUserInput() {
        demo.add(TaskTypeInfo.SWITCH_THEME)
        demo.updateConfig(SwitchThemeConfig(themes = listOf("")))
        demo.updateConfig(SwitchThemeConfig(themes = listOf("白天")))
        assertEquals(listOf("白天"), selectedThemes())
        // 清空不算新增，不会被填回去
        demo.updateConfig(SwitchThemeConfig(themes = listOf("")))
        assertEquals(listOf(""), selectedThemes())
    }

    @Test
    fun newFight_weeklyAllUnchecked() {
        demo.add(TaskTypeInfo.COMBAT)
        val config = demo.selectedNode!!.config as FightConfig
        assertFalse(config.weeklySchedule.values.any { it })
    }

    @Test
    fun reset_restoresInitialList() {
        demo.toggleEditMode()
        demo.add(TaskTypeInfo.COMBAT)
        demo.reset()
        assertEquals(listOf(TaskTypeInfo.WAKE_UP.name, TaskTypeInfo.COMBAT.name), demo.nodes.map { it.name })
        assertFalse(demo.ui.isEditMode)
        assertNull(demo.ui.selectedNodeId)
    }
}
