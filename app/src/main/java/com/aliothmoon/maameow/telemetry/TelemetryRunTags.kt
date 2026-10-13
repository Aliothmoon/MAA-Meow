package com.aliothmoon.maameow.telemetry

import android.content.Context
import android.os.PowerManager
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.ScreenPowerController
import com.aliothmoon.maameow.manager.RemoteAccessCoordinator
import com.aliothmoon.maameow.manager.ShizukuManager
import rikka.shizuku.Shizuku

/**
 * 会随用户设置或设备状态变的运行环境，每轮开跑时重读
 *
 * 读资源版本与查 Shizuku 要碰盘和 binder，别在主线程调
 */
internal object TelemetryRunTags {

    private const val UNKNOWN = "unknown"

    fun collect(
        context: Context,
        settings: AppSettingsManager,
        taskChainState: TaskChainState,
        pathConfig: MaaPathConfig,
        screenPowerOff: ScreenPowerController.Origin?,
    ): Map<String, String> = buildMap {
        val clientType = taskChainState.clientType
        put("client_type", clientType)
        put("run_mode", settings.runMode.value.name.lowercase())
        put("backend", RemoteAccessCoordinator.configuredBackend().name.lowercase())
        put("core_location", pathConfig.coreLocation.name.lowercase())
        pathConfig.readDiskResourceVersion()?.let { put("resource.version", it) }
        put("background_resolution", settings.backgroundResolution.value.name.lowercase())
        put("game.version", gameVersion(context, clientType))
        put("shizuku.identity", shizukuIdentity())
        put("battery_optimization", batteryOptimization(context))
        // 会改变运行行为的设置；开了任务覆盖的识别失败多半是用户自己改的 tasks.json 所致
        put("tasks_override", settings.tasksOverrideEnabled.value.toString())
        put("force_fullscreen", settings.forceFullscreenOnVirtualDisplay.value.toString())
        put("deploy_with_pause", settings.deployWithPause.value.toString())
        put("hardware_screen_off", settings.useHardwareScreenOff.value.toString())
        // 定时策略的硬件熄屏不看全局开关，另记开跑时屏是否被关着
        put("screen_power_off", screenPowerOff?.name?.lowercase() ?: "none")
    }

    private fun gameVersion(context: Context, clientType: String): String {
        val packageName = Packages[clientType] ?: return UNKNOWN
        return runCatching { context.packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrElse { return "not_installed" } ?: UNKNOWN
    }

    private fun shizukuIdentity(): String = runCatching {
        val available = ShizukuManager.isShizukuAvailable()
        shizukuIdentity(
            available = available,
            granted = available && ShizukuManager.isGranted(),
            uid = if (available) runCatching { Shizuku.getUid() }.getOrNull() else null,
        )
    }.getOrDefault(UNKNOWN)

    /** 同是 Shizuku，以 root 起的和以 adb 起的能做的事不一样，连接与虚拟显示器的问题常要靠它分 */
    internal fun shizukuIdentity(available: Boolean, granted: Boolean, uid: Int?): String = when {
        !available -> "unavailable"
        !granted -> "not_granted"
        uid == null -> UNKNOWN
        uid == 0 -> "root"
        else -> "adb"
    }

    private fun batteryOptimization(context: Context): String = runCatching {
        val powerManager = context.getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) "exempt" else "restricted"
    }.getOrDefault(UNKNOWN)
}
