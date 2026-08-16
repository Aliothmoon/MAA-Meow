package com.aliothmoon.maameow.remote.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 输入设备原始坐标 → 逻辑屏幕坐标 */
class TouchCoordMapperTest {

    @Test
    fun rawCoordsScaleToScreenAtRotationZero() {
        val device = TouchDeviceInfo("/dev/input/event2", "touch", 0, 1439, 0, 2959)

        assertEquals(0 to 0, TouchCoordMapper.mapPoint(0, 0, device, ScreenGeometry(1080, 2220, 0)))
        assertEquals(1079 to 2219, TouchCoordMapper.mapPoint(1439, 2959, device, ScreenGeometry(1080, 2220, 0)))
        assertEquals(540 to 1110, TouchCoordMapper.mapPoint(720, 1480, device, ScreenGeometry(1080, 2220, 0)))
    }

    @Test
    fun mappedPointsStayInsideScreenForEveryRotation() {
        val device = TouchDeviceInfo("/dev/input/event2", "touch", 0, 1439, 0, 2959)
        for (rotation in 0..3) {
            // 横屏时逻辑宽高互换
            val w = if (rotation % 2 == 0) 1080 else 2220
            val h = if (rotation % 2 == 0) 2220 else 1080
            for (rawX in listOf(0, 700, 1439)) {
                for (rawY in listOf(0, 1500, 2959)) {
                    val (x, y) =
                        TouchCoordMapper.mapPoint(rawX, rawY, device, ScreenGeometry(w, h, rotation))
                    assertTrue("rotation=$rotation ($x,$y)", x in 0 until w && y in 0 until h)
                }
            }
        }
    }

    @Test
    fun naturalToLogicalIsIdentityAtRotationZero() {
        assertEquals(37 to 99, TouchCoordMapper.naturalToLogical(37, 99, 1080, 2220, 0))
    }
}
