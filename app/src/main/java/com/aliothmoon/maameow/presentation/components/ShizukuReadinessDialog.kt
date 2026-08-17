package com.aliothmoon.maameow.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.manager.ShizukuReadiness
import com.aliothmoon.maameow.manager.ShizukuReadinessStage

/**
 * Shizuku 就绪引导弹窗（统一组件）
 *
 * 纯展示：根据 [ShizukuReadiness.stage] 渲染对应的标题 / 消息 / 按钮，
 * 所有动作通过回调上抛，自身无状态、无业务逻辑（遵循 State Hoisting）
 *
 * 当 [ShizukuReadiness.needsGuidance] 为 false 时不渲染
 *
 * @param onInstall      未安装时的安装动作
 * @param onOpenApp      未运行时的打开 Shizuku动作
 * @param onRequestAuth  运行中未授权时的立即授权”动作
 * @param onDismiss      用户选择跳过检查或知道了”——不再提醒
 * @param onSwitchToRoot 切换至 Root出口（仅 canSwitchToRoot 时展示对应按钮）
 * @param isRequesting   授权请求进行中（由调用方管理，按钮文案随之变化）
 */
@Composable
fun ShizukuReadinessDialog(
    readiness: ShizukuReadiness,
    onInstall: () -> Unit,
    onOpenApp: () -> Unit,
    onRequestAuth: () -> Unit,
    onDismiss: () -> Unit,
    onSwitchToRoot: () -> Unit,
    isRequesting: Boolean = false,
    dismissText: String? = null,
) {
    if (!readiness.needsGuidance) return

    val switchToRootText = if (readiness.canSwitchToRoot)
        stringResource(R.string.dialog_shizuku_switch_to_root) else null
    val skipText = dismissText ?: stringResource(R.string.dialog_shizuku_skip_check)

    when (readiness.stage) {
        ShizukuReadinessStage.NotInstalled -> {
            AdaptiveTaskPromptDialog(
                visible = true,
                title = stringResource(R.string.dialog_shizuku_not_installed_title),
                message = stringResource(R.string.dialog_shizuku_not_installed_message),
                icon = Icons.Rounded.Warning,
                confirmText = stringResource(R.string.dialog_shizuku_install_confirm),
                onConfirm = onInstall,
                neutralText = switchToRootText,
                onNeutralClick = onSwitchToRoot,
                dismissText = skipText,
                onDismissRequest = onDismiss,
                dismissOnOutsideClick = false,
            )
        }

        ShizukuReadinessStage.NotRunning -> {
            AdaptiveTaskPromptDialog(
                visible = true,
                title = stringResource(R.string.dialog_shizuku_not_running_title),
                message = stringResource(R.string.dialog_shizuku_not_running_message),
                icon = Icons.Rounded.Build,
                confirmText = stringResource(R.string.dialog_shizuku_open_app),
                onConfirm = onOpenApp,
                neutralText = switchToRootText,
                onNeutralClick = onSwitchToRoot,
                dismissText = skipText,
                onDismissRequest = onDismiss,
                dismissOnOutsideClick = false,
            )
        }

        ShizukuReadinessStage.SuiAvailable -> {
            AdaptiveTaskPromptDialog(
                visible = true,
                title = stringResource(R.string.dialog_sui_detected_title),
                message = stringResource(R.string.dialog_sui_detected_message),
                icon = Icons.Rounded.Info,
                confirmText = stringResource(R.string.common_got_it),
                onConfirm = onDismiss,
                dismissText = null,
                onDismissRequest = onDismiss,
                dismissOnOutsideClick = false,
            )
        }

        ShizukuReadinessStage.NeedAuth -> {
            AdaptiveTaskPromptDialog(
                visible = true,
                title = stringResource(R.string.dialog_shizuku_need_auth_title),
                message = stringResource(R.string.dialog_shizuku_need_auth_message),
                icon = Icons.Rounded.Build,
                confirmText = if (isRequesting)
                    stringResource(R.string.shizuku_auth_requesting)
                else stringResource(R.string.shizuku_auth_grant_now),
                onConfirm = onRequestAuth,
                neutralText = switchToRootText,
                onNeutralClick = onSwitchToRoot,
                dismissText = skipText,
                onDismissRequest = onDismiss,
                dismissOnOutsideClick = false,
            )
        }

        ShizukuReadinessStage.Ready -> {}
    }
}
