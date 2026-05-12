package com.aliothmoon.maameow.remote

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PermissionGrantRequest(
    val packageName: String,
    val uid: Int = 0,
    val accessibilityServiceId: String = "",
    val permissions: Int = PERM_ALL
) : Parcelable {
    companion object {
        const val PERM_FLOATING_WINDOW = 1 shl 0
        const val PERM_STORAGE = 1 shl 1
        const val PERM_BATTERY = 1 shl 2
        const val PERM_ACCESSIBILITY = 1 shl 3
        const val PERM_NOTIFICATION = 1 shl 4
        const val PERM_BACKGROUND = 1 shl 5
        const val PERM_ALL = PERM_FLOATING_WINDOW or PERM_STORAGE or
                PERM_BATTERY or PERM_ACCESSIBILITY or PERM_NOTIFICATION or PERM_BACKGROUND
    }
}
