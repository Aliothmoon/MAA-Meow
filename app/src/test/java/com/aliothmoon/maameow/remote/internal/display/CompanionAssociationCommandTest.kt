package com.aliothmoon.maameow.remote.internal.display

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionAssociationCommandTest {

    @Test
    fun android14Association_usesLegacyThreeArgumentShape() {
        assertEquals(
            listOf("cmd", "companiondevice", "associate", "10", "com.android.shell", "02:4D:41:00:00:01"),
            CompanionAssociation.buildAssociateCommand("02:4D:41:00:00:01", 10, 34),
        )
    }

    @Test
    fun android15Association_requestsStreamingProfile() {
        assertEquals(
            listOf(
                "cmd",
                "companiondevice",
                "associate",
                "10",
                "com.android.shell",
                "02:4D:41:00:00:01",
                "android.app.role.COMPANION_DEVICE_APP_STREAMING",
                "false",
            ),
            CompanionAssociation.buildAssociateCommand("02:4D:41:00:00:01", 10, 35),
        )
    }

    @Test
    fun android15Association_migratesLegacyNullProfile() {
        assertEquals(
            true,
            CompanionAssociation.requiresStreamingProfileMigration(null, 35),
        )
        assertEquals(
            false,
            CompanionAssociation.requiresStreamingProfileMigration(
                "android.app.role.COMPANION_DEVICE_APP_STREAMING",
                35,
            ),
        )
        assertEquals(
            false,
            CompanionAssociation.requiresStreamingProfileMigration(null, 34),
        )
    }
}
