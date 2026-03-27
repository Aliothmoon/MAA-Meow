package com.aliothmoon.maameow.remote

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PermissionStateInfo(
    val floatingWindowPermission: Boolean = false,
    val storagePermission: Boolean = false,
    val batteryOptimizationExempt: Boolean = false,
    val accessibilityPermission: Boolean = false,
    val notificationPermission: Boolean = false,
    val backgroundUnrestricted: Boolean = false
) : Parcelable
