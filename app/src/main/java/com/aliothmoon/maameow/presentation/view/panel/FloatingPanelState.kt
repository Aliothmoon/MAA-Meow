package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.runtime.Stable

/**
 * 面板 Tab 类型
 */
enum class PanelTab(val displayName: String) {
    TASKS("一键长草"),
    AUTO_BATTLE("自动战斗"),
    TOOLS("小工具"),
    LOG("日志");

    companion object {
        fun canShowTaskActions(state: PanelTab): Boolean {
            return state == TASKS || state == AUTO_BATTLE || state == TOOLS
        }
    }
}

@Stable
data class FloatingPanelState(
    val isExpanded: Boolean = false,
    val currentTab: PanelTab = PanelTab.TASKS,
    val selectedNodeId: String? = null,
    val isEditMode: Boolean = false,
    val isAddingTask: Boolean = false,
    val isProfileMode: Boolean = false,
    val dialog: PanelDialogUiState? = null
)

enum class PanelDialogType {
    SUCCESS,
    WARNING,
    ERROR
}

enum class PanelDialogConfirmAction {
    DISMISS_ONLY,
    CONFIRM_PENDING_START,
    GO_LOG,
    GO_LOG_AND_STOP
}

@Stable
data class PanelDialogUiState(
    val type: PanelDialogType,
    val title: String,
    val message: String,
    val confirmText: String = "确认",
    val dismissText: String = "关闭",
    val confirmAction: PanelDialogConfirmAction = PanelDialogConfirmAction.DISMISS_ONLY
)
