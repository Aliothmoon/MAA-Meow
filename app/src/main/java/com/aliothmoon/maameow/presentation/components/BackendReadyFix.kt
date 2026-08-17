package com.aliothmoon.maameow.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.ShizukuReadinessProvider
import com.aliothmoon.maameow.presentation.LocalToaster
import com.dokar.sonner.ToastType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Shizuku/Root 授权「去修复」的统一实现，供健康卡与触发日志共用
 *
 * 与 [ShizukuReadinessGate] 只差关闭语义：那个是常驻引导，关掉即写 skipShizukuCheck
 * 全局不再提醒；这里是主动点出来的一次性弹窗，关掉不动全局设置
 *
 * 用法：[rememberBackendReadyFixState] 拿状态，[BackendReadyFixHost] 渲染
 */
@Stable
class BackendReadyFixState internal constructor(
    private val permissionManager: PermissionManager,
    private val readinessProvider: ShizukuReadinessProvider,
    private val scope: CoroutineScope,
) {
    internal var showDialog by mutableStateOf(false)
        private set

    /** 置位后由 Host 弹提示并清零 */
    internal var stillNotReady by mutableStateOf(false)

    fun request() {
        if (readinessProvider.state.value.needsGuidance) {
            showDialog = true
        } else {
            // 引导覆盖不到时（勾了跳过检查、Root 后端、状态过期）直接请求，别静默空操作
            scope.launch {
                if (!permissionManager.requestRemoteAccess()) stillNotReady = true
            }
        }
    }

    internal fun dismiss() {
        showDialog = false
    }
}

/**
 * 只造状态、不发射 UI
 *
 * 刻意不把 toaster / 文案捕获进状态对象：语言或主题一变 remember 就重建，弹窗状态会丢
 */
@Composable
fun rememberBackendReadyFixState(
    permissionManager: PermissionManager = koinInject(),
    readinessProvider: ShizukuReadinessProvider = koinInject(),
): BackendReadyFixState {
    val scope = rememberCoroutineScope()
    return remember(permissionManager, readinessProvider, scope) {
        BackendReadyFixState(permissionManager, readinessProvider, scope)
    }
}

/** 未触发或已就绪时不渲染任何东西 */
@Composable
fun BackendReadyFixHost(
    state: BackendReadyFixState,
    permissionManager: PermissionManager = koinInject(),
    readinessProvider: ShizukuReadinessProvider = koinInject(),
) {
    val toaster = LocalToaster.current
    val stillNotReadyText = stringResource(
        R.string.schedule_backend_fix_still_not_ready,
        permissionManager.permissions.startupBackend.display,
    )
    LaunchedEffect(state.stillNotReady) {
        if (state.stillNotReady) {
            toaster.show(stillNotReadyText, type = ToastType.Error)
            state.stillNotReady = false
        }
    }

    if (!state.showDialog) return

    // 授权成功或切到 Root 后自行收起
    val readiness by readinessProvider.state.collectAsStateWithLifecycle()
    LaunchedEffect(readiness.needsGuidance) {
        if (!readiness.needsGuidance) state.dismiss()
    }

    ShizukuReadinessGate(
        onDismiss = { state.dismiss() },
        dismissText = stringResource(R.string.common_later),
    )
}
