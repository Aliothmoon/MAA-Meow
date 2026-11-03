package com.aliothmoon.maameow.presentation.onboarding.demo

import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.SwitchThemeConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.presentation.components.GuideAnchor
import com.aliothmoon.maameow.presentation.components.GuideHighlight
import java.time.DayOfWeek

/** 按引导顺序排列，取第一个未满足的 */
enum class TaskDemoGate {
    RESTORE_FIGHT,
    ADD_FIGHT,
    SELECT_STAGE,
    REORDER_FIGHT,
    ENABLE_WEEKLY,
    CHECK_WEEK_DAYS,
    ADD_SWITCH_THEME,
    REORDER_THEME,
    ADD_NIGHT_THEME,
    DONE,
}

/** 语义同 BackgroundTaskViewModel */
data class TaskDemoUi(
    val isEditMode: Boolean = false,
    val isAddingTask: Boolean = false,
    val selectedNodeId: String? = null,
)

/** 拖动手势：把 nodeId 拖到 anchorId 的上沿或下沿 */
data class DragHint(val nodeId: String, val anchorId: String, val below: Boolean)

/**
 * 对齐上游 ComputeGuideGateStep，只看最终状态
 *
 * 去掉补回开始唤醒与重命名；补回理智作战看普通关卡，否则删掉默认那个后排序会被空判通过
 */
object TaskDemoRules {

    const val ANNIHILATION = "Annihilation"

    private val REQUIRED_DAYS = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)

    private val TaskChainNode.fight get() = config as? FightConfig
    private val TaskChainNode.isAnnihilation get() = fight?.stage1 == ANNIHILATION
    private val TaskChainNode.theme get() = config as? SwitchThemeConfig

    private fun FightConfig.dayChecked(day: DayOfWeek) = weeklySchedule[day.name] != false

    fun gate(nodes: List<TaskChainNode>, nightTheme: String): TaskDemoGate {
        val fights = nodes.filter { it.fight != null }
        val annihilation = fights.firstOrNull { it.isAnnihilation }
            ?: return when {
                fights.isEmpty() -> TaskDemoGate.RESTORE_FIGHT
                fights.size >= 2 -> TaskDemoGate.SELECT_STAGE
                else -> TaskDemoGate.ADD_FIGHT
            }
        val defaults = fights.filterNot { it.isAnnihilation }
        if (defaults.isEmpty()) return TaskDemoGate.RESTORE_FIGHT

        val annihilationIndex = nodes.indexOf(annihilation)
        if (defaults.any { nodes.indexOf(it) < annihilationIndex }) return TaskDemoGate.REORDER_FIGHT

        val config = annihilation.fight!!
        if (!config.useWeeklySchedule) return TaskDemoGate.ENABLE_WEEKLY
        if (REQUIRED_DAYS.any { !config.dayChecked(it) }) return TaskDemoGate.CHECK_WEEK_DAYS

        val themes = nodes.filter { it.theme != null }
        if (themes.isEmpty()) return TaskDemoGate.ADD_SWITCH_THEME
        if (nodes.last().theme == null) return TaskDemoGate.REORDER_THEME
        if (themes.none { node -> node.theme!!.themes.any { it.trim() == nightTheme } }) {
            return TaskDemoGate.ADD_NIGHT_THEME
        }
        return TaskDemoGate.DONE
    }

    /** 改关卡指新加的（追加在尾部），周计划指剿灭，主题指最后一个更换主题 */
    private fun target(gate: TaskDemoGate, nodes: List<TaskChainNode>): TaskChainNode? = when (gate) {
        TaskDemoGate.SELECT_STAGE -> nodes.lastOrNull { it.fight != null }
        TaskDemoGate.REORDER_FIGHT,
        TaskDemoGate.ENABLE_WEEKLY,
        TaskDemoGate.CHECK_WEEK_DAYS -> nodes.firstOrNull { it.isAnnihilation }

        TaskDemoGate.REORDER_THEME,
        TaskDemoGate.ADD_NIGHT_THEME -> nodes.lastOrNull { it.theme != null }

        else -> null
    }

    /** 先进出编辑，再选中任务，最后才是面板里的控件 */
    fun highlight(gate: TaskDemoGate, nodes: List<TaskChainNode>, ui: TaskDemoUi): GuideHighlight? {
        fun add(type: TaskTypeInfo) = when {
            !ui.isEditMode -> GuideHighlight(GuideAnchor.EDIT_TOGGLE)
            !ui.isAddingTask -> GuideHighlight(GuideAnchor.ADD_TASK)
            else -> GuideHighlight(GuideAnchor.GALLERY_ITEM, type.name)
        }

        fun configure(target: TaskChainNode, inner: GuideHighlight) = when {
            ui.isEditMode -> GuideHighlight(GuideAnchor.EDIT_TOGGLE)
            ui.selectedNodeId != target.id -> GuideHighlight(GuideAnchor.NODE, target.id)
            else -> inner
        }

        val target = target(gate, nodes)
        return when (gate) {
            TaskDemoGate.RESTORE_FIGHT, TaskDemoGate.ADD_FIGHT -> add(TaskTypeInfo.COMBAT)
            TaskDemoGate.ADD_SWITCH_THEME -> add(TaskTypeInfo.SWITCH_THEME)
            TaskDemoGate.SELECT_STAGE ->
                configure(target!!, GuideHighlight(GuideAnchor.STAGE_ITEM, ANNIHILATION))

            TaskDemoGate.REORDER_FIGHT, TaskDemoGate.REORDER_THEME ->
                GuideHighlight(GuideAnchor.NODE, target!!.id)

            TaskDemoGate.ENABLE_WEEKLY ->
                configure(target!!, GuideHighlight(GuideAnchor.WEEKLY_SWITCH))

            TaskDemoGate.CHECK_WEEK_DAYS -> {
                val day = REQUIRED_DAYS.first { !target!!.fight!!.dayChecked(it) }
                configure(target!!, GuideHighlight(GuideAnchor.WEEKLY_DAY, day.name))
            }

            TaskDemoGate.ADD_NIGHT_THEME -> {
                val themes = target!!.theme!!.themes
                val inner = if (themes.isEmpty()) {
                    GuideHighlight(GuideAnchor.THEME_ADD)
                } else {
                    GuideHighlight(GuideAnchor.THEME_FIELD, themes.lastIndex.toString())
                }
                configure(target, inner)
            }

            TaskDemoGate.DONE -> null
        }
    }

    /** 剿灭拖到最上面的普通理智作战上沿，更换主题拖到末尾下沿 */
    fun dragHint(gate: TaskDemoGate, nodes: List<TaskChainNode>): DragHint? {
        val target = target(gate, nodes) ?: return null
        return when (gate) {
            TaskDemoGate.REORDER_FIGHT -> {
                val top = nodes.first { it.fight != null && !it.isAnnihilation }
                DragHint(target.id, top.id, below = false)
            }

            TaskDemoGate.REORDER_THEME -> DragHint(target.id, nodes.last().id, below = true)
            else -> null
        }
    }
}
