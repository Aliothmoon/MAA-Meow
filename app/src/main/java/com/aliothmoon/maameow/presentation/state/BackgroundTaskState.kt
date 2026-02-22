package com.aliothmoon.maameow.presentation.state

import com.aliothmoon.maameow.data.model.TaskType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.presentation.view.panel.PanelTab

data class BackgroundTaskState(
    val currentTaskType: TaskType = TaskType.COMBAT,
    val currentTab: PanelTab = PanelTab.TASKS,
    val isMonitorRunning: Boolean = false,
    val isFullscreenMonitor: Boolean = false,
    val dialog: PanelDialogUiState? = null,
)
