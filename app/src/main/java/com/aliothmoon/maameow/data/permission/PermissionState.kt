package com.aliothmoon.maameow.data.permission

/**
 * 权限状态数据类
 */
data class PermissionState(
    val shizuku: Boolean = false,
    val overlay: Boolean = false,
    val storage: Boolean = false,
    val accessibility: Boolean = false,
    val batteryWhitelist: Boolean = false,
    val notification: Boolean = false
)
