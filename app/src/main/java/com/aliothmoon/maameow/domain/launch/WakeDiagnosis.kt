package com.aliothmoon.maameow.domain.launch

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 唤醒前的设备状态，须在唤醒前采样 */
internal data class WakeSnapshot(val screenOn: Boolean, val keyguardLocked: Boolean)

private val RECORDED_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * 解锁失败时写进触发日志的配置摘要
 * 触发日志会随遥测上传：只报配没配、何时录的；不报步数，点按录的手势步数就是 PIN 位数
 */
internal fun wakeConfigText(
    unlockType: String,
    credential: UnlockCredential,
    gestureRecordedAtMs: Long?,
    passwordRequired: Boolean,
    before: WakeSnapshot,
    zone: ZoneId = ZoneId.systemDefault(),
): UiText {
    val pin = uiTextOf(R.string.settings_wake_unlock_type_pin)
    val gesture = uiTextOf(R.string.settings_wake_unlock_type_gesture)
    val setup = when (credential) {
        is UnlockCredential.Pin -> uiTextOf(
            if (credential.isReady) {
                R.string.schedule_log_wake_config_pin_set
            } else {
                R.string.schedule_log_wake_config_pin_empty
            },
            pin,
        )

        is UnlockCredential.Gesture -> uiTextOf(
            R.string.schedule_log_wake_config_gesture,
            gesture,
            gestureRecordedAtMs?.let {
                RECORDED_AT_FORMAT.format(Instant.ofEpochMilli(it).atZone(zone))
            } ?: uiTextOf(R.string.schedule_log_wake_config_unknown),
        )

        // 选了手动录制但没录成时已退回无密码
        UnlockCredential.Swipe -> if (unlockType == UnlockCredential.TYPE_GESTURE) {
            uiTextOf(R.string.schedule_log_wake_config_gesture_missing, gesture)
        } else {
            uiTextOf(R.string.settings_wake_unlock_type_swipe)
        }
    }
    return uiTextOf(
        R.string.schedule_log_wake_config,
        setup,
        uiTextOf(if (passwordRequired) R.string.schedule_log_wake_yes else R.string.schedule_log_wake_no),
        uiTextOf(if (before.screenOn) R.string.schedule_log_wake_screen_on else R.string.schedule_log_wake_screen_off),
        uiTextOf(
            if (before.keyguardLocked) {
                R.string.schedule_log_wake_keyguard_locked
            } else {
                R.string.schedule_log_wake_keyguard_unlocked
            },
        ),
    )
}
