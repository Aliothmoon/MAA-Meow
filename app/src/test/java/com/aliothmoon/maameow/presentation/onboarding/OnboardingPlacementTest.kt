package com.aliothmoon.maameow.presentation.onboarding

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPlacementTest {

    private val area = IntRect(left = 0, top = 0, right = 1000, bottom = 2000)

    @Test
    fun noHole_centersCard() {
        val offset = OnboardingPlacement.resolve(area, null, cardWidth = 400, cardHeight = 300, gap = 20)
        assertEquals(IntOffset(300, 850), offset)
    }

    @Test
    fun spaceBelow_placesBelowHoleCenteredHorizontally() {
        val hole = Rect(100f, 200f, 900f, 400f)
        val offset = OnboardingPlacement.resolve(area, hole, cardWidth = 400, cardHeight = 300, gap = 20)
        assertEquals(IntOffset(300, 420), offset)
    }

    @Test
    fun noSpaceBelow_placesAboveHole() {
        val hole = Rect(100f, 1800f, 900f, 1950f)
        val offset = OnboardingPlacement.resolve(area, hole, cardWidth = 400, cardHeight = 300, gap = 20)
        assertEquals(IntOffset(300, 1800 - 20 - 300), offset)
    }

    @Test
    fun neitherAboveNorBelow_fallsBackToSide() {
        val wide = IntRect(left = 0, top = 0, right = 2000, bottom = 600)
        val hole = Rect(0f, 100f, 800f, 500f)
        val offset = OnboardingPlacement.resolve(wide, hole, cardWidth = 400, cardHeight = 300, gap = 20)
        assertEquals(IntOffset(820, 150), offset)
    }

    @Test
    fun nothingFits_staysInsideAreaAndMayOverlapHole() {
        val small = IntRect(left = 0, top = 0, right = 500, bottom = 600)
        val hole = Rect(0f, 100f, 500f, 500f)
        val offset = OnboardingPlacement.resolve(small, hole, cardWidth = 400, cardHeight = 300, gap = 20)
        assertTrue(offset.x >= small.left && offset.x + 400 <= small.right)
        assertTrue(offset.y >= small.top && offset.y + 300 <= small.bottom)
    }

    @Test
    fun horizontalCenteringIsClampedToArea() {
        val hole = Rect(900f, 200f, 1000f, 300f)
        val offset = OnboardingPlacement.resolve(area, hole, cardWidth = 400, cardHeight = 300, gap = 20)
        assertEquals(600, offset.x)
    }

    @Test
    fun cardLargerThanArea_doesNotThrow() {
        val tiny = IntRect(left = 0, top = 0, right = 100, bottom = 100)
        val offset = OnboardingPlacement.resolve(tiny, Rect(10f, 10f, 20f, 20f), cardWidth = 400, cardHeight = 300, gap = 20)
        assertEquals(0, offset.x)
    }
}
