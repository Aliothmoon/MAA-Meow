package com.aliothmoon.maameow.remote.internal

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VdmDisplayControllerTest {

    private companion object {
        const val SHELL_PACKAGE = "com.android.shell"
        const val ASSOCIATION_ADDRESS = "02:00:00:00:00:01"
        const val STREAMING_PROFILE = "android.app.role.COMPANION_DEVICE_APP_STREAMING"
        const val OTHER_PROFILE = "android.app.role.COMPANION_DEVICE_WATCH"

        const val DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
        const val TRUSTED = 1 shl 10
        const val TOUCH_FEEDBACK_DISABLED = 1 shl 13
        const val OWN_FOCUS = 1 shl 14
    }

    private val tiers = VdmDisplayController.tiers

    @Test
    fun associateCommandDependsOnAndroidVersion() {
        assertEquals(
            "cmd companiondevice associate 0 $SHELL_PACKAGE $ASSOCIATION_ADDRESS",
            associateCommand(Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
        )
        val withStreamingProfile =
            "cmd companiondevice associate 0 $SHELL_PACKAGE $ASSOCIATION_ADDRESS $STREAMING_PROFILE false"
        assertEquals(withStreamingProfile, associateCommand(Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertEquals(withStreamingProfile, associateCommand(Build.VERSION_CODES.BAKLAVA))
    }

    @Test
    fun profileMigrationOnlyTargetsStreamingProfileOnAndroid15Plus() {
        assertFalse(VdmDisplayController.requiresProfileMigration(profile = null, sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
        assertTrue(VdmDisplayController.requiresProfileMigration(profile = null, sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertTrue(VdmDisplayController.requiresProfileMigration(profile = OTHER_PROFILE, sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertFalse(VdmDisplayController.requiresProfileMigration(profile = STREAMING_PROFILE, sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertTrue(VdmDisplayController.requiresProfileMigration(profile = null, sdkInt = Build.VERSION_CODES.BAKLAVA))
        assertFalse(VdmDisplayController.requiresProfileMigration(profile = STREAMING_PROFILE, sdkInt = Build.VERSION_CODES.BAKLAVA))
    }

    @Test
    fun firstTierAsksForEverything() {
        val first = tiers.first()
        assertTrue(first.alwaysUnlocked)
        assertTrue(first.flags and TRUSTED != 0)
        assertTrue(first.flags and OWN_FOCUS != 0)
    }

    @Test
    fun lastTierNeedsNoExtraPermission() {
        val last = tiers.last()
        assertFalse(last.alwaysUnlocked)
        assertEquals(0, last.flags and (TRUSTED or OWN_FOCUS))
    }

    @Test
    fun alwaysUnlockedIsKeptBeforeTrustedIsDropped() {
        val firstLocked = tiers.indexOfFirst { !it.alwaysUnlocked }
        assertTrue(tiers.take(firstLocked).all { it.alwaysUnlocked })
        assertTrue(tiers.take(firstLocked).any { !it.trusted })
    }

    @Test
    fun ownFocusOnlyComesWithTrusted() {
        for (tier in tiers) {
            assertEquals(tier.trusted, tier.flags and TRUSTED != 0)
            assertEquals(tier.trusted, tier.flags and OWN_FOCUS != 0)
        }
    }

    @Test
    fun everyTierDisablesTouchFeedbackAndDestroysContentOnRemoval() {
        for (tier in tiers) {
            assertTrue(tier.flags and TOUCH_FEEDBACK_DISABLED != 0)
            assertTrue(tier.flags and DESTROY_CONTENT_ON_REMOVAL != 0)
        }
    }

    private fun associateCommand(sdkInt: Int): String =
        VdmDisplayController.buildAssociateCommand(address = ASSOCIATION_ADDRESS, sdkInt = sdkInt)
}
