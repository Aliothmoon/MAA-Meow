package com.aliothmoon.maameow.remote

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VirtualDisplayStatus(
    val displayId: Int,
    val usingVdm: Boolean,
    val flagTier: Int,
    val displayGroupId: Int,
    /** [com.aliothmoon.maameow.constant.VirtualDisplayUpgradeOutcome] */
    val upgradeOutcome: Int,
    val hasOwnFocus: Boolean = false,
) : Parcelable
