package com.aliothmoon.maameow.presentation.view.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class WallpaperCropMathTest {

    // ========== minScaleForRotation ==========

    @Test
    fun minScaleForRotation_zeroRotation_fitsCropExactly() {
        // display 1000x800、crop 500x400：min = max(500/1000, 400/800) = 0.5
        val scale = WallpaperCropMath.minScaleForRotation(500f, 400f, 1000f, 800f, 0f)

        assertEquals(0.5f, scale, 1e-4f)
    }

    @Test
    fun minScaleForRotation_rotated90_swapsCropAxes() {
        // 90° 时裁剪框宽高互换：min = max(400/1000, 500/800) = 0.625
        val scale = WallpaperCropMath.minScaleForRotation(500f, 400f, 1000f, 800f, 90f)

        assertEquals(0.625f, scale, 1e-4f)
    }

    @Test
    fun minScaleForRotation_rotated45_requiresLargerScale() {
        // 旋转 45° 需要覆盖的半宽/半高为 (w+h)*√2/4，最小缩放大于 0° 时
        val straight = WallpaperCropMath.minScaleForRotation(500f, 400f, 1000f, 800f, 0f)
        val rotated = WallpaperCropMath.minScaleForRotation(500f, 400f, 1000f, 800f, 45f)

        val halfDiagonal = (500f + 400f) * sin(Math.toRadians(45.0)).toFloat() / 2f
        val expected = maxOf(halfDiagonal / 500f, halfDiagonal / 400f)
        assertEquals(expected, rotated, 1e-4f)
        assert(rotated > straight)
    }

    @Test
    fun minScaleForRotation_invalidDimensions_returnsFloor() {
        assertEquals(0.1f, WallpaperCropMath.minScaleForRotation(0f, 400f, 1000f, 800f, 0f), 0f)
        assertEquals(0.1f, WallpaperCropMath.minScaleForRotation(500f, 400f, 0f, 800f, 0f), 0f)
    }

    // ========== constrainTransform ==========

    @Test
    fun constrainTransform_clampsScaleToMinimum() {
        val result = WallpaperCropMath.constrainTransform(
            scale = 0.1f, panX = 0f, panY = 0f, rotationDegrees = 0f,
            displayWidth = 1000f, displayHeight = 800f,
            cropWidth = 500f, cropHeight = 400f, minimumScale = 0.5f,
        )

        assertEquals(0.5f, result.scale, 1e-4f)
    }

    @Test
    fun constrainTransform_atExactFit_locksPanToZero() {
        // scale=0.5 时缩放后的图片恰好等于裁剪框，任何平移都会露出留白
        val result = WallpaperCropMath.constrainTransform(
            scale = 0.5f, panX = 30f, panY = -20f, rotationDegrees = 0f,
            displayWidth = 1000f, displayHeight = 800f,
            cropWidth = 500f, cropHeight = 400f, minimumScale = 0.5f,
        )

        assertEquals(0f, result.panX, 1e-4f)
        assertEquals(0f, result.panY, 1e-4f)
    }

    @Test
    fun constrainTransform_withinSlack_keepsPanUnchanged() {
        // scale=1：横向余量 (1000-500)/2=250，纵向 (800-400)/2=200
        val result = WallpaperCropMath.constrainTransform(
            scale = 1f, panX = 100f, panY = -150f, rotationDegrees = 0f,
            displayWidth = 1000f, displayHeight = 800f,
            cropWidth = 500f, cropHeight = 400f, minimumScale = 0.5f,
        )

        assertEquals(100f, result.panX, 1e-4f)
        assertEquals(-150f, result.panY, 1e-4f)
    }

    @Test
    fun constrainTransform_beyondSlack_clampsPanToBounds() {
        val result = WallpaperCropMath.constrainTransform(
            scale = 1f, panX = 400f, panY = -300f, rotationDegrees = 0f,
            displayWidth = 1000f, displayHeight = 800f,
            cropWidth = 500f, cropHeight = 400f, minimumScale = 0.5f,
        )

        assertEquals(250f, result.panX, 1e-4f)
        assertEquals(-200f, result.panY, 1e-4f)
    }

    @Test
    fun constrainTransform_rotated90_clampsInRotatedFrame() {
        // 90°：requiredHalf = (400/2, 500/2)，maxLocal = (500-200, 400-250) = (300, 150)
        // pan(0, 100) 的局部坐标为 (100, 0)，均在界内，应保持不变
        val result = WallpaperCropMath.constrainTransform(
            scale = 1f, panX = 0f, panY = 100f, rotationDegrees = 90f,
            displayWidth = 1000f, displayHeight = 800f,
            cropWidth = 500f, cropHeight = 400f, minimumScale = 0.1f,
        )

        assertEquals(0f, result.panX, 1e-3f)
        assertEquals(100f, result.panY, 1e-3f)
    }

    // ========== transformAroundCentroid ==========

    @Test
    fun transformAroundCentroid_purePan_addsGesturePan() {
        val result = WallpaperCropMath.transformAroundCentroid(
            panX = 10f, panY = 20f,
            centroidX = 600f, centroidY = 500f, centerX = 500f, centerY = 400f,
            gesturePanX = 30f, gesturePanY = -40f, zoom = 1f, rotationDegrees = 0f,
        )

        assertEquals(40f, result.x, 1e-4f)
        assertEquals(-20f, result.y, 1e-4f)
    }

    @Test
    fun transformAroundCentroid_zoomAndRotate_keepsCentroidContentFixed() {
        // 性质：变换前位于质心处的图片内容，变换后应位于「质心 + 手势平移」处。
        val panX = 10f
        val panY = 20f
        val centroidX = 620f
        val centroidY = 480f
        val centerX = 500f
        val centerY = 400f
        val gesturePanX = 15f
        val gesturePanY = -25f
        val zoom = 1.8f
        val rotation = 33f

        val newPan = WallpaperCropMath.transformAroundCentroid(
            panX = panX,
            panY = panY,
            centroidX = centroidX,
            centroidY = centroidY,
            centerX = centerX,
            centerY = centerY,
            gesturePanX = gesturePanX,
            gesturePanY = gesturePanY,
            zoom = zoom,
            rotationDegrees = rotation,
        )

        // 原本位于质心的内容点相对图片中心的向量，经 zoom+rotation 后的新屏幕位置
        val radians = Math.toRadians(rotation.toDouble())
        val cosR = cos(radians).toFloat()
        val sinR = sin(radians).toFloat()
        val relX = centroidX - centerX - panX
        val relY = centroidY - centerY - panY
        val movedX = centerX + newPan.x + zoom * (relX * cosR - relY * sinR)
        val movedY = centerY + newPan.y + zoom * (relX * sinR + relY * cosR)

        assertEquals(centroidX + gesturePanX, movedX, 1e-3f)
        assertEquals(centroidY + gesturePanY, movedY, 1e-3f)
    }
}
