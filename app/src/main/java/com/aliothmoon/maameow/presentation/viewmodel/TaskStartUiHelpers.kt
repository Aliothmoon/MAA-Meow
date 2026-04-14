package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.TaskStartDecision
import com.aliothmoon.maameow.domain.usecase.TaskStartDecisionReason
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextDynamic
import com.aliothmoon.maameow.utils.i18n.uiTextJoin
import com.aliothmoon.maameow.utils.i18n.uiTextOf

internal fun Context.resolveGameNotRunningWarningMessage(): UiText =
    uiTextOf(R.string.task_start_warning_game_not_running)

internal fun Context.resolveTaskStartDecisionMessage(decision: TaskStartDecision): UiText {
    val conflictingTypes = when (decision) {
        is TaskStartDecision.Blocked -> uiTextJoin(
            *decision.clientTypes.map(::uiTextDynamic).toTypedArray(),
            separator = uiTextOf(R.string.common_enumeration_separator)
        )

        is TaskStartDecision.RequiresConfirmation -> uiTextJoin(
            *decision.clientTypes.map(::uiTextDynamic).toTypedArray(),
            separator = uiTextOf(R.string.common_enumeration_separator)
        )

        is TaskStartDecision.Ready -> UiText.Empty
    }
    return when (decision) {
        is TaskStartDecision.Ready -> UiText.Empty

        is TaskStartDecision.RequiresConfirmation -> when (decision.reason) {
            TaskStartDecisionReason.NO_TASK_SELECTED -> {
                uiTextOf(R.string.task_start_error_no_task_selected)
            }

            TaskStartDecisionReason.CONFLICTING_CLIENT_TYPES -> {
                uiTextOf(R.string.task_start_error_conflicting_client_types, conflictingTypes)
            }

            TaskStartDecisionReason.NO_EXECUTABLE_TASKS -> {
                uiTextOf(R.string.task_start_error_no_executable_tasks)
            }

            TaskStartDecisionReason.GAME_NOT_RUNNING_WITHOUT_WAKE_UP -> {
                uiTextOf(R.string.task_start_warning_game_not_running)
            }

            TaskStartDecisionReason.GAME_NOT_INSTALLED -> {
                uiTextOf(R.string.task_start_warning_game_not_installed)
            }
        }

        is TaskStartDecision.Blocked -> when (decision.reason) {
            TaskStartDecisionReason.NO_TASK_SELECTED -> {
                uiTextOf(R.string.task_start_error_no_task_selected)
            }

            TaskStartDecisionReason.CONFLICTING_CLIENT_TYPES -> {
                uiTextOf(R.string.task_start_error_conflicting_client_types, conflictingTypes)
            }

            TaskStartDecisionReason.NO_EXECUTABLE_TASKS -> {
                uiTextOf(R.string.task_start_error_no_executable_tasks)
            }

            TaskStartDecisionReason.GAME_NOT_RUNNING_WITHOUT_WAKE_UP -> {
                uiTextOf(R.string.task_start_error_scheduled_no_wakeup)
            }

            TaskStartDecisionReason.GAME_NOT_INSTALLED -> {
                uiTextOf(R.string.task_start_error_scheduled_game_not_installed)
            }
        }
    }
}

internal fun Context.resolveTaskStartFailureMessage(result: MaaCompositionService.StartResult): UiText? {
    return when (result) {
        is MaaCompositionService.StartResult.Success -> null
        is MaaCompositionService.StartResult.ResourceError -> {
            uiTextOf(R.string.task_start_error_resource_load_failed)
        }

        is MaaCompositionService.StartResult.InitializationError -> when (result.phase) {
            MaaCompositionService.StartResult.InitializationError.InitPhase.CREATE_INSTANCE -> {
                uiTextOf(R.string.task_start_error_maa_create_instance)
            }

            MaaCompositionService.StartResult.InitializationError.InitPhase.SET_TOUCH_MODE -> {
                uiTextOf(R.string.task_start_error_set_touch_mode)
            }
        }

        is MaaCompositionService.StartResult.PortraitOrientationError -> {
            uiTextOf(R.string.task_start_error_portrait_orientation)
        }

        is MaaCompositionService.StartResult.ConnectionError -> when (result.phase) {
            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.DISPLAY_MODE -> {
                uiTextOf(R.string.task_start_error_display_mode)
            }

            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.VIRTUAL_DISPLAY -> {
                uiTextOf(R.string.task_start_error_virtual_display)
            }

            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.MAA_CONNECT -> {
                uiTextOf(R.string.task_start_error_connect_timeout)
            }
        }

        is MaaCompositionService.StartResult.StartError -> {
            uiTextOf(R.string.task_start_error_start_failed)
        }
    }
}

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
