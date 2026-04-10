package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState

internal fun Context.gameNotRunningWarning(): String =
    getString(R.string.game_not_running_warning)

internal fun Context.resolveTaskStartFailureMessage(result: MaaCompositionService.StartResult): String? {
    return when (result) {
        is MaaCompositionService.StartResult.Success -> null
        is MaaCompositionService.StartResult.ResourceError ->
            getString(R.string.resource_load_failed_msg)
        is MaaCompositionService.StartResult.InitializationError -> when (result.phase) {
            MaaCompositionService.StartResult.InitializationError.InitPhase.CREATE_INSTANCE ->
                getString(R.string.maacore_init_failed)
            MaaCompositionService.StartResult.InitializationError.InitPhase.SET_TOUCH_MODE ->
                getString(R.string.touch_mode_failed)
        }
        is MaaCompositionService.StartResult.PortraitOrientationError ->
            getString(R.string.portrait_orientation_error)
        is MaaCompositionService.StartResult.ConnectionError -> when (result.phase) {
            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.DISPLAY_MODE ->
                getString(R.string.display_mode_failed)
            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.VIRTUAL_DISPLAY ->
                getString(R.string.virtual_display_failed)
            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.MAA_CONNECT ->
                getString(R.string.maacore_connect_timeout)
        }
        is MaaCompositionService.StartResult.StartError ->
            getString(R.string.maacore_start_failed)
    }
}

internal fun Context.formatStartResult(
    result: MaaCompositionService.StartResult,
    successMessage: String,
): String {
    return resolveTaskStartFailureMessage(result) ?: successMessage
}

internal fun Context.createStartFailedDialog(message: String): PanelDialogUiState {
    return PanelDialogUiState(
        type = PanelDialogType.ERROR,
        title = getString(R.string.launch_failed_title),
        message = message,
        confirmText = getString(R.string.view_log),
        confirmAction = PanelDialogConfirmAction.GO_LOG,
    )
}
