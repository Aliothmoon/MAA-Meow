package com.aliothmoon.maameow.remote.internal

import android.os.IBinder
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.SurfaceControl
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PowerControllerTest {
    private val display = mockk<IBinder>()

    @Before
    fun setUp() {
        mockkStatic(SurfaceControl::class, Ln::class)
        every { SurfaceControl.getBuiltInDisplay() } returns display
        every { SurfaceControl.getPhysicalDisplayIds() } returns longArrayOf(1L)
        every { SurfaceControl.getPhysicalDisplayToken(any()) } returns display
        every { SurfaceControl.hasGetPhysicalDisplayIdsMethod() } returns true
        every { SurfaceControl.setDisplayPowerMode(any(), any()) } returns true
        every { Ln.i(any()) } just runs
        every { Ln.e(any()) } just runs
        every { Ln.e(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        every { SurfaceControl.setDisplayPowerMode(any(), any()) } returns true
        PowerController.setDisplayPower(true)
        unmockkStatic(SurfaceControl::class, Ln::class)
    }

    @Test
    fun restoreFalse_isRetriedUntilSuccessful() {
        assertTrue(PowerController.setDisplayPower(false))
        every { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_NORMAL) } returnsMany
                listOf(false, false, true)
        assertTrue(PowerController.setDisplayPower(true))
        PowerController.destroy()
        verify(exactly = 3) { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_NORMAL) }
    }

    @Test
    fun restoreExhausted_keepsEmergencyRecoveryPending() {
        assertTrue(PowerController.setDisplayPower(false))
        every { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_NORMAL) } returns false
        assertFalse(PowerController.setDisplayPower(true))
        verify(exactly = 3) { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_NORMAL) }
        every { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_NORMAL) } returns true
        PowerController.destroy()
        PowerController.destroy()
        verify(exactly = 4) { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_NORMAL) }
    }

    @Test
    fun restoreException_isRetriedUntilSuccessful() {
        assertTrue(PowerController.setDisplayPower(false))
        var attempts = 0
        every { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_NORMAL) } answers {
            if (++attempts < 3) error("display service unavailable")
            true
        }
        assertTrue(PowerController.setDisplayPower(true))
        verify(exactly = 3) { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_NORMAL) }
    }

    @Test
    fun failedPowerOff_stillAttemptsEmergencyRestore() {
        every { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_OFF) } returns false
        assertFalse(PowerController.setDisplayPower(false))
        PowerController.destroy()
        verify(exactly = 1) { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_NORMAL) }
    }
}
