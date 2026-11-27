package com.aliothmoon.maameow.presentation.state

import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.presentation.view.panel.PanelTab

data class BackgroundTaskState(
    val selectedNodeId: String? = null,
    val current: PanelTab = PanelTab.TASKS,
    val isFullscreenMonitor: Boolean = false,
    val canInjectInput: Boolean = false,
    val isInputBarVisible: Boolean = false,
    val isEditMode: Boolean = false,
    val isAddingTask: Boolean = false,
    val isProfileMode: Boolean = false,
    val dialog: PanelDialogUiState? = null,
)
