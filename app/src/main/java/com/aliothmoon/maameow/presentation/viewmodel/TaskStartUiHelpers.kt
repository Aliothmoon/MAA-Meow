package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.resolveStartResultMessage
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.TaskStartAcknowledgement
import com.aliothmoon.maameow.manager.RemoteAccessCoordinator
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogFixAction
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

internal fun Context.createStartWarningDialog(
    message: UiText,
    fixAction: PanelDialogFixAction? = null,
): PanelDialogUiState {
    return PanelDialogUiState(
        type = PanelDialogType.WARNING,
        title = uiTextOf(R.string.toolbox_dialog_start_warning_title),
        message = message,
        confirmText = uiTextOf(R.string.toolbox_dialog_start_anyway),
        dismissText = uiTextOf(R.string.common_cancel),
        confirmAction = PanelDialogConfirmAction.CONFIRM_PENDING_START,
        fixAction = fixAction,
    )
}

internal fun Context.createStartWarningDialog(
    acknowledgement: TaskStartAcknowledgement,
    message: UiText = acknowledgement.message,
): PanelDialogUiState = createStartWarningDialog(
    message,
    startWarningFix(acknowledgement, RemoteAccessCoordinator.configuredBackend()),
)

/** 能就地解决的前置检查给出修复动作，鼓励先改掉再启动 */
internal fun startWarningFix(
    acknowledgement: TaskStartAcknowledgement,
    backend: RemoteBackend,
): PanelDialogFixAction? = when (acknowledgement) {
    TaskStartAcknowledgement.ADB_INPUT_RESTRICTED ->
        if (backend == RemoteBackend.ROOT) PanelDialogFixAction.ENABLE_ADB_INPUT_WITH_ROOT
        else PanelDialogFixAction.OPEN_DEVELOPER_SETTINGS

    TaskStartAcknowledgement.SMART_RESOLUTION_ENABLED,
    TaskStartAcknowledgement.EYE_PROTECTION_ENABLED -> PanelDialogFixAction.OPEN_DISPLAY_SETTINGS

    TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP,
    TaskStartAcknowledgement.GAME_NOT_INSTALLED -> null
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
