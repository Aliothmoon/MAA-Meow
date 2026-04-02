package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState

internal fun resolveTaskStartFailureMessage(result: MaaCompositionService.StartResult): String? {
    return when (result) {
        is MaaCompositionService.StartResult.Success -> null
        is MaaCompositionService.StartResult.ResourceError -> "资源加载失败，请尝试重新初始化资源"
        is MaaCompositionService.StartResult.InitializationError -> when (result.phase) {
            MaaCompositionService.StartResult.InitializationError.InitPhase.CREATE_INSTANCE ->
                "MaaCore 初始化失败，请重启应用"

            MaaCompositionService.StartResult.InitializationError.InitPhase.SET_TOUCH_MODE ->
                "触控模式设置失败，请重启应用"
        }

        is MaaCompositionService.StartResult.PortraitOrientationError ->
            "当前屏幕为竖屏，请切换为横屏后启动"

        is MaaCompositionService.StartResult.ConnectionError -> when (result.phase) {
            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.DISPLAY_MODE ->
                "显示模式设置失败，请重试"

            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.VIRTUAL_DISPLAY ->
                "虚拟屏幕启动失败，请检查服务权限"

            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.MAA_CONNECT ->
                "连接 MaaCore 超时，请重试"
        }

        is MaaCompositionService.StartResult.StartError ->
            "MaaCore 启动失败，请检查任务配置"
    }
}

internal fun formatStartResult(
    result: MaaCompositionService.StartResult,
    successMessage: String,
): String {
    return resolveTaskStartFailureMessage(result) ?: successMessage
}

internal fun createStartFailedDialog(message: String): PanelDialogUiState {
    return PanelDialogUiState(
        type = PanelDialogType.ERROR,
        title = "启动失败",
        message = message,
        confirmText = "查看日志",
        confirmAction = PanelDialogConfirmAction.GO_LOG,
    )
}
