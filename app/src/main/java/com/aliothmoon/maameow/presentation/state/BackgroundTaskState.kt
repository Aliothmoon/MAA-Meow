package com.aliothmoon.maameow.presentation.state

import com.aliothmoon.maameow.data.model.TaskType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.presentation.view.panel.PanelTab

enum class MonitorPhase {
    IDLE, BINDING, WAITING_FRAME, RUNNING, ERROR
}

data class BackgroundTaskState(
    val currentTaskType: TaskType = TaskType.COMBAT,
    val currentTab: PanelTab = PanelTab.TASKS,
    val monitorPhase: MonitorPhase = MonitorPhase.IDLE,
    val monitorErrorMessage: String? = null,
    val hasSurface: Boolean = false,
    val isMonitorRunning: Boolean = false,
    val isMonitorLoading: Boolean = false,
    val isFullscreenMonitor: Boolean = false,
    val dialog: PanelDialogUiState? = null,
)

