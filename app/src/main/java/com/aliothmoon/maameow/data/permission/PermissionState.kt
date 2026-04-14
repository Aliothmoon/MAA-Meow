package com.aliothmoon.maameow.data.permission

import com.aliothmoon.maameow.domain.models.RemoteBackend

/**
 * 权限状态数据类
 */
data class PermissionState(
    val shizukuAvailable: Boolean = false,
    val shizuku: Boolean = false,
    val root: Boolean = false,
    val rootAvailable: Boolean = false,
    val startupBackend: RemoteBackend = RemoteBackend.SHIZUKU,
    val overlay: Boolean = false,
    val storage: Boolean = false,
    val accessibility: Boolean = false,
    val batteryWhitelist: Boolean = false,
    val notification: Boolean = false
) {
    fun isStartupBackendAvailable(backend: RemoteBackend): Boolean {
        return when (backend) {
            RemoteBackend.SHIZUKU -> shizukuAvailable
            RemoteBackend.ROOT -> rootAvailable
        }
    }

    val remoteAccessGranted: Boolean
        get() = when (startupBackend) {
            RemoteBackend.SHIZUKU -> shizuku
            RemoteBackend.ROOT -> root
        }
}
