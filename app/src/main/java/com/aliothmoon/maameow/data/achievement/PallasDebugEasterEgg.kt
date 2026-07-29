package com.aliothmoon.maameow.data.achievement

import kotlin.random.Random

class PallasDebugEasterEgg(
    private val random: () -> Double = { Random.nextDouble() },
    private val clicksRequired: Int = 10,
    private val triggerChance: Double = 0.1,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val clickDebounceMs: Long = 280L,
    private val exitCooldownMs: Long = 1_200L,
) {
    var isTriggered: Boolean = false
        private set

    private var clickCount: Int = 0
    private var lastClickMs: Long = Long.MIN_VALUE / 2
    private var enteredAtMs: Long = 0L

    fun onClick(): PallasClickResult {
        val now = nowMs()
        if (now - lastClickMs < clickDebounceMs) {
            return PallasClickResult.Ignored
        }
        lastClickMs = now

        if (isTriggered) {
            if (now - enteredAtMs < exitCooldownMs) {
                return PallasClickResult.Ignored
            }
            reset()
            return PallasClickResult.ExitedDebug
        }
        if (++clickCount < clicksRequired) {
            return PallasClickResult.Counting(clickCount)
        }
        clickCount = 0
        return if (random() < triggerChance) {
            isTriggered = true
            enteredAtMs = now
            PallasClickResult.EnteredDebug
        } else {
            PallasClickResult.MissedRoll
        }
    }

    fun reset() {
        isTriggered = false
        clickCount = 0
        enteredAtMs = 0L
    }
}

sealed interface PallasClickResult {
    data object EnteredDebug : PallasClickResult
    data object ExitedDebug : PallasClickResult
    data class Counting(val n: Int) : PallasClickResult
    data object MissedRoll : PallasClickResult
    /** 防抖 / 退出冷却内，不改变状态。 */
    data object Ignored : PallasClickResult
}
