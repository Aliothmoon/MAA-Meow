package com.aliothmoon.maameow.remote

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WakeOutcome(
    /** [com.aliothmoon.maameow.constant.WakeUnlockResult] */
    val unlockCode: Int,
    /** 要求拉起界面且已确认到主屏前台 */
    val uiLaunched: Boolean,
) : Parcelable
