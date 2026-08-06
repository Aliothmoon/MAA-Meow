package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf

/** 将 [MaaCompositionService.StartResult] 映射为可读的 i18n 文案；Success 返回 null */
fun resolveStartResultMessage(result: MaaCompositionService.StartResult): UiText? =
    when (result) {
        is MaaCompositionService.StartResult.Success -> null
        is MaaCompositionService.StartResult.ResourceError ->
            uiTextOf(R.string.task_start_error_resource_load_failed)

        is MaaCompositionService.StartResult.InitializationError -> when (result.phase) {
            MaaCompositionService.StartResult.InitializationError.InitPhase.CREATE_INSTANCE ->
                uiTextOf(R.string.task_start_error_maa_create_instance)

            MaaCompositionService.StartResult.InitializationError.InitPhase.SET_TOUCH_MODE ->
                uiTextOf(R.string.task_start_error_set_touch_mode)
        }

        is MaaCompositionService.StartResult.PortraitOrientationError ->
            uiTextOf(R.string.task_start_error_portrait_orientation)

        is MaaCompositionService.StartResult.InvalidAspectRatioError ->
            uiTextOf(R.string.task_start_error_invalid_aspect_ratio)

        is MaaCompositionService.StartResult.ConnectionError -> when (result.phase) {
            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.DISPLAY_MODE ->
                uiTextOf(R.string.task_start_error_display_mode)

            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.VIRTUAL_DISPLAY ->
                uiTextOf(R.string.task_start_error_virtual_display)

            MaaCompositionService.StartResult.ConnectionError.ConnectPhase.MAA_CONNECT ->
                uiTextOf(R.string.task_start_error_connect_timeout)
        }

        is MaaCompositionService.StartResult.StartError ->
            uiTextOf(R.string.task_start_error_start_failed)

        is MaaCompositionService.StartResult.ServiceConnecting ->
            uiTextOf(R.string.task_start_error_service_connecting)

        is MaaCompositionService.StartResult.RemoteAccessUnavailable ->
            uiTextOf(R.string.task_start_error_backend_unavailable, result.backend.display)
    }
