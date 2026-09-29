package com.aliothmoon.maameow.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelTimePickerStateTest {
    @Test
    fun initialWheelSelectionPreservesEveryHour() {
        for (hour in 0..23) {
            val state = WheelTimePickerState(hour, 41)

            assertEquals(hour >= 12, state.isPm)
            state.selectHour(state.startHourIndex + 1)

            assertEquals(hour, state.hour)
            assertEquals(41, state.minute)
        }
    }

    @Test
    fun twelveAmIsMidnightAndTwelvePmIsNoon() {
        val state = WheelTimePickerState(9, 30)
        state.selectHour(12)
        assertEquals(0, state.hour)

        state.selectPeriod(true)
        assertEquals(12, state.hour)

        state.selectPeriod(false)
        assertEquals(0, state.hour)
        assertEquals(30, state.minute)
    }

    @Test
    fun scrollingHoursPreservesSelectedPeriod() {
        val state = WheelTimePickerState(0, 59)
        state.selectPeriod(true)

        for (hour in 1..11) {
            state.selectHour(hour)
            assertEquals(hour + 12, state.hour)
        }
        state.selectHour(12)
        assertEquals(12, state.hour)
        assertEquals(59, state.minute)
    }

    @Test
    fun switchingPeriodPreservesClockHourAndMinute() {
        val state = WheelTimePickerState(23, 59)
        state.selectPeriod(false)
        assertEquals(11, state.hour)
        state.selectPeriod(false)
        assertEquals(11, state.hour)
        state.selectPeriod(true)
        assertEquals(23, state.hour)
        assertEquals(59, state.minute)
    }

    @Test
    fun hour24WheelMapsIndexStraightToHour() {
        for (hour in 0..23) {
            val state = WheelTimePickerState(hour, 41, is24Hour = true)

            assertEquals(24, state.hourCount)
            assertEquals(0, state.firstHourValue)
            assertEquals(hour, state.startHourIndex)

            state.selectHourIndex(state.startHourIndex)
            assertEquals(hour, state.hour)
            assertEquals(41, state.minute)
        }
    }

    @Test
    fun hour12WheelKeepsOneToTwelveColumn() {
        val state = WheelTimePickerState(13, 0)

        assertEquals(12, state.hourCount)
        assertEquals(1, state.firstHourValue)
        // 13 点落在 12 制的第 1 项「01」
        assertEquals(0, state.startHourIndex)

        state.selectHourIndex(0)
        assertEquals(13, state.hour)
    }

    @Test
    fun hour24IndexStaysWithinDay() {
        val state = WheelTimePickerState(8, 0, is24Hour = true)

        state.selectHourIndex(-1)
        assertEquals(0, state.hour)

        state.selectHourIndex(24)
        assertEquals(23, state.hour)
    }

    @Test
    fun rebuildingWithOtherFormatPreservesTheClockValue() {
        for (hour in 0..23) {
            val from24 = WheelTimePickerState(hour, 41, is24Hour = true)
            val to12 = WheelTimePickerState(from24.hour, from24.minute, is24Hour = false)
            to12.selectHourIndex(to12.startHourIndex)
            assertEquals(hour, to12.hour)
            assertEquals(41, to12.minute)

            val back24 = WheelTimePickerState(to12.hour, to12.minute, is24Hour = true)
            back24.selectHourIndex(back24.startHourIndex)
            assertEquals(hour, back24.hour)
            assertEquals(41, back24.minute)
        }
    }

    @Test
    fun initialValuesStayWithinValidTimeRange() {
        val early = WheelTimePickerState(-1, -1)
        early.selectHour(early.startHourIndex + 1)
        assertEquals(0, early.hour)
        assertEquals(0, early.minute)

        val late = WheelTimePickerState(24, 60)
        late.selectHour(late.startHourIndex + 1)
        assertEquals(23, late.hour)
        assertEquals(59, late.minute)

        val late24 = WheelTimePickerState(24, 60, is24Hour = true)
        late24.selectHourIndex(late24.startHourIndex)
        assertEquals(23, late24.hour)
        assertEquals(59, late24.minute)
    }
}
