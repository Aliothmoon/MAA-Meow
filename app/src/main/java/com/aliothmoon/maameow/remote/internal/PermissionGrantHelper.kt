package com.aliothmoon.maameow.remote.internal

import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.aliothmoon.maameow.third.FakeContext
import com.aliothmoon.maameow.third.Ln

object PermissionGrantHelper {
    private const val TAG = "PermissionGrantHelper"

    // UserHandle.PER_USER_RANGE，隐藏常量
    private const val PER_USER_RANGE = 100_000

    fun grantAccessibilityService(serviceId: String): Boolean {
        return serviceId != "" && try {
            val contentResolver = FakeContext.get().contentResolver
            val existingServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            if (existingServices.contains(serviceId)) {
                Ln.i("$TAG: Accessibility service already enabled: $serviceId")
                return true
            }

            val newServices = if (existingServices.isEmpty()) {
                serviceId
            } else {
                "$existingServices:$serviceId"
            }

            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                newServices
            )
            Settings.Secure.putInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )
            Ln.i("$TAG: Accessibility service enabled: $serviceId")
            true
        } catch (e: Exception) {
            Ln.e("$TAG: Failed to enable accessibility service: $e")
            false
        }
    }


    fun grantFloatingWindowPermission(packageName: String, uid: Int): Boolean =
        allow(packageName, uid, AppOps.SYSTEM_ALERT_WINDOW)


    fun grantNotificationPermission(packageName: String, uid: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return allow(packageName, uid, AppOps.POST_NOTIFICATION)
        }
        // 13+ 通知只认运行时权限，op 由系统按权限同步
        return grantRuntimePermission(packageName, "android.permission.POST_NOTIFICATIONS", uid)
    }

    /** 授完读回，userId 由 uid 推出 */
    private fun grantRuntimePermission(packageName: String, permission: String, uid: Int): Boolean {
        val userId = uid / PER_USER_RANGE
        return runCatching {
            RemoteUtils.packageManager.grantRuntimePermission(packageName, permission, userId)
            RemoteUtils.packageManager.checkPermission(permission, packageName, userId) ==
                    PackageManager.PERMISSION_GRANTED
        }.onFailure { Ln.w("$TAG: grant $permission failed for $packageName: $it") }
            .getOrDefault(false)
    }


    fun grantBatteryOptimizationExemption(packageName: String): Boolean {
        return try {
            RemoteUtils.deviceIdleController.addPowerSaveWhitelistApp(packageName)
            Ln.i("$TAG: Battery optimization exemption granted for $packageName")
            true
        } catch (e: Exception) {
            Ln.e("$TAG: Failed to grant battery optimization exemption: $e")
            false
        }
    }


    fun grantBackgroundUnrestricted(packageName: String, uid: Int): Boolean {
        val opsAllowed =
            allow(packageName, uid, AppOps.RUN_IN_BACKGROUND, AppOps.RUN_ANY_IN_BACKGROUND)
        return try {
            RemoteUtils.shellExec("am set-standby-bucket $packageName active")
            RemoteUtils.shellExec("am set-inactive $packageName false")

            // set-bg-restriction-level 仅 Android 14+ 可用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                RemoteUtils.shellExec("am set-bg-restriction-level $packageName unrestricted")
            }

            disablePhantomProcessKiller()

            Ln.i("$TAG: Background unrestricted for $packageName")
            opsAllowed
        } catch (e: Exception) {
            Ln.e("$TAG: Failed to grant background unrestricted: $e")
            false
        }
    }

    /** specialUse FGS 的 appop 可能被 ROM 或管控工具拒绝，任务 FGS 会因此起不来 */
    fun grantForegroundServiceSpecialUse(packageName: String, uid: Int) {
        allow(packageName, uid, AppOps.FOREGROUND_SERVICE_SPECIAL_USE)
    }

    fun grantScheduleOps(packageName: String, uid: Int) {
        allow(packageName, uid, *AppOps.SCHEDULE)
    }

    /** 本机没有的 op 跳过，不算失败 */
    private fun allow(packageName: String, uid: Int, vararg ops: AppOp): Boolean =
        ops.filter { it.code != null }
            .map { AppOpsHelper.set(it, packageName, uid, AppOpsManager.MODE_ALLOWED) }
            .all { it }

    /**
     * 禁用 Android 12+ 的 Phantom Process Killer
     */
    fun disablePhantomProcessKiller(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || try {
            // Android 12L & 13+
            RemoteUtils.shellExec("device_config put activity_manager max_phantom_processes 2147483647")
            // Android 12 Beta
            RemoteUtils.shellExec("settings put global settings_config_disable_monitor_phantom_procs true")
            // Android 11+
            RemoteUtils.shellExec("settings put global phantom_process_killer_enable false")
            Ln.i("$TAG: Phantom process killer disabled")
            true
        } catch (e: Exception) {
            Ln.e("$TAG: Failed to disable phantom process killer: $e")
            false
        }
    }

    fun grantStoragePermission(packageName: String, uid: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return allow(packageName, uid, AppOps.MANAGE_EXTERNAL_STORAGE)
        }
        val granted = listOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
        ).map { grantRuntimePermission(packageName, it, uid) }.all { it }
        if (granted) Ln.i("$TAG: Storage permission granted for $packageName")
        return granted
    }
}
