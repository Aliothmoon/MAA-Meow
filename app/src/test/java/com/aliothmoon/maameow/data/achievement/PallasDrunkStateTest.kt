package com.aliothmoon.maameow.data.achievement

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 注入首点即中、无防抖的彩蛋,专测状态机本身
 *
 * 连点门槛、防抖与退出冷却由 [PallasDebugEasterEggTest] 覆盖
 */
class PallasDrunkStateTest {

    private lateinit var hangoverFlag: MutableStateFlow<Boolean>
    private lateinit var settings: AppSettingsManager
    private lateinit var state: PallasDrunkState

    @Before
    fun setUp() {
        hangoverFlag = MutableStateFlow(false)
        settings = mockk(relaxed = true)
        every { settings.pallasHangover } returns hangoverFlag
        coEvery { settings.awaitLoaded() } returns Unit
        coEvery { settings.setPallasHangover(any()) } answers { hangoverFlag.value = firstArg() }
        state = PallasDrunkState(
            settings,
            PallasDebugEasterEgg(
                clicksRequired = 1,
                triggerChance = 1.0,
                clickDebounceMs = 0L,
                exitCooldownMs = 0L,
            ),
        )
    }

    @Test
    fun firstClick_lightsMedalAndAsksBeforePouring() = runTest {
        state.onMedalClick()

        assertTrue(state.debugActive.value)
        assertEquals(PallasPrompt.DRUNK, state.prompt.value)
        // 还没确认，先别醉，也别落盘
        assertFalse(state.isDrunk.value)
        assertFalse(hangoverFlag.value)
        assertTrue(state.tip.value.isNotEmpty())
    }

    @Test
    fun confirmingDrunkPrompt_getsDrunkAndMarksHangover() = runTest {
        state.onMedalClick()
        state.confirmPrompt()

        assertTrue(state.isDrunk.value)
        assertTrue(hangoverFlag.value)
        assertNull(state.prompt.value)
    }

    @Test
    fun clickingAgainWhileDrunk_sobersAndNags() = runTest {
        state.onMedalClick()
        state.confirmPrompt()

        state.onMedalClick()

        assertFalse(state.isDrunk.value)
        assertFalse(state.debugActive.value)
        assertFalse(hangoverFlag.value)
        assertEquals(PallasPrompt.HANGOVER, state.prompt.value)
        // 醒了就把碎碎念收掉
        assertTrue(state.tip.value.isEmpty())
    }

    /** 对齐 WPF HangoverEnd 的提前 return:没真喝下去就退出的不念叨 */
    @Test
    fun exitingWithoutConfirming_staysQuiet() = runTest {
        state.onMedalClick()
        state.onMedalClick()

        assertFalse(state.debugActive.value)
        assertNull(state.prompt.value)
        // 没醉过就没必要写盘
        coVerify(exactly = 0) { settings.setPallasHangover(any()) }
    }

    @Test
    fun startup_withLeftoverFlag_nagsOnceAndClears() = runTest {
        hangoverFlag.value = true

        state.restorePendingHangover()

        assertEquals(PallasPrompt.HANGOVER, state.prompt.value)
        assertFalse(hangoverFlag.value)
    }

    @Test
    fun startup_withoutFlag_saysNothing() = runTest {
        state.restorePendingHangover()

        assertNull(state.prompt.value)
    }

    /** Activity 重建会再跑一次，标记已清，不该重复弹 */
    @Test
    fun startup_rerunAfterConsume_doesNotNagTwice() = runTest {
        hangoverFlag.value = true
        state.restorePendingHangover()
        state.confirmPrompt()

        state.restorePendingHangover()

        assertNull(state.prompt.value)
    }

    /** 醉着时重建，别把宿醉标记误清掉 */
    @Test
    fun startup_whileDrunk_keepsFlag() = runTest {
        state.onMedalClick()
        state.confirmPrompt()

        state.restorePendingHangover()

        assertTrue(hangoverFlag.value)
        assertNull(state.prompt.value)
    }
}
