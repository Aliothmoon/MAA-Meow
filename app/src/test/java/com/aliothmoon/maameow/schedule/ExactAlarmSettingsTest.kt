package com.aliothmoon.maameow.schedule

import android.os.Build
import com.aliothmoon.maameow.schedule.service.ExactAlarmSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmSettingsTest {

    @Test
    fun belowS_alwaysAllowed_andNoToggle() {
        assertTrue(ExactAlarmSettings.isAllowed(sdkInt = Build.VERSION_CODES.Q, canScheduleExactAlarms = false))
        assertFalse(ExactAlarmSettings.hasToggle(Build.VERSION_CODES.Q))
    }

    @Test
    fun atS_followsSystemSwitch() {
        assertTrue(ExactAlarmSettings.hasToggle(Build.VERSION_CODES.S))
        assertTrue(ExactAlarmSettings.isAllowed(sdkInt = Build.VERSION_CODES.S, canScheduleExactAlarms = true))
        assertFalse(ExactAlarmSettings.isAllowed(sdkInt = Build.VERSION_CODES.S, canScheduleExactAlarms = false))
    }
}
