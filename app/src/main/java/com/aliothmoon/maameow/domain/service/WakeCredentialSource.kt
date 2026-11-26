package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.UnlockCredential

/** 设置里选的唤醒解锁方式与凭证；启动管线与设置页自测共用 */
class WakeCredentialSource(
    private val settings: AppSettingsManager,
    private val gestures: UnlockGestureReader,
) {
    data class Configured(val type: String, val credential: UnlockCredential)

    suspend fun current(): Configured {
        val type = settings.wakeUnlockType.value
        val credential = UnlockCredential.of(
            type = type,
            pin = settings.wakeCredential.value,
            gestureJson = if (type == UnlockCredential.TYPE_GESTURE) gestures.readJson() else "",
        )
        return Configured(type, credential)
    }

    suspend fun gestureRecordedAtMs(): Long? = gestures.recordedAtMs()
}
