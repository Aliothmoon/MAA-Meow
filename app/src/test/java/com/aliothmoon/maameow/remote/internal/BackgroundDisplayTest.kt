package com.aliothmoon.maameow.remote.internal

import android.os.Build
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.VirtualDisplayBackendMode
import com.aliothmoon.maameow.constant.VirtualDisplayUpgradeOutcome
import com.aliothmoon.maameow.third.Ln
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackgroundDisplayTest {

    @Before
    fun setUp() {
        BackgroundDisplay.resetForTest()
        mockkStatic(Ln::class)
        every { Ln.i(any()) } just runs
        every { Ln.w(any()) } just runs
        every { Ln.e(any()) } just runs
    }

    @After
    fun tearDown() {
        BackgroundDisplay.resetForTest()
        unmockkStatic(Ln::class)
    }

    @Test
    fun shouldUpgradeToVdm_onlyTriggersOnFailureTierDegradeOrGroup0() {
        assertFalse(BackgroundDisplay.shouldUpgradeToVdm(legacyId = 5, legacyTier = 0, legacyGroupId = 2))
        assertFalse(BackgroundDisplay.shouldUpgradeToVdm(legacyId = 5, legacyTier = 0, legacyGroupId = -1))
        assertTrue(BackgroundDisplay.shouldUpgradeToVdm(legacyId = 5, legacyTier = 0, legacyGroupId = 0))
        assertTrue(BackgroundDisplay.shouldUpgradeToVdm(legacyId = 5, legacyTier = 1, legacyGroupId = 2))
        assertTrue(
            BackgroundDisplay.shouldUpgradeToVdm(
                legacyId = DefaultDisplayConfig.DISPLAY_NONE,
                legacyTier = VirtualDisplayManager.FLAG_TIER_NONE,
                legacyGroupId = VirtualDisplayManager.DISPLAY_GROUP_UNKNOWN,
            )
        )
    }

    @Test
    fun auto_healthyLegacyStaysOnLegacy() {
        val fake = FakeOps(legacyId = 5, legacyTier = 0, legacyGroupId = 2, vdmSupported = true)
        install(fake, sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, mode = VirtualDisplayBackendMode.AUTO)

        assertEquals(5, BackgroundDisplay.start())
        val status = BackgroundDisplay.status()
        assertFalse(status.usingVdm)
        assertEquals(5, status.displayId)
        assertEquals(0, status.flagTier)
        assertEquals(2, status.displayGroupId)
        assertEquals(VirtualDisplayUpgradeOutcome.NONE, status.upgradeOutcome)
        assertEquals(1, fake.legacyStartCalls)
        assertEquals(0, fake.vdmStartCalls)
    }

    @Test
    fun auto_legacyGroup0UpgradesToVdmAndReusesOnNextStart() {
        val fake = FakeOps(
            legacyId = 5,
            legacyTier = 0,
            legacyGroupId = 0,
            vdmSupported = true,
            vdmId = 12,
            vdmTier = 0,
            vdmGroupId = 3,
        )
        install(fake, sdk = Build.VERSION_CODES.BAKLAVA, mode = VirtualDisplayBackendMode.AUTO)

        assertEquals(12, BackgroundDisplay.start())
        val status = BackgroundDisplay.status()
        assertTrue(status.usingVdm)
        assertEquals(12, status.displayId)
        assertEquals(3, status.displayGroupId)
        assertEquals(VirtualDisplayUpgradeOutcome.UPGRADED, status.upgradeOutcome)
        assertEquals(1, fake.legacyStartCalls)
        assertEquals(1, fake.legacyStopCalls)
        assertEquals(1, fake.vdmStartCalls)

        assertEquals(12, BackgroundDisplay.start())
        assertEquals(1, fake.legacyStartCalls)
        assertEquals(2, fake.vdmStartCalls)
    }

    @Test
    fun auto_legacyTierDegradedUpgradesToVdm() {
        val fake = FakeOps(
            legacyId = 6,
            legacyTier = 1,
            legacyGroupId = 0,
            vdmSupported = true,
            vdmId = 15,
            vdmTier = 0,
            vdmGroupId = 4,
        )
        install(fake, sdk = Build.VERSION_CODES.VANILLA_ICE_CREAM, mode = VirtualDisplayBackendMode.AUTO)

        assertEquals(15, BackgroundDisplay.start())
        assertTrue(BackgroundDisplay.status().usingVdm)
        assertEquals(1, fake.legacyStopCalls)
        assertEquals(1, fake.vdmStartCalls)
    }

    @Test
    fun auto_legacyUnknownGroupStaysOnLegacy() {
        val fake = FakeOps(
            legacyId = 6,
            legacyTier = 0,
            legacyGroupId = VirtualDisplayManager.DISPLAY_GROUP_UNKNOWN,
            vdmSupported = true,
        )
        install(fake, sdk = Build.VERSION_CODES.BAKLAVA, mode = VirtualDisplayBackendMode.AUTO)

        assertEquals(6, BackgroundDisplay.start())
        assertFalse(BackgroundDisplay.status().usingVdm)
        assertEquals(0, fake.vdmStartCalls)
    }

    @Test
    fun auto_vdmUnsupportedKeepsLegacyWithoutRestarting() {
        val fake = FakeOps(legacyId = 7, legacyTier = 0, legacyGroupId = 0, vdmSupported = false)
        install(fake, sdk = Build.VERSION_CODES.BAKLAVA, mode = VirtualDisplayBackendMode.AUTO)

        assertEquals(7, BackgroundDisplay.start())
        val status = BackgroundDisplay.status()
        assertFalse(status.usingVdm)
        assertEquals(0, status.displayGroupId)
        assertEquals(VirtualDisplayUpgradeOutcome.UNSUPPORTED, status.upgradeOutcome)
        assertEquals(1, fake.legacyStartCalls)
        assertEquals(0, fake.legacyStopCalls)
        assertEquals(0, fake.vdmStartCalls)
    }

    @Test
    fun auto_vdmUpgradeFailureFallsBackToLegacy() {
        val fake = FakeOps(
            legacyId = 8,
            legacyTier = 0,
            legacyGroupId = 0,
            vdmSupported = true,
            vdmError = IllegalStateException("associate failed"),
        )
        install(fake, sdk = Build.VERSION_CODES.BAKLAVA, mode = VirtualDisplayBackendMode.AUTO)

        assertEquals(8, BackgroundDisplay.start())
        val first = BackgroundDisplay.status()
        assertFalse(first.usingVdm)
        assertEquals(8, first.displayId)
        assertEquals(VirtualDisplayUpgradeOutcome.FALLBACK, first.upgradeOutcome)
        assertEquals(2, fake.legacyStartCalls)
        assertEquals(1, fake.legacyStopCalls)
        assertEquals(1, fake.vdmStartCalls)
        assertEquals(1, fake.vdmStopCalls)

        assertEquals(8, BackgroundDisplay.start())
        val reused = BackgroundDisplay.status()
        assertFalse(reused.usingVdm)
        assertEquals(VirtualDisplayUpgradeOutcome.FALLBACK, reused.upgradeOutcome)
        assertEquals(2, fake.legacyStartCalls)
        assertEquals(1, fake.legacyStopCalls)
        assertEquals(1, fake.vdmStartCalls)

        BackgroundDisplay.stop()
        assertEquals(8, BackgroundDisplay.start())
        val circuitBroken = BackgroundDisplay.status()
        assertFalse(circuitBroken.usingVdm)
        assertEquals(VirtualDisplayUpgradeOutcome.FALLBACK, circuitBroken.upgradeOutcome)
        assertEquals(3, fake.legacyStartCalls)
        assertEquals(2, fake.legacyStopCalls)
        assertEquals(1, fake.vdmStartCalls)
    }

    @Test
    fun auto_legacyFailedAndVdmFailedDoesNotRetryLegacy() {
        val fake = FakeOps(
            legacyId = DefaultDisplayConfig.DISPLAY_NONE,
            vdmSupported = true,
            vdmError = IllegalStateException("vdm failed"),
        )
        install(fake, sdk = Build.VERSION_CODES.BAKLAVA, mode = VirtualDisplayBackendMode.AUTO)

        assertEquals(DefaultDisplayConfig.DISPLAY_NONE, BackgroundDisplay.start())
        assertEquals(1, fake.legacyStartCalls)
        assertEquals(1, fake.vdmStartCalls)
        assertEquals(1, fake.vdmStopCalls)
    }

    @Test
    fun auto_vdmReuseFailureFallsBackToLegacy() {
        val fake = FakeOps(
            legacyId = 5,
            legacyTier = 0,
            legacyGroupId = 0,
            vdmSupported = true,
            vdmId = 12,
            vdmGroupId = 3,
        )
        install(fake, sdk = Build.VERSION_CODES.BAKLAVA, mode = VirtualDisplayBackendMode.AUTO)

        assertEquals(12, BackgroundDisplay.start())
        assertTrue(BackgroundDisplay.status().usingVdm)

        fake.vdmError = IllegalStateException("association gone")
        assertEquals(5, BackgroundDisplay.start())
        val status = BackgroundDisplay.status()
        assertFalse(status.usingVdm)
        assertEquals(VirtualDisplayUpgradeOutcome.FALLBACK, status.upgradeOutcome)
        assertEquals(1, fake.vdmStopCalls)

        BackgroundDisplay.stop()
        assertEquals(VirtualDisplayUpgradeOutcome.NONE, BackgroundDisplay.status().upgradeOutcome)
    }

    @Test
    fun explicitLegacy_neverUpgradesEvenOnGroup0() {
        val fake = FakeOps(legacyId = 9, legacyTier = 0, legacyGroupId = 0, vdmSupported = true)
        install(fake, sdk = Build.VERSION_CODES.BAKLAVA, mode = VirtualDisplayBackendMode.LEGACY)

        assertEquals(9, BackgroundDisplay.start())
        assertFalse(BackgroundDisplay.status().usingVdm)
        assertEquals(0, fake.vdmStartCalls)
    }

    @Test
    fun strictVdm_failureNeverFallsBackToLegacy() {
        val fake = FakeOps(
            legacyId = 9,
            vdmSupported = true,
            vdmError = IllegalStateException("companiondevice service unavailable"),
        )
        install(fake, sdk = Build.VERSION_CODES.BAKLAVA, mode = VirtualDisplayBackendMode.VDM)

        assertEquals(DefaultDisplayConfig.DISPLAY_NONE, BackgroundDisplay.start())
        assertEquals(0, fake.legacyStartCalls)
        assertEquals(1, fake.vdmStartCalls)
    }

    @Test
    fun belowAndroid14_autoStaysOnLegacy() {
        val fake = FakeOps(legacyId = 4, legacyTier = 0, legacyGroupId = 0, vdmSupported = true)
        install(fake, sdk = Build.VERSION_CODES.TIRAMISU, mode = VirtualDisplayBackendMode.AUTO)

        assertEquals(4, BackgroundDisplay.start())
        assertFalse(BackgroundDisplay.status().usingVdm)
        assertEquals(0, fake.vdmStartCalls)
    }

    private fun install(fake: FakeOps, sdk: Int, mode: Int) {
        BackgroundDisplay.ops = fake
        BackgroundDisplay.sdkInt = { sdk }
        BackgroundDisplay.backendMode = mode
    }

    private class FakeOps(
        var legacyId: Int = 5,
        var legacyTier: Int = 0,
        var legacyGroupId: Int = 2,
        var vdmSupported: Boolean = true,
        var vdmId: Int = 10,
        var vdmTier: Int = 0,
        var vdmGroupId: Int = 3,
        var vdmError: Throwable? = null,
    ) : BackgroundDisplay.BackendOps {
        var legacyActive = false
        var vdmActive = false
        var legacyStartCalls = 0
        var legacyStopCalls = 0
        var vdmStartCalls = 0
        var vdmStopCalls = 0

        override fun startLegacy(): Int {
            legacyStartCalls++
            legacyActive = legacyId != DefaultDisplayConfig.DISPLAY_NONE
            return legacyId
        }

        override fun stopLegacy() {
            legacyStopCalls++
            legacyActive = false
        }

        override fun setLegacyResolution(width: Int, height: Int, dpi: Int) = Unit

        override fun legacyDisplayId(): Int = if (legacyActive) legacyId else DefaultDisplayConfig.DISPLAY_NONE
        override fun legacyFlagTier(): Int = if (legacyActive) legacyTier else VirtualDisplayManager.FLAG_TIER_NONE
        override fun legacyDisplayGroupId(): Int =
            if (legacyActive) legacyGroupId else VirtualDisplayManager.DISPLAY_GROUP_UNKNOWN

        override fun isVdmSupported(): Boolean = vdmSupported

        override fun startVdm(): Int {
            vdmStartCalls++
            vdmError?.let { throw it }
            vdmActive = vdmId != DefaultDisplayConfig.DISPLAY_NONE
            return vdmId
        }

        override fun stopVdm() {
            vdmStopCalls++
            vdmActive = false
        }

        override fun setVdmResolution(width: Int, height: Int, dpi: Int) = Unit

        override fun vdmDisplayId(): Int = if (vdmActive) vdmId else DefaultDisplayConfig.DISPLAY_NONE
        override fun vdmFlagTier(): Int = if (vdmActive) vdmTier else VirtualDisplayManager.FLAG_TIER_NONE
        override fun vdmDisplayGroupId(): Int =
            if (vdmActive) vdmGroupId else VirtualDisplayManager.DISPLAY_GROUP_UNKNOWN
    }
}
