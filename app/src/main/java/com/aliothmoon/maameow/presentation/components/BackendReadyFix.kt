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
import com.aliothmoon.maameow.manager.ShizukuReadiness
import com.aliothmoon.maameow.manager.ShizukuReadinessProvider
import com.aliothmoon.maameow.manager.ShizukuReadinessStage
import com.aliothmoon.maameow.presentation.LocalToaster
import com.dokar.sonner.ToastType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Shizuku/Root 没就绪时「去修复」的统一实现，供任务页启动、首页请求权限、健康卡与触发日志共用
 *
 * 与 [ShizukuReadinessGate] 的差别：那个是常驻引导，关掉即写 skipShizukuCheck 全局不再提醒；
 * 这里是用户主动点出来的一次性弹窗，勾了跳过检查也照样弹，关掉不动全局设置
 *
 * 用法：[rememberBackendReadyFixState] 拿状态，[BackendReadyFixHost] 渲染
 */
@Stable
class BackendReadyFixState internal constructor(
    private val permissionManager: PermissionManager,
    private val readinessProvider: ShizukuReadinessProvider,
    private val scope: CoroutineScope,
) {
    /** 非空即弹窗开着，值是点出来那一刻的判定，之后由 Host 跟进 */
    internal var guidance by mutableStateOf<ShizukuReadiness?>(null)
        private set

    /** 置位后由 Host 弹提示并清零 */
    internal var stillNotReady by mutableStateOf(false)

    /** @param authDirectly 差授权时直接请求不弹引导，给本身就写着「请求权限」的入口，免得多点一次 */
    fun request(authDirectly: Boolean = false) {
        scope.launch {
            val readiness = readinessProvider.resolveUnskipped()
            val guide = readiness.fixable &&
                    !(authDirectly && readiness.stage == ShizukuReadinessStage.NeedAuth)
            if (guide) {
                guidance = readiness
            } else if (!permissionManager.requestRemoteAccess()) {
                // 引导覆盖不到时（Root 后端）直接请求，别静默空操作
                stillNotReady = true
            }
        }
    }

    internal fun dismiss() {
        guidance = null
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

    val initial = state.guidance ?: return

    // 弹窗开着才收集；授权成功或切到 Root 后自行收起
    val readiness by readinessProvider.unskipped.collectAsStateWithLifecycle(initialValue = initial)
    LaunchedEffect(readiness.fixable) {
        if (!readiness.fixable) state.dismiss()
    }

    ShizukuReadinessGuide(
        readiness = readiness,
        onDismiss = { state.dismiss() },
        dismissText = stringResource(R.string.common_later),
    )
}
