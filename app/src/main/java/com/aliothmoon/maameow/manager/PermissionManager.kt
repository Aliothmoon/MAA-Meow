package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.data.permission.PermissionState
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.remote.PermissionGrantRequest
import com.aliothmoon.maameow.remote.PermissionGrantRequest.Companion.PERM_ACCESSIBILITY
import com.aliothmoon.maameow.service.AccessibilityHelperService
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

class PermissionManager(
    private val context: Context,
    private val appSettings: AppSettingsManager,
    private val achievementReporter: AchievementReporter,
) : DefaultLifecycleObserver {

    private val _state = MutableStateFlow(PermissionState())
    val state: StateFlow<PermissionState> = _state.asStateFlow()

    val permissions: PermissionState
        get() = state.value

    private val _isGranting = MutableStateFlow(false)
    val isGranting: StateFlow<Boolean> = _isGranting.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            RemoteAccessCoordinator.state.collect { remoteState ->
                applyRemoteAccessState(remoteState)
            }
        }
        scope.launch {
            state.map { it.startupBackend to it.remoteAccessGranted }
                .distinctUntilChanged()
                .filter { (_, granted) -> granted }
                .collect {
                    RemoteServiceManager.bind()
                }
        }

        refresh()
    }

    override fun onResume(owner: LifecycleOwner) {
        refresh()
    }

    fun refresh() {
        val remoteState = RemoteAccessCoordinator.refresh()
        updateState(
            PermissionState(
                shizukuAvailable = remoteState.shizukuAvailable,
                shizuku = remoteState.shizukuGranted,
                root = remoteState.rootGranted,
                rootAvailable = remoteState.rootAvailable,
                startupBackend = remoteState.configuredBackend,
                overlay = checkOverlay(),
                storage = checkStorage(),
                accessibility = checkAccessibility(),
                batteryWhitelist = checkBatteryWhitelist(),
                notification = checkNotification(),
            )
        )
    }

    private fun applyRemoteAccessState(remoteState: RemoteAccessState) {
        updateState(
            _state.value.copy(
                shizukuAvailable = remoteState.shizukuAvailable,
                shizuku = remoteState.shizukuGranted,
                root = remoteState.rootGranted,
                rootAvailable = remoteState.rootAvailable,
                startupBackend = remoteState.configuredBackend
            )
        )
    }

    private fun updateState(state: PermissionState) {
        _state.value = state
        achievementReporter.reportPermissionState(state)
    }

    private fun checkOverlay(): Boolean {
        return try {
            XXPermissions.isGrantedPermission(
                context,
                PermissionLists.getSystemAlertWindowPermission()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking overlay permission")
            false
        }
    }

    private fun checkStorage(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                XXPermissions.isGrantedPermission(
                    context,
                    PermissionLists.getManageExternalStoragePermission()
                )
            } else {
                XXPermissions.isGrantedPermission(
                    context,
                    PermissionLists.getReadExternalStoragePermission()
                ) && XXPermissions.isGrantedPermission(
                    context,
                    PermissionLists.getWriteExternalStoragePermission()
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking storage permission")
            false
        }
    }

    private fun checkAccessibility(): Boolean {
        return try {
            XXPermissions.isGrantedPermission(
                context,
                PermissionLists.getBindAccessibilityServicePermission(
                    AccessibilityHelperService::class.java
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking accessibility permission")
            false
        }
    }

    private fun checkBatteryWhitelist(): Boolean {
        return try {
            XXPermissions.isGrantedPermission(
                context,
                PermissionLists.getRequestIgnoreBatteryOptimizationsPermission()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking battery whitelist permission")
            false
        }
    }

    private fun checkNotification(): Boolean {
        return try {
            XXPermissions.isGrantedPermission(
                context,
                PermissionLists.getPostNotificationsPermission()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking notification permission")
            false
        }
    }

    suspend fun requestShizuku(): Boolean {
        if (!RemoteAccessCoordinator.isAvailable(RemoteBackend.SHIZUKU)) {
            refresh()
            return false
        }

        val granted = RemoteAccessCoordinator.request(RemoteBackend.SHIZUKU)
        refresh()
        return granted
    }

    suspend fun requestRoot(): Boolean {
        val granted = RemoteAccessCoordinator.request(RemoteBackend.ROOT)
        refresh()
        return granted
    }

    suspend fun requestRemoteAccess(): Boolean {
        refresh()
        if (permissions.remoteAccessGranted) {
            return true
        }
        val granted = RemoteAccessCoordinator.request(permissions.startupBackend)
        refresh()
        return granted
    }

    suspend fun setStartupBackend(backend: RemoteBackend) {
        val currentBackend = permissions.startupBackend
        if (currentBackend == backend) {
            refresh()
            return
        }
        appSettings.setStartupBackend(backend)
        RemoteServiceManager.unbind()
        refresh()
    }

    suspend fun requestOverlay(context: Context): Boolean {
        if (checkOverlay()) {
            _state.update { it.copy(overlay = true) }
            return true
        }

        val granted = suspendCancellableCoroutine { cont ->
            XXPermissions.with(context)
                .permission(PermissionLists.getSystemAlertWindowPermission())
                .request { grantedList, _ ->
                    cont.resume(grantedList.isNotEmpty())
                }
        }

        _state.update { it.copy(overlay = granted) }
        return granted
    }

    suspend fun requestStorage(context: Context): Boolean {
        if (checkStorage()) {
            _state.update { it.copy(storage = true) }
            return true
        }

        val granted = suspendCancellableCoroutine { cont ->
            XXPermissions.with(context)
                .permission(PermissionLists.getManageExternalStoragePermission())
                .request { grantedList, _ ->
                    cont.resume(grantedList.isNotEmpty())
                }
        }

        _state.update { it.copy(storage = granted) }
        return granted
    }

    suspend fun requestBatteryWhitelist(context: Context): Boolean {
        if (checkBatteryWhitelist()) {
            _state.update { it.copy(batteryWhitelist = true) }
            return true
        }

        val granted = suspendCancellableCoroutine { cont ->
            XXPermissions.with(context)
                .permission(PermissionLists.getRequestIgnoreBatteryOptimizationsPermission())
                .request { grantedList, _ ->
                    cont.resume(grantedList.isNotEmpty())
                }
        }

        _state.update { it.copy(batteryWhitelist = granted) }
        return granted
    }

    suspend fun requestNotification(context: Context): Boolean {
        if (checkNotification()) {
            _state.update { it.copy(notification = true) }
            return true
        }

        val granted = suspendCancellableCoroutine { cont ->
            XXPermissions.with(context)
                .permission(PermissionLists.getPostNotificationsPermission())
                .request { grantedList, _ ->
                    cont.resume(grantedList.isNotEmpty())
                }
        }

        _state.update { it.copy(notification = granted) }
        achievementReporter.reportNotificationPermissionResult(granted)
        return granted
    }

    suspend fun requestAccessibility(context: Context): Boolean {
        if (checkAccessibility()) {
            _state.update { it.copy(accessibility = true) }
            return true
        }

        val granted = suspendCancellableCoroutine { cont ->
            XXPermissions.with(context)
                .permission(
                    PermissionLists.getBindAccessibilityServicePermission(
                        AccessibilityHelperService::class.java
                    )
                )
                .request { grantedList, _ ->
                    cont.resume(grantedList.isNotEmpty())
                }
        }

        _state.update { it.copy(accessibility = granted) }
        return granted
    }

    suspend fun quickGrantAccessibility(): Boolean {
        if (!permissions.remoteAccessGranted) return false
        return try {
            val result = RemoteServiceManager.useRemoteService { srv ->
                srv.grantPermissions(
                    PermissionGrantRequest(
                        packageName = context.packageName,
                        uid = context.applicationInfo.uid,
                        accessibilityServiceId = AccessibilityHelperService.SERVICE_ID,
                        permissions = PERM_ACCESSIBILITY
                    )
                )
            }
            if (result.accessibilityPermission) {
                withTimeoutOrNull(3000L) {
                    AccessibilityHelperService.isConnected
                        .filter { it }
                        .first()
                }
                refresh()
            }
            result.accessibilityPermission
        } catch (e: Exception) {
            Timber.e(e, "quickGrantAccessibility failed")
            false
        }
    }

    suspend fun grantRequiredPermissions(srv: RemoteService) {
        if (!permissions.remoteAccessGranted) {
            Timber.w("grantAll: remote permission not granted")
            return
        }

        _isGranting.value = true
        try {
            val packageName = context.packageName
            val uid = context.applicationInfo.uid
            val isForeground = appSettings.runMode.value == RunMode.FOREGROUND

            Timber.i("grantAll: packageName=$packageName, uid=$uid, isForeground=$isForeground")

            val result = srv.grantPermissions(
                PermissionGrantRequest(
                    packageName = packageName,
                    uid = uid,
                    accessibilityServiceId = if (isForeground)
                        AccessibilityHelperService.SERVICE_ID else ""
                )
            )

            if (isForeground && result.accessibilityPermission) {
                withTimeoutOrNull(3000L) {
                    AccessibilityHelperService.isConnected
                        .filter { it }
                        .first()
                }
            }

            Timber.i("grantAll result: $result")
            refresh()
        } catch (e: Exception) {
            Timber.e(e, "grantAll failed")
        } finally {
            _isGranting.value = false
        }
    }
}
