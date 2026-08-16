package com.aliothmoon.maameow.remote.internal

/** 触摸采样点；坐标为输入设备原始值 */
internal data class TouchPoint(val x: Int, val y: Int, val tMs: Int)

/** 一次 down→up 的完整轨迹，[endTMs] 为抬起时刻 */
internal data class TouchStroke(val points: List<TouchPoint>, val endTMs: Int)

/**
 * evdev 事件流 → 单指轨迹
 * 只跟主触点：PIN、图案、上滑都是单指，多指对解锁没有意义
 * 兼容 MT 协议 B（ABS_MT_TRACKING_ID）、协议 A（SYN_MT_REPORT）与老式单点（BTN_TOUCH + ABS_X/Y）
 */
internal class TouchStreamParser {

    private val _strokes = mutableListOf<TouchStroke>()
    val strokes: List<TouchStroke> get() = _strokes

    private var slot = 0

    /** 第一个按下的 slot 即主触点；驱动未必从 slot 0 起手 */
    private var primarySlot = NO_SLOT
    private var usesTrackingId = false
    private var btnTouch = false
    private var usesBtnTouch = false
    private var usesMt = false

    private var curX = -1
    private var curY = -1
    private var frameHasCoords = false

    /** 协议 A 一帧里的第几根手指，只认第 0 根 */
    private var mtFrameIndex = 0

    private var current: MutableList<TouchPoint>? = null

    fun onEvent(type: Int, code: Int, value: Int, tMs: Int) {
        when (type) {
            EV_ABS -> onAbs(code, value)

            EV_KEY -> if (code == BTN_TOUCH) {
                usesBtnTouch = true
                btnTouch = value != 0
            }

            EV_SYN -> when (code) {
                SYN_MT_REPORT -> mtFrameIndex++
                SYN_REPORT -> {
                    commitFrame(tMs)
                    frameHasCoords = false
                    mtFrameIndex = 0
                }
            }
        }
    }

    /** 流结束仍有未抬起的手指时补一个抬起，避免丢掉最后一步 */
    fun finish(tMs: Int) = closeStroke(tMs)

    private fun onAbs(code: Int, value: Int) {
        when (code) {
            ABS_MT_SLOT -> slot = value

            ABS_MT_TRACKING_ID -> {
                usesTrackingId = true
                if (value >= 0) {
                    if (primarySlot == NO_SLOT) primarySlot = slot
                } else if (slot == primarySlot) {
                    primarySlot = NO_SLOT
                }
            }

            ABS_MT_POSITION_X -> {
                usesMt = true
                if (isPrimaryContact()) {
                    curX = value
                    frameHasCoords = true
                }
            }

            ABS_MT_POSITION_Y -> {
                usesMt = true
                if (isPrimaryContact()) {
                    curY = value
                    frameHasCoords = true
                }
            }

            // 同时上报 MT 与单点时以 MT 为准
            ABS_X -> if (!usesMt) {
                curX = value
                frameHasCoords = true
            }

            ABS_Y -> if (!usesMt) {
                curY = value
                frameHasCoords = true
            }
        }
    }

    private fun isPrimaryContact() = mtFrameIndex == 0 &&
        if (usesTrackingId) slot == primarySlot else slot == 0

    private fun commitFrame(tMs: Int) {
        val down = when {
            usesTrackingId -> primarySlot != NO_SLOT
            usesBtnTouch -> btnTouch
            // 协议 A 且无 BTN_TOUCH：空帧即抬起
            else -> frameHasCoords
        }
        if (down && curX >= 0 && curY >= 0) {
            appendPoint(tMs)
        } else {
            closeStroke(tMs)
        }
    }

    private fun appendPoint(tMs: Int) {
        val points = current ?: mutableListOf<TouchPoint>().also { current = it }
        if (points.size >= MAX_POINTS_PER_STROKE) return
        val last = points.lastOrNull()
        // 静止不重复记点，时长由 endTMs 兜住
        if (last != null && last.x == curX && last.y == curY) return
        points.add(TouchPoint(curX, curY, tMs))
    }

    private fun closeStroke(tMs: Int) {
        val points = current
        current = null
        // 抬起后清掉坐标，免得下一次按下先补一个落在旧位置的假点
        curX = -1
        curY = -1
        if (points == null || points.isEmpty()) return
        if (_strokes.size >= MAX_STROKES) return
        _strokes.add(TouchStroke(points, tMs.coerceAtLeast(points.last().tMs)))
    }

    companion object {
        const val EV_SYN = 0x00
        const val EV_KEY = 0x01
        const val EV_ABS = 0x03

        const val SYN_REPORT = 0x00
        const val SYN_MT_REPORT = 0x02

        const val BTN_TOUCH = 0x14a

        const val ABS_X = 0x00
        const val ABS_Y = 0x01
        const val ABS_MT_SLOT = 0x2f
        const val ABS_MT_POSITION_X = 0x35
        const val ABS_MT_POSITION_Y = 0x36
        const val ABS_MT_TRACKING_ID = 0x39

        const val MAX_POINTS_PER_STROKE = 4000
        const val MAX_STROKES = 128

        private const val NO_SLOT = -1
    }
}
