package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.resolveStartResultMessage
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf

internal fun Context.resolveTaskStartFailureMessage(
    result: MaaCompositionService.StartResult,
): UiText? = resolveStartResultMessage(result)

internal fun Context.formatStartResult(
    result: MaaCompositionService.StartResult,
    successMessage: UiText = uiTextOf(R.string.task_start_success),
): UiText {
    return resolveTaskStartFailureMessage(result) ?: successMessage
}

internal fun Context.createStartFailedDialog(message: UiText): PanelDialogUiState {
    return PanelDialogUiState(
        type = PanelDialogType.ERROR,
        title = uiTextOf(R.string.task_start_dialog_failed_title),
        message = message,
        confirmText = uiTextOf(R.string.task_start_dialog_view_log),
        confirmAction = PanelDialogConfirmAction.GO_LOG,
    )
}

internal fun Context.createStartBlockedDialog(message: UiText): PanelDialogUiState {
    return PanelDialogUiState(
        type = PanelDialogType.WARNING,
        title = uiTextOf(R.string.task_start_dialog_info_title),
        message = message,
        confirmText = uiTextOf(R.string.task_start_dialog_ack),
        confirmAction = PanelDialogConfirmAction.DISMISS_ONLY,
    )
}

internal fun Context.createStartWarningDialog(message: UiText): PanelDialogUiState {
    return PanelDialogUiState(
        type = PanelDialogType.WARNING,
        title = uiTextOf(R.string.toolbox_dialog_start_warning_title),
        message = message,
        confirmText = uiTextOf(R.string.toolbox_dialog_start_anyway),
        dismissText = uiTextOf(R.string.common_cancel),
        confirmAction = PanelDialogConfirmAction.CONFIRM_PENDING_START,
    )
}

internal fun Context.createExecutionEndDialog(endState: MaaExecutionState): PanelDialogUiState {
    val message = when (endState) {
        MaaExecutionState.ERROR -> uiTextOf(R.string.task_start_execution_aborted_message)
        else -> uiTextOf(R.string.task_start_execution_finished_message)
    }
    return if (endState == MaaExecutionState.ERROR) {
        PanelDialogUiState(
            type = PanelDialogType.ERROR,
            title = uiTextOf(R.string.task_start_dialog_info_title),
            message = message,
            confirmText = uiTextOf(R.string.task_start_dialog_ack),
            confirmAction = PanelDialogConfirmAction.GO_LOG_AND_STOP,
        )
    } else {
        PanelDialogUiState(
            type = PanelDialogType.SUCCESS,
            title = uiTextOf(R.string.task_start_execution_completed_title),
            message = message,
            confirmText = uiTextOf(R.string.task_start_dialog_view_log),
            confirmAction = PanelDialogConfirmAction.GO_LOG,
        )
    }
}
