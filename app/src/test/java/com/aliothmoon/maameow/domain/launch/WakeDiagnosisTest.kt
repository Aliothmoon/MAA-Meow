package com.aliothmoon.maameow.domain.launch

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.ZoneOffset

/** 解锁失败时的配置摘要：报配没配，不带凭证 */
class WakeDiagnosisTest {

    private val idle = WakeSnapshot(screenOn = false, keyguardLocked = true)
    private val gesture = uiTextOf(R.string.settings_wake_unlock_type_gesture)

    @Test
    fun pinSet_reportsSetWithoutDigits() {
        val text = wakeConfigText(
            unlockType = UnlockCredential.TYPE_PIN,
            credential = UnlockCredential.Pin("135790"),
            gestureRecordedAtMs = null,
            passwordRequired = true,
            before = idle,
        )
        assertEquals(
            uiTextOf(
                R.string.schedule_log_wake_config,
                uiTextOf(R.string.schedule_log_wake_config_pin_set, uiTextOf(R.string.settings_wake_unlock_type_pin)),
                uiTextOf(R.string.schedule_log_wake_yes),
                uiTextOf(R.string.schedule_log_wake_screen_off),
                uiTextOf(R.string.schedule_log_wake_keyguard_locked),
            ),
            text,
        )
        assertFalse(text.toString().contains("135790"))
    }

    @Test
    fun pinBlank_reportsNotSet() {
        val text = wakeConfigText(
            unlockType = UnlockCredential.TYPE_PIN,
            credential = UnlockCredential.Pin(""),
            gestureRecordedAtMs = null,
            passwordRequired = true,
            before = idle,
        )
        assertEquals(
            uiTextOf(R.string.schedule_log_wake_config_pin_empty, uiTextOf(R.string.settings_wake_unlock_type_pin)),
            firstArg(text),
        )
    }

    @Test
    fun gestureRecorded_reportsRecordingTime() {
        val text = wakeConfigText(
            unlockType = UnlockCredential.TYPE_GESTURE,
            credential = UnlockCredential.Gesture("{}"),
            // 2026-10-16 09:30 UTC
            gestureRecordedAtMs = 1_792_143_000_000L,
            passwordRequired = true,
            before = WakeSnapshot(screenOn = true, keyguardLocked = false),
            zone = ZoneOffset.UTC,
        )
        assertEquals(
            uiTextOf(R.string.schedule_log_wake_config_gesture, gesture, "2026-10-16 09:30"),
            firstArg(text),
        )
    }

    @Test
    fun gestureTypeWithoutRecording_reportsFallback() {
        // 没录成时 UnlockCredential.of 已退回 Swipe
        val text = wakeConfigText(
            unlockType = UnlockCredential.TYPE_GESTURE,
            credential = UnlockCredential.Swipe,
            gestureRecordedAtMs = null,
            passwordRequired = false,
            before = idle,
        )
        assertEquals(uiTextOf(R.string.schedule_log_wake_config_gesture_missing, gesture), firstArg(text))
    }

    @Test
    fun gestureUnknownTime_fallsBackToUnknown() {
        val text = wakeConfigText(
            unlockType = UnlockCredential.TYPE_GESTURE,
            credential = UnlockCredential.Gesture("{}"),
            gestureRecordedAtMs = null,
            passwordRequired = true,
            before = idle,
        )
        assertEquals(
            uiTextOf(
                R.string.schedule_log_wake_config_gesture,
                gesture,
                uiTextOf(R.string.schedule_log_wake_config_unknown),
            ),
            firstArg(text),
        )
    }

    private fun firstArg(text: UiText) = (text as UiText.Resource).args.first()
}
