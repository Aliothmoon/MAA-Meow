package com.aliothmoon.maameow.presentation.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {

    private val cooldownSteps = OnboardingSteps.all.indices.filter { OnboardingSteps.all[it].cooldown }
    private val plainSteps = OnboardingSteps.all.indices.filterNot { OnboardingSteps.all[it].cooldown }

    @Test
    fun steps_haveCooldownAndPlainSteps() {
        assertTrue(cooldownSteps.isNotEmpty())
        assertTrue(plainSteps.isNotEmpty())
    }

    @Test
    fun steps_targetsAreUnique() {
        val targets = OnboardingSteps.all.mapNotNull { it.target }
        assertEquals(targets.size, targets.toSet().size)
    }

    @Test
    fun defaultStart_skippableWithoutCooldown() {
        val state = OnboardingState()
        state.start()
        assertTrue(state.skippable)
        assertTrue(OnboardingSteps.all.indices.none { state.needsCooldown(it) })
    }

    @Test
    fun forcedStart_cooldownOnlyOnMarkedSteps() {
        val state = OnboardingState()
        state.start(skippable = false, cooldown = true)
        assertFalse(state.skippable)
        assertTrue(cooldownSteps.all { state.needsCooldown(it) })
        assertTrue(plainSteps.none { state.needsCooldown(it) })
    }

    @Test
    fun cooledDownStep_notRepeatedUntilRestart() {
        val state = OnboardingState()
        val index = cooldownSteps.first()
        state.start(cooldown = true)
        state.markCooledDown(index)
        assertFalse(state.needsCooldown(index))

        state.finish()
        state.start(cooldown = true)
        assertTrue(state.needsCooldown(index))
    }

    @Test
    fun startWhileActive_keepsCurrentMode() {
        val state = OnboardingState()
        state.start(skippable = false, cooldown = true)
        state.next()
        state.start()
        assertFalse(state.skippable)
        assertEquals(1, state.stepIndex)
    }
}
