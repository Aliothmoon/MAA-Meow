package com.aliothmoon.maameow.remote.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class BouncerSettleTest {

    @Test
    fun activeDeviceKeepsBaseline() {
        // 屏幕本就亮着，唤醒接近 0，不额外拖慢亮屏解锁
        assertEquals(1_200L, WakeUnlockController.bouncerSettleMs(0L))
        assertEquals(1_200L, WakeUnlockController.bouncerSettleMs(0L, coldWake = false))
    }

    @Test
    fun coldWakeUsesLongerBaselineEvenWhenInteractiveFlipsFast() {
        // 息屏/Doze 下 isDisplayInteractive 往往几十 ms 就变 true，冷唤醒需留足 bouncer 弹出时间
        assertEquals(2_000L, WakeUnlockController.bouncerSettleMs(0L, coldWake = true))
        assertEquals(2_172L, WakeUnlockController.bouncerSettleMs(86L, coldWake = true))
    }

    @Test
    fun deepSleepWakeGetsProportionallyLongerSettle() {
        // #230 现场：唤醒约 1.2 秒，原来只等 1.2 秒就打 PIN
        assertEquals(3_600L, WakeUnlockController.bouncerSettleMs(1_200L))
        assertEquals(4_400L, WakeUnlockController.bouncerSettleMs(1_200L, coldWake = true))
    }

    @Test
    fun settleIsCappedSoCountdownBudgetSurvives() {
        assertEquals(5_000L, WakeUnlockController.bouncerSettleMs(5_000L))
        assertEquals(5_000L, WakeUnlockController.bouncerSettleMs(60_000L))
        assertEquals(5_000L, WakeUnlockController.bouncerSettleMs(5_000L, coldWake = true))
    }
}
