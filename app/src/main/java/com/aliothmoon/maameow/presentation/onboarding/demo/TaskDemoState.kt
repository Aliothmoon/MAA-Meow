package com.aliothmoon.maameow.presentation.onboarding.demo

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.SwitchThemeConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.preferences.duplicateTaskName
import java.time.DayOfWeek
import java.util.UUID

/** 演示任务列表，纯内存，操作语义照搬 BackgroundTaskViewModel */
@Stable
class TaskDemoState(
    private val nightTheme: String,
    private val nameOf: (TaskTypeInfo) -> String,
) {

    var nodes by mutableStateOf(initialNodes())
        private set

    var ui by mutableStateOf(TaskDemoUi())
        private set

    val selectedNode: TaskChainNode? get() = nodes.firstOrNull { it.id == ui.selectedNodeId }

    fun reset() {
        nodes = initialNodes()
        ui = TaskDemoUi()
    }

    private fun initialNodes() = listOf(newNode(TaskTypeInfo.WAKE_UP), newNode(TaskTypeInfo.COMBAT))

    private fun newNode(type: TaskTypeInfo): TaskChainNode {
        val config = type.defaultConfig()
        return TaskChainNode(
            id = UUID.randomUUID().toString(),
            name = nameOf(type),
            enabled = true,
            // 真实默认全勾，演示全不勾，否则勾周一周日无事可做
            config = (config as? FightConfig)?.copy(weeklySchedule = NO_DAYS) ?: config,
        )
    }

    fun select(nodeId: String) {
        ui = ui.copy(selectedNodeId = nodeId, isAddingTask = false)
    }

    fun toggleEditMode() {
        ui = ui.copy(isEditMode = !ui.isEditMode, isAddingTask = false)
    }

    fun toggleAddingTask() {
        ui = ui.copy(isAddingTask = !ui.isAddingTask, selectedNodeId = null)
    }

    fun add(type: TaskTypeInfo) {
        val node = newNode(type)
        nodes = nodes + node
        ui = ui.copy(isAddingTask = false, selectedNodeId = node.id)
    }

    fun remove(nodeId: String) {
        nodes = nodes.filterNot { it.id == nodeId }
        if (ui.selectedNodeId == nodeId) ui = ui.copy(selectedNodeId = null)
    }

    fun duplicate(nodeId: String) {
        val index = nodes.indexOfFirst { it.id == nodeId }
        if (index < 0) return
        val src = nodes[index]
        val copy = src.copy(
            id = UUID.randomUUID().toString(),
            name = duplicateTaskName(src.name, nodes.map { it.name }),
        )
        nodes = nodes.toMutableList().apply { add(index + 1, copy) }
        ui = ui.copy(selectedNodeId = copy.id)
    }

    fun rename(nodeId: String, name: String) = update(nodeId) { it.copy(name = name) }

    fun setEnabled(nodeId: String, enabled: Boolean) = update(nodeId) { it.copy(enabled = enabled) }

    fun updateConfig(config: TaskParamProvider) {
        val id = ui.selectedNodeId ?: return
        update(id) { it.copy(config = prefillTheme(it.config, config)) }
    }

    /** 同上游 AddGuideDemoTheme，新增候选直接填夜间免打字 */
    private fun prefillTheme(old: TaskParamProvider, new: TaskParamProvider): TaskParamProvider {
        if (old !is SwitchThemeConfig || new !is SwitchThemeConfig) return new
        val added = new.themes.size == old.themes.size + 1 && new.themes.last().isEmpty()
        return if (added) new.copy(themes = new.themes.dropLast(1) + nightTheme) else new
    }

    fun move(from: Int, to: Int) {
        if (from !in nodes.indices || to !in nodes.indices) return
        nodes = nodes.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun update(nodeId: String, transform: (TaskChainNode) -> TaskChainNode) {
        nodes = nodes.map { if (it.id == nodeId) transform(it) else it }
    }

    private companion object {
        val NO_DAYS = DayOfWeek.entries.associate { it.name to false }
    }
}
