package com.aliothmoon.maameow.presentation.view.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WallpaperCropStateTest {

    private fun readyState(
        screenW: Float = 1080f,
        screenH: Float = 1920f,
        cropW: Float = 756f,
        cropH: Float = 1344f,
        imageW: Float = 2000f,
        imageH: Float = 1500f,
    ): CropState = CropState().apply {
        this.screenW = screenW
        this.screenH = screenH
        this.cropW = cropW
        this.cropH = cropH
        this.cropLeft = (screenW - cropW) / 2f
        this.cropTop = (screenH - cropH) / 2f
        this.imageW = imageW
        this.imageH = imageH
    }

    @Test
    fun ensureCoverScale_boostsScaleToCoverCropWindow() {
        val s = readyState()
        s.scale = 0.1f
        s.ensureCoverScale()
        assertTrue("scale should cover crop: ${s.scale}", s.scale >= 0.1f)
        // After cover, pan must stay within bounds for the boosted scale.
        val panX = s.panX
        val panY = s.panY
        s.clampPan()
        assertEquals(panX, s.panX, 0.01f)
        assertEquals(panY, s.panY, 0.01f)
    }

    @Test
    fun clampPan_limitsOverPan() {
        val s = readyState()
        s.scale = 1f
        s.ensureCoverScale()
        s.panX = 99999f
        s.panY = -99999f
        s.clampPan()
        assertTrue(abs(s.panX) < 99999f)
        assertTrue(abs(s.panY) < 99999f)
    }

    @Test
    fun rotateBy_snapsNinetyDegreeSteps() {
        val s = readyState()
        s.scale = 1f
        s.ensureCoverScale()
        s.rotateBy(90f)
        assertEquals(90f, s.rotationDegrees, 0.01f)
        s.rotateBy(90f)
        assertEquals(180f, s.rotationDegrees, 0.01f)
    }

    @Test
    fun applyTransform_oneToOneRotationAroundPivotDoesNotNaN() {
        val s = readyState()
        s.scale = 1.2f
        s.ensureCoverScale()
        s.applyTransform(
            zoom = 1f,
            panDeltaX = 0f,
            panDeltaY = 0f,
            rotationDegreesDelta = 15f,
            pivotX = s.screenW / 2f,
            pivotY = s.screenH / 2f,
        )
        assertTrue(s.scale.isFinite())
        assertTrue(s.panX.isFinite())
        assertTrue(s.panY.isFinite())
        assertTrue(s.rotationDegrees.isFinite())
        assertEquals(15f, s.rotationDegrees, 0.5f)
    }

    @Test
    fun restore_rejectsNonFiniteAndCovers() {
        val s = readyState()
        s.restore(
            restoredScale = Float.NaN,
            restoredPanX = Float.POSITIVE_INFINITY,
            restoredPanY = 3f,
            restoredRotation = 45f,
            fallbackScale = 1.5f,
        )
        // NaN scale falls back, then ensureCoverScale may boost further to cover.
        assertTrue(s.scale >= 1.5f)
        assertTrue(s.scale.isFinite())
        assertEquals(0f, s.panX, 0.01f) // non-finite panX -> 0
        assertTrue(s.panY.isFinite())
        assertEquals(45f, s.rotationDegrees, 0.01f)
    }
}
