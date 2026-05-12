package com.aliothmoon.maameow.remote.internal

import android.os.Build
import android.provider.Settings
import com.aliothmoon.maameow.third.FakeContext
import com.aliothmoon.maameow.third.Ln

object PermissionGrantHelper {
    private const val TAG = "PermissionGrantHelper"

    fun grantAccessibilityService(serviceId: String): Boolean {
        if (serviceId == "") {
            return false
        }
        return try {
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


    fun grantFloatingWindowPermission(packageName: String, uid: Int): Boolean {
        return try {
            // OP_SYSTEM_ALERT_WINDOW = 24
            RemoteUtils.appOpsService.setMode(24, uid, packageName, 0) // MODE_ALLOWED = 0
            Ln.i("$TAG: Floating window permission granted for $packageName")
            true
        } catch (e: Exception) {
            Ln.e("$TAG: Failed to grant floating window permission: $e")
            false
        }
    }


    fun grantNotificationPermission(packageName: String, uid: Int): Boolean {
        return try {
            // OP_POST_NOTIFICATION = 11
            RemoteUtils.appOpsService.setMode(11, uid, packageName, 0) // MODE_ALLOWED = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching {
                    RemoteUtils.packageManager.grantRuntimePermission(
                        packageName,
                        "android.permission.POST_NOTIFICATIONS",
                        0
                    )
                }.onFailure {
                    Ln.w("$TAG: Failed to grant POST_NOTIFICATIONS runtime permission: $it")
                }
            }

            Ln.i("$TAG: Notification permission granted for $packageName")
            true
        } catch (e: Exception) {
            Ln.e("$TAG: Failed to grant notification permission: $e")
            false
        }
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
        return try {
            // OP_RUN_IN_BACKGROUND = 63, MODE_ALLOWED = 0
            RemoteUtils.appOpsService.setMode(63, uid, packageName, 0)
            // OP_RUN_ANY_IN_BACKGROUND = 65, MODE_ALLOWED = 0
            RemoteUtils.appOpsService.setMode(65, uid, packageName, 0)

            RemoteUtils.shellExec("am set-standby-bucket $packageName active")
            RemoteUtils.shellExec("am set-inactive $packageName false")

            // set-bg-restriction-level 仅 Android 14+ 可用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                RemoteUtils.shellExec("am set-bg-restriction-level $packageName unrestricted")
            }

            disablePhantomProcessKiller()

            Ln.i("$TAG: Background unrestricted for $packageName")
            true
        } catch (e: Exception) {
            Ln.e("$TAG: Failed to grant background unrestricted: $e")
            false
        }
    }

    /**
     * 禁用 Android 12+ 的 Phantom Process Killer
     */
    fun disablePhantomProcessKiller(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        return try {
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
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                RemoteUtils.appOpsService.setMode(92, uid, packageName, 0) // MODE_ALLOWED = 0
                Ln.i("$TAG: MANAGE_EXTERNAL_STORAGE granted for $packageName via AppOps")
            } else {
                RemoteUtils.packageManager.grantRuntimePermission(
                    packageName,
                    "android.permission.READ_EXTERNAL_STORAGE",
                    0
                )
                RemoteUtils.packageManager.grantRuntimePermission(
                    packageName,
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    0
                )
                Ln.i("$TAG: Storage permission granted for $packageName")
            }
            true
        } catch (e: Exception) {
            Ln.e("$TAG: Failed to grant storage permission: $e", e)
            false
        }
    }
}
