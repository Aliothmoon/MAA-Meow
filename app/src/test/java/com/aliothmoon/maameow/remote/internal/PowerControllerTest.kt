package com.aliothmoon.maameow.remote.internal

import android.os.IBinder
import com.aliothmoon.maameow.constant.AndroidVersions
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.SurfaceControl
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PowerControllerTest {
    private val display = mockk<IBinder>()
    private val defaultSystemAwake = PowerController.systemAwake
    private val defaultOps = PowerController.ops
    private val defaultSleepMs = PowerController.sleepMs

    @Volatile
    private var awake = true

    @Before
    fun setUp() {
        mockkStatic(SurfaceControl::class, Ln::class)
        PowerController.systemAwake = { awake }
        every { SurfaceControl.getBuiltInDisplay() } returns display
        every { SurfaceControl.setDisplayPowerMode(any(), any()) } returns true
        every { Ln.i(any()) } just runs
        every { Ln.w(any()) } just runs
        every { Ln.e(any()) } just runs
        every { Ln.e(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        PowerController.stopUserActivityKeepAlive()
        PowerController.setDisplayPower(true)
        PowerController.systemAwake = defaultSystemAwake
        PowerController.ops = defaultOps
        PowerController.sleepMs = defaultSleepMs
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

    @Test
    fun targetKeepAliveDisplay_previewOnlyKeepsOwnPowerGroupAlive() {
        val fake = FakeOps()
        PowerController.ops = fake

        assertNull(PowerController.targetKeepAliveDisplay(null, taskKeepAlive = true, previewKeepAlive = true))

        for (group in listOf(-1, 0)) {
            fake.groupId = group
            assertNull(PowerController.targetKeepAliveDisplay(8, taskKeepAlive = false, previewKeepAlive = true))
            assertEquals(8, PowerController.targetKeepAliveDisplay(8, taskKeepAlive = true, previewKeepAlive = false))
        }

        fake.groupId = 1
        assertEquals(8, PowerController.targetKeepAliveDisplay(8, taskKeepAlive = false, previewKeepAlive = true))
        assertEquals(8, PowerController.targetKeepAliveDisplay(8, taskKeepAlive = true, previewKeepAlive = false))
        assertNull(PowerController.targetKeepAliveDisplay(8, taskKeepAlive = false, previewKeepAlive = false))
    }

    @Test
    fun ensureDisplayAwake_sharedOrUnknownGroup_neverWakesMainScreen() {
        val fake = FakeOps(displayAwake = false)
        PowerController.ops = fake

        for (group in listOf(-1, 0)) {
            fake.groupId = group
            assertTrue(PowerController.ensureDisplayAwake(8, AndroidVersions.API_36_ANDROID_16))
            assertTrue(PowerController.ensureDisplayAwake(8, AndroidVersions.API_34_ANDROID_14))
        }
        assertEquals(0, fake.stateChecks)
        assertTrue(fake.wokenDisplays.isEmpty())
    }

    @Test
    fun ensureDisplayAwake_ownGroupAwakeOrUnknown_skipsWake() {
        val fake = FakeOps(groupId = 1, displayAwake = true)
        PowerController.ops = fake
        assertTrue(PowerController.ensureDisplayAwake(8, AndroidVersions.API_36_ANDROID_16))

        fake.displayAwake = null
        assertTrue(PowerController.ensureDisplayAwake(8, AndroidVersions.API_34_ANDROID_14))
        assertTrue(fake.wokenDisplays.isEmpty())
    }

    @Test
    fun ensureDisplayAwake_api33CannotQueryPerDisplay_assumesAwake() {
        val fake = FakeOps(groupId = 1, displayAwake = false)
        PowerController.ops = fake

        assertTrue(PowerController.ensureDisplayAwake(8, AndroidVersions.API_33_ANDROID_13))
        assertEquals(0, fake.stateChecks)
        assertTrue(fake.wokenDisplays.isEmpty())
    }

    @Test
    fun ensureDisplayAwake_ownGroupAsleepApi34And35_returnsFalseForRecreate() {
        val fake = FakeOps(groupId = 1, displayAwake = false)
        PowerController.ops = fake

        for (sdk in listOf(
            AndroidVersions.API_34_ANDROID_14,
            AndroidVersions.API_35_ANDROID_15,
        )) {
            assertFalse("sdk=$sdk should report asleep", PowerController.ensureDisplayAwake(8, sdk))
        }
        assertTrue(fake.wokenDisplays.isEmpty())
    }

    @Test
    fun ensureDisplayAwake_ownGroupAsleepApi36_wakesAndVerifiesState() {
        val fake = FakeOps(groupId = 1, displayAwake = false, wakeResult = true, wakeFlipsAwake = true)
        PowerController.ops = fake
        assertTrue(PowerController.ensureDisplayAwake(8, AndroidVersions.API_36_ANDROID_16))
        assertEquals(listOf(8), fake.wokenDisplays)

        val unverified = FakeOps(groupId = 1, displayAwake = false, wakeResult = true, wakeFlipsAwake = false)
        PowerController.ops = unverified
        assertFalse(PowerController.ensureDisplayAwake(9, AndroidVersions.API_36_ANDROID_16))
        assertEquals(listOf(9), unverified.wokenDisplays)

        val rejected = FakeOps(groupId = 1, displayAwake = false, wakeResult = false, wakeFlipsAwake = true)
        PowerController.ops = rejected
        assertFalse(PowerController.ensureDisplayAwake(10, AndroidVersions.API_36_ANDROID_16))
    }

    @Test
    fun keepAlive_firesImmediatelySwitchesDisplayAndRestartsAfterStop() {
        val poked = LinkedBlockingQueue<Int>()
        val sleepEntered = LinkedBlockingQueue<CountDownLatch>()
        val fake = FakeOps(onUserActivity = { poked.put(it) })
        PowerController.ops = fake
        PowerController.sleepMs = {
            val gate = CountDownLatch(1)
            sleepEntered.put(gate)
            assertTrue(gate.await(5, TimeUnit.SECONDS))
        }

        PowerController.startUserActivityKeepAlive(8)
        assertEquals(8, poked.poll(2, TimeUnit.SECONDS))
        val firstSleep = sleepEntered.poll(2, TimeUnit.SECONDS)!!

        PowerController.startUserActivityKeepAlive(10)
        firstSleep.countDown()
        assertEquals(10, poked.poll(2, TimeUnit.SECONDS))
        val secondSleep = sleepEntered.poll(2, TimeUnit.SECONDS)!!

        PowerController.stopUserActivityKeepAlive()
        secondSleep.countDown()
        PowerController.startUserActivityKeepAlive(12)
        assertEquals(12, poked.poll(2, TimeUnit.SECONDS))
        val thirdSleep = sleepEntered.poll(2, TimeUnit.SECONDS)!!
        PowerController.stopUserActivityKeepAlive()
        thirdSleep.countDown()
    }

    private class FakeOps(
        var groupId: Int = 1,
        var displayAwake: Boolean? = true,
        var wakeResult: Boolean = true,
        var wakeFlipsAwake: Boolean = true,
        private val onUserActivity: (Int) -> Unit = {},
    ) : PowerController.DisplayPowerOps {
        var stateChecks = 0
        val wokenDisplays = mutableListOf<Int>()

        override fun displayGroupId(displayId: Int): Int = groupId

        override fun isDisplayInteractiveOrNull(displayId: Int): Boolean? {
            stateChecks++
            return displayAwake
        }

        override fun wakeDisplay(displayId: Int): Boolean {
            wokenDisplays += displayId
            if (wakeResult && wakeFlipsAwake) displayAwake = true
            return wakeResult
        }

        override fun userActivity(displayId: Int) = onUserActivity(displayId)
    }
}
