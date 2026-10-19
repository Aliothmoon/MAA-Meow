package com.aliothmoon.maameow.presentation.view.panel

import android.provider.Settings
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
        // 日志页也提供停止和快捷选项；是否允许启动由各页面的操作栏决定。
        fun canShowTaskActions(state: PanelTab): Boolean {
            return state == TASKS || state == AUTO_BATTLE || state == TOOLS || state == LOG
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

/**
 * 前置检查的修复动作：有它时修复按钮最醒目，「仍然启动」降为文字按钮
 * [settingsAction] 非空即跳系统设置页并关掉弹窗
 */
enum class PanelDialogFixAction(@param:StringRes val labelRes: Int, val settingsAction: String? = null) {
    /** 用 Root 开小米「USB 调试（安全设置）」，开成功按确认继续启动 */
    ENABLE_ADB_INPUT_WITH_ROOT(R.string.task_start_enable_adb_input_with_root),
    OPEN_DEVELOPER_SETTINGS(
        R.string.task_start_fix_open_developer_settings,
        Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
    ),
    OPEN_DISPLAY_SETTINGS(R.string.task_start_fix_open_display_settings, Settings.ACTION_DISPLAY_SETTINGS),
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
    val confirmAction: PanelDialogConfirmAction = PanelDialogConfirmAction.DISMISS_ONLY,
    val fixAction: PanelDialogFixAction? = null,
)
