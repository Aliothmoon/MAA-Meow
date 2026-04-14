package com.aliothmoon.maameow.presentation.view.panel

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.utils.i18n.UiText

/**
 * 面板 Tab 类型
 */
enum class PanelTab(@param:StringRes val labelRes: Int) {
    TASKS(R.string.panel_tab_tasks),
    AUTO_BATTLE(R.string.panel_tab_auto_battle),
    TOOLS(R.string.panel_tab_tools),
    LOG(R.string.panel_tab_log);

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
    val title: UiText,
    val message: UiText,
    val confirmText: UiText = UiText.Empty,
    val dismissText: UiText? = null,
    val confirmAction: PanelDialogConfirmAction = PanelDialogConfirmAction.DISMISS_ONLY
)
