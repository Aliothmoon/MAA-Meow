package com.aliothmoon.maameow.remote.internal

import android.view.Display
import com.aliothmoon.maameow.third.wrappers.ServiceManager

/** 触摸屏的坐标量程，来自 getevent -p */
internal data class TouchDeviceInfo(
    val path: String,
    val name: String,
    val absXMin: Int,
    val absXMax: Int,
    val absYMin: Int,
    val absYMax: Int,
) {
    val rangeX: Int get() = absXMax - absXMin + 1
    val rangeY: Int get() = absYMax - absYMin + 1
    val usable: Boolean get() = rangeX > 1 && rangeY > 1
}

/** 录制/回放当时的屏幕尺寸与方向，三者必须同时采样 */
internal data class ScreenGeometry(val width: Int, val height: Int, val rotation: Int) {
    companion object {
        fun current(): ScreenGeometry {
            val info = ServiceManager.getDisplayManager().getDisplayInfo(Display.DEFAULT_DISPLAY)
            return ScreenGeometry(info.size().width(), info.size().height(), info.rotation())
        }
    }
}

/** 输入设备原始坐标 → 当前旋转下的逻辑屏幕坐标 */
internal object TouchCoordMapper {

    fun mapStrokes(
        strokes: List<TouchStroke>,
        device: TouchDeviceInfo,
        screen: ScreenGeometry,
    ): List<TouchStroke> = strokes.map { stroke ->
        TouchStroke(
            points = stroke.points.map { p ->
                val (x, y) = mapPoint(p.x, p.y, device, screen)
                TouchPoint(x, y, p.tMs)
            },
            endTMs = stroke.endTMs,
        )
    }

    fun mapPoint(
        rawX: Int,
        rawY: Int,
        device: TouchDeviceInfo,
        screen: ScreenGeometry,
    ): Pair<Int, Int> {
        // 面板坐标轴固定在自然方向上，先还原自然尺寸
        val naturalWidth = if (screen.rotation % 2 == 0) screen.width else screen.height
        val naturalHeight = if (screen.rotation % 2 == 0) screen.height else screen.width

        val nx = ((rawX - device.absXMin).toLong() * naturalWidth / device.rangeX)
            .toInt().coerceIn(0, naturalWidth - 1)
        val ny = ((rawY - device.absYMin).toLong() * naturalHeight / device.rangeY)
            .toInt().coerceIn(0, naturalHeight - 1)

        return naturalToLogical(nx, ny, naturalWidth, naturalHeight, screen.rotation)
    }

    /** rotation 非 0 时锁屏方向与自然方向不一致，需要转一次 */
    fun naturalToLogical(
        nx: Int,
        ny: Int,
        naturalWidth: Int,
        naturalHeight: Int,
        rotation: Int,
    ): Pair<Int, Int> = when (rotation) {
        1 -> ny to (naturalWidth - 1 - nx)
        2 -> (naturalWidth - 1 - nx) to (naturalHeight - 1 - ny)
        3 -> (naturalHeight - 1 - ny) to nx
        else -> nx to ny
    }
}
