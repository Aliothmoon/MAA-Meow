package com.aliothmoon.maameow.manager

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class ShizukuReadinessProvider(
    context: Context,
    private val appSettings: AppSettingsManager,
) : DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val refreshTrigger = MutableStateFlow(0)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        refreshTrigger.update { it + 1 }
    }

    val state: StateFlow<ShizukuReadiness> = combine(
        RemoteAccessCoordinator.state,
        appSettings.shizukuLaunchPackage,
        appSettings.skipShizukuCheck,
        refreshTrigger,
    ) { remoteState, launchPackage, skipCheck, _ ->
        resolve(remoteState, launchPackage, skipCheck)
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShizukuReadiness(),
    )

    /**
     * 不看 skipShizukuCheck 的判定，供用户主动点出来的一次性引导使用
     *
     * 冷流：只在引导弹窗开着时才付 checkStatus 的开销
     */
    val unskipped: Flow<ShizukuReadiness> = combine(
        RemoteAccessCoordinator.state,
        appSettings.shizukuLaunchPackage,
        refreshTrigger,
    ) { remoteState, launchPackage, _ ->
        resolve(remoteState, launchPackage, skipCheck = false)
    }

    /** 即时取一次 [unskipped]；先刷新后端状态，免得用到过期快照 */
    suspend fun resolveUnskipped(): ShizukuReadiness = resolve(
        RemoteAccessCoordinator.refresh(),
        appSettings.shizukuLaunchPackage.value,
        skipCheck = false,
    )

    private suspend fun resolve(
        remoteState: RemoteAccessState,
        launchPackage: String,
        skipCheck: Boolean,
    ): ShizukuReadiness {
        val stage = when {
            // 用户已选择跳过：无需展示引导，也不必付出 checkStatus 的 IPC 开销
            skipCheck -> ShizukuReadinessStage.Ready

            remoteState.configuredBackend != RemoteBackend.SHIZUKU ->
                ShizukuReadinessStage.Ready

            remoteState.shizukuGranted ->
                ShizukuReadinessStage.Ready

            remoteState.shizukuAvailable ->
                ShizukuReadinessStage.NeedAuth

            else -> {
                val status = withContext(Dispatchers.IO) {
                    ShizukuInstallHelper.checkStatus(appContext, launchPackage)
                }
                when (status) {
                    // Sui 的服务已在，差授权
                    ShizukuInstallHelper.ShizukuStatus.SUI_AVAILABLE ->
                        ShizukuReadinessStage.NeedAuth

                    ShizukuInstallHelper.ShizukuStatus.APP_NOT_RUNNING ->
                        ShizukuReadinessStage.NotRunning

                    else -> ShizukuReadinessStage.NotInstalled
                }
            }
        }
        return ShizukuReadiness(
            stage = stage,
            canSwitchToRoot = remoteState.rootAvailable,
        )
    }
}
