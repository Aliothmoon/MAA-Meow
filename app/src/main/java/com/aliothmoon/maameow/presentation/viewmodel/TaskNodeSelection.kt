package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.data.model.TaskChainNode

/**
 * 任务列表面板右侧应展示的节点 id。
 *
 * - 新增任务 / 配置管理时保留当前选中（可为 null，用于对应模式 UI）
 * - 否则若选中无效或为空，回落到任务链第一项
 * - 链为空时为 null（占位）
 */
fun resolveTaskPanelSelectedNodeId(
    nodes: List<TaskChainNode>,
    selectedNodeId: String?,
    isAddingTask: Boolean,
    isProfileMode: Boolean,
): String? {
    if (isAddingTask || isProfileMode) return selectedNodeId
    if (nodes.isEmpty()) return null
    if (selectedNodeId != null && nodes.any { it.id == selectedNodeId }) return selectedNodeId
    return nodes.first().id
}
