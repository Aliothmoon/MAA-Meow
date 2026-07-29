package com.aliothmoon.maameow.remote.internal

import android.app.AppOpsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAudioMuteControllerTest {

    @Test
    fun playAudioRestoredWhenDefaultOrAllowed() {
        assertTrue(AppOpsHelper.isPlayAudioRestored(AppOpsManager.MODE_DEFAULT))
        assertTrue(AppOpsHelper.isPlayAudioRestored(AppOpsManager.MODE_ALLOWED))
    }

    @Test
    fun playAudioNotRestoredWhenIgnoredOrInvalid() {
        assertFalse(AppOpsHelper.isPlayAudioRestored(AppOpsManager.MODE_IGNORED))
        assertFalse(AppOpsHelper.isPlayAudioRestored(-1))
    }
}
