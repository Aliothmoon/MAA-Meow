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
import org.junit.Before
import org.junit.Test

class PowerControllerTest {
    private val display = mockk<IBinder>()
    private val defaultSystemAwake = PowerController.systemAwake

    @Volatile
    private var awake = true

    @Before
    fun setUp() {
        mockkStatic(SurfaceControl::class, Ln::class)
        PowerController.systemAwake = { awake }
        every { SurfaceControl.getBuiltInDisplay() } returns display
        every { SurfaceControl.setDisplayPowerMode(any(), any()) } returns true
        every { Ln.i(any()) } just runs
        every { Ln.e(any()) } just runs
    }

    @After
    fun tearDown() {
        PowerController.setDisplayPower(true)
        PowerController.systemAwake = defaultSystemAwake
        unmockkStatic(SurfaceControl::class, Ln::class)
    }

    /** 关屏失败也留恢复标记，多屏可能只关掉了一部分 */
    @Test
    fun destroy_restoresWhileAwake() {
        every { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_OFF) } returns false
        PowerController.setDisplayPower(false)
        PowerController.destroy()
        verify(exactly = 1) { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_NORMAL) }
    }

    @Test
    fun destroy_leavesPanelToSystemWhileAsleep() {
        PowerController.setDisplayPower(false)
        awake = false
        PowerController.destroy()
        PowerController.destroy()
        verify(exactly = 0) { SurfaceControl.setDisplayPowerMode(any(), SurfaceControl.POWER_MODE_NORMAL) }
    }
}
