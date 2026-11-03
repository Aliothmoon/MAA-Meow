package com.aliothmoon.maameow.presentation.onboarding.demo

import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.SwitchThemeConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.presentation.components.GuideAnchor
import com.aliothmoon.maameow.presentation.components.GuideHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

/** 任务设置演示门槛，对齐上游 v6.21.2 ComputeGuideGateStep，去掉开始唤醒补回与重命名 */
class TaskDemoRulesTest {

    private val night = "夜间"
    private val noDays = DayOfWeek.entries.associate { it.name to false }

    private val wake = TaskChainNode(id = "wake", name = "开始唤醒", config = WakeUpConfig())
    private fun fight(id: String, stage: String = "", weekly: Boolean = false, days: Map<String, Boolean> = noDays) =
        TaskChainNode(
            id = id,
            name = "理智作战",
            config = FightConfig(stage1 = stage, useWeeklySchedule = weekly, weeklySchedule = days),
        )

    private fun theme(id: String = "theme", vararg themes: String) =
        TaskChainNode(id = id, name = "更换主题", config = SwitchThemeConfig(themes.toList()))

    private val anni = TaskDemoRules.ANNIHILATION
    private val mondaySunday = noDays + mapOf("MONDAY" to true, "SUNDAY" to true)

    private fun gate(vararg nodes: TaskChainNode) = TaskDemoRules.gate(nodes.toList(), night)

    @Test
    fun initial_needsAnotherFight_andWalksAddFlow() {
        val nodes = listOf(wake, fight("f1"))
        val gate = TaskDemoRules.gate(nodes, night)
        assertEquals(TaskDemoGate.ADD_FIGHT, gate)
        assertEquals(GuideHighlight(GuideAnchor.EDIT_TOGGLE), TaskDemoRules.highlight(gate, nodes, TaskDemoUi()))
        assertEquals(
            GuideHighlight(GuideAnchor.ADD_TASK),
            TaskDemoRules.highlight(gate, nodes, TaskDemoUi(isEditMode = true)),
        )
        assertEquals(
            GuideHighlight(GuideAnchor.GALLERY_ITEM, TaskTypeInfo.COMBAT.name),
            TaskDemoRules.highlight(gate, nodes, TaskDemoUi(isEditMode = true, isAddingTask = true)),
        )
    }

    @Test
    fun noFight_restores() {
        assertEquals(TaskDemoGate.RESTORE_FIGHT, gate(wake))
    }

    @Test
    fun twoDefaultFights_selectStageOnNewest() {
        val nodes = listOf(wake, fight("f1"), fight("f2"))
        val gate = TaskDemoRules.gate(nodes, night)
        assertEquals(TaskDemoGate.SELECT_STAGE, gate)
        // 刚加完还在编辑模式：先点完成
        assertEquals(
            GuideHighlight(GuideAnchor.EDIT_TOGGLE),
            TaskDemoRules.highlight(gate, nodes, TaskDemoUi(isEditMode = true, selectedNodeId = "f2")),
        )
        assertEquals(
            GuideHighlight(GuideAnchor.NODE, "f2"),
            TaskDemoRules.highlight(gate, nodes, TaskDemoUi(selectedNodeId = "f1")),
        )
        assertEquals(
            GuideHighlight(GuideAnchor.STAGE_ITEM, anni),
            TaskDemoRules.highlight(gate, nodes, TaskDemoUi(selectedNodeId = "f2")),
        )
    }

    @Test
    fun annihilationBelowDefault_reorderWithDragUp() {
        val nodes = listOf(wake, fight("f1"), fight("f2", anni))
        val gate = TaskDemoRules.gate(nodes, night)
        assertEquals(TaskDemoGate.REORDER_FIGHT, gate)
        assertEquals(GuideHighlight(GuideAnchor.NODE, "f2"), TaskDemoRules.highlight(gate, nodes, TaskDemoUi()))
        assertEquals(DragHint("f2", "f1", below = false), TaskDemoRules.dragHint(gate, nodes))
    }

    @Test
    fun deletingDefaultFight_restoresInsteadOfPassingReorder() {
        // 上游只在一个理智作战都没有时补回，这里剿灭那个还在也要补
        assertEquals(TaskDemoGate.RESTORE_FIGHT, gate(wake, fight("f2", anni)))
    }

    @Test
    fun weekly_enableThenCheckMondayAndSunday() {
        assertEquals(TaskDemoGate.ENABLE_WEEKLY, gate(wake, fight("f2", anni), fight("f1")))

        val unchecked = listOf(wake, fight("f2", anni, weekly = true), fight("f1"))
        val gate = TaskDemoRules.gate(unchecked, night)
        assertEquals(TaskDemoGate.CHECK_WEEK_DAYS, gate)
        assertEquals(
            GuideHighlight(GuideAnchor.WEEKLY_DAY, "MONDAY"),
            TaskDemoRules.highlight(gate, unchecked, TaskDemoUi(selectedNodeId = "f2")),
        )

        val mondayOnly = listOf(wake, fight("f2", anni, weekly = true, days = noDays + ("MONDAY" to true)), fight("f1"))
        assertEquals(
            GuideHighlight(GuideAnchor.WEEKLY_DAY, "SUNDAY"),
            TaskDemoRules.highlight(TaskDemoGate.CHECK_WEEK_DAYS, mondayOnly, TaskDemoUi(selectedNodeId = "f2")),
        )
    }

    @Test
    fun theme_addThenMoveLastThenNight() {
        val ready = fight("f2", anni, weekly = true, days = mondaySunday)
        assertEquals(TaskDemoGate.ADD_SWITCH_THEME, gate(wake, ready, fight("f1")))

        val notLast = listOf(wake, ready, theme(), fight("f1"))
        assertEquals(TaskDemoGate.REORDER_THEME, TaskDemoRules.gate(notLast, night))
        assertEquals(DragHint("theme", "f1", below = true), TaskDemoRules.dragHint(TaskDemoGate.REORDER_THEME, notLast))

        val empty = listOf(wake, ready, fight("f1"), theme())
        assertEquals(TaskDemoGate.ADD_NIGHT_THEME, TaskDemoRules.gate(empty, night))
        assertEquals(
            GuideHighlight(GuideAnchor.THEME_ADD),
            TaskDemoRules.highlight(TaskDemoGate.ADD_NIGHT_THEME, empty, TaskDemoUi(selectedNodeId = "theme")),
        )

        val typing = listOf(wake, ready, fight("f1"), theme("theme", "白天", ""))
        assertEquals(
            GuideHighlight(GuideAnchor.THEME_FIELD, "1"),
            TaskDemoRules.highlight(TaskDemoGate.ADD_NIGHT_THEME, typing, TaskDemoUi(selectedNodeId = "theme")),
        )

        val done = listOf(wake, ready, fight("f1"), theme("theme", " 夜间 "))
        assertEquals(TaskDemoGate.DONE, TaskDemoRules.gate(done, night))
        assertNull(TaskDemoRules.highlight(TaskDemoGate.DONE, done, TaskDemoUi()))
    }
}
