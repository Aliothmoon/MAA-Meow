package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.ABS_MT_POSITION_X
import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.ABS_MT_POSITION_Y
import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.ABS_MT_SLOT
import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.ABS_MT_TRACKING_ID
import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.ABS_X
import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.ABS_Y
import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.BTN_TOUCH
import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.EV_ABS
import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.EV_KEY
import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.EV_SYN
import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.SYN_MT_REPORT
import com.aliothmoon.maameow.remote.internal.TouchStreamParser.Companion.SYN_REPORT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** evdev 事件流 → 单指轨迹 */
class TouchStreamParserTest {

    @Test
    fun protocolBTapProducesSingleStroke() {
        val f = Feeder()
        f.down(100, 200, t = 0)
        f.up(t = 80)

        val strokes = f.parser.strokes
        assertEquals(1, strokes.size)
        assertEquals(TouchPoint(100, 200, 0), strokes[0].points.single())
        assertEquals(80, strokes[0].endTMs)
    }

    @Test
    fun staticContactIsNotRepeated() {
        val f = Feeder()
        f.down(100, 200, t = 0)
        repeat(20) { f.move(100, 200, t = it * 10 + 10) }
        f.up(t = 600)

        // 坐标没变就不重复记点，时长靠 endTMs
        assertEquals(1, f.parser.strokes.single().points.size)
        assertEquals(600, f.parser.strokes.single().endTMs)
    }

    @Test
    fun secondaryFingerIsIgnored() {
        val f = Feeder()
        f.down(100, 200, t = 0)
        // slot 1 的所有事件都不该影响 slot 0 的轨迹
        f.raw(EV_ABS, ABS_MT_SLOT, 1)
        f.raw(EV_ABS, ABS_MT_TRACKING_ID, 2)
        f.raw(EV_ABS, ABS_MT_POSITION_X, 900)
        f.raw(EV_ABS, ABS_MT_POSITION_Y, 900)
        f.syn(t = 20)
        f.raw(EV_ABS, ABS_MT_SLOT, 0)
        f.move(140, 200, t = 40)
        f.up(t = 60)

        val points = f.parser.strokes.single().points
        assertEquals(listOf(100 to 200, 140 to 200), points.map { it.x to it.y })
    }

    @Test
    fun primaryContactOnNonZeroSlotIsCaptured() {
        // 驱动未必从 slot 0 起手，写死 slot 0 会一条轨迹都收不到
        val parser = TouchStreamParser()
        parser.onEvent(EV_ABS, ABS_MT_SLOT, 1, 0)
        parser.onEvent(EV_ABS, ABS_MT_TRACKING_ID, 7, 0)
        parser.onEvent(EV_ABS, ABS_MT_POSITION_X, 300, 0)
        parser.onEvent(EV_ABS, ABS_MT_POSITION_Y, 400, 0)
        parser.onEvent(EV_SYN, SYN_REPORT, 0, 0)
        parser.onEvent(EV_ABS, ABS_MT_POSITION_X, 320, 30)
        parser.onEvent(EV_SYN, SYN_REPORT, 0, 30)
        parser.onEvent(EV_ABS, ABS_MT_TRACKING_ID, -1, 60)
        parser.onEvent(EV_SYN, SYN_REPORT, 0, 60)

        val stroke = parser.strokes.single()
        assertEquals(listOf(300 to 400, 320 to 400), stroke.points.map { it.x to it.y })
        assertEquals(60, stroke.endTMs)
    }

    @Test
    fun coordsAreNotCarriedAcrossStrokes() {
        val f = Feeder()
        f.down(100, 200, t = 0)
        f.up(t = 50)
        // 新触点先来 tracking id、坐标还没到，不该在旧位置补一个假点
        f.raw(EV_ABS, ABS_MT_TRACKING_ID, 9)
        f.syn(t = 100)
        f.raw(EV_ABS, ABS_MT_POSITION_X, 700)
        f.raw(EV_ABS, ABS_MT_POSITION_Y, 800)
        f.syn(t = 130)
        f.up(t = 160)

        assertEquals(2, f.parser.strokes.size)
        assertEquals(listOf(100 to 200), f.parser.strokes[0].points.map { it.x to it.y })
        assertEquals(listOf(700 to 800), f.parser.strokes[1].points.map { it.x to it.y })
    }

    @Test
    fun danglingDownIsClosedByFinish() {
        val f = Feeder()
        f.down(10, 10, t = 0)
        f.move(200, 300, t = 50)
        f.parser.finish(tMs = 90)

        val stroke = f.parser.strokes.single()
        assertEquals(90, stroke.endTMs)
        assertEquals(2, stroke.points.size)
    }

    @Test
    fun protocolAUsesSynMtReport() {
        val parser = TouchStreamParser()
        // 第一根手指的坐标在首个 SYN_MT_REPORT 之前
        parser.onEvent(EV_ABS, ABS_MT_POSITION_X, 50, 0)
        parser.onEvent(EV_ABS, ABS_MT_POSITION_Y, 60, 0)
        parser.onEvent(EV_SYN, SYN_MT_REPORT, 0, 0)
        // 第二根手指应被忽略
        parser.onEvent(EV_ABS, ABS_MT_POSITION_X, 800, 0)
        parser.onEvent(EV_ABS, ABS_MT_POSITION_Y, 900, 0)
        parser.onEvent(EV_SYN, SYN_MT_REPORT, 0, 0)
        parser.onEvent(EV_SYN, SYN_REPORT, 0, 0)
        // 空帧 = 抬起
        parser.onEvent(EV_SYN, SYN_REPORT, 0, 40)

        val stroke = parser.strokes.single()
        assertEquals(TouchPoint(50, 60, 0), stroke.points.single())
        assertEquals(40, stroke.endTMs)
    }

    @Test
    fun legacySingleTouchUsesBtnTouch() {
        val parser = TouchStreamParser()
        parser.onEvent(EV_KEY, BTN_TOUCH, 1, 0)
        parser.onEvent(EV_ABS, ABS_X, 300, 0)
        parser.onEvent(EV_ABS, ABS_Y, 400, 0)
        parser.onEvent(EV_SYN, SYN_REPORT, 0, 0)
        parser.onEvent(EV_KEY, BTN_TOUCH, 0, 70)
        parser.onEvent(EV_SYN, SYN_REPORT, 0, 70)

        val stroke = parser.strokes.single()
        assertEquals(TouchPoint(300, 400, 0), stroke.points.single())
        assertEquals(70, stroke.endTMs)
    }

    /** 按 MT 协议 B 造事件流 */
    private class Feeder(val parser: TouchStreamParser = TouchStreamParser()) {
        private var trackingId = 0

        fun raw(type: Int, code: Int, value: Int) = parser.onEvent(type, code, value, 0)

        fun syn(t: Int) = parser.onEvent(EV_SYN, SYN_REPORT, 0, t)

        fun down(x: Int, y: Int, t: Int) {
            parser.onEvent(EV_ABS, ABS_MT_TRACKING_ID, ++trackingId, t)
            parser.onEvent(EV_ABS, ABS_MT_POSITION_X, x, t)
            parser.onEvent(EV_ABS, ABS_MT_POSITION_Y, y, t)
            parser.onEvent(EV_KEY, BTN_TOUCH, 1, t)
            syn(t)
        }

        fun move(x: Int, y: Int, t: Int) {
            parser.onEvent(EV_ABS, ABS_MT_POSITION_X, x, t)
            parser.onEvent(EV_ABS, ABS_MT_POSITION_Y, y, t)
            syn(t)
        }

        fun up(t: Int) {
            parser.onEvent(EV_ABS, ABS_MT_TRACKING_ID, -1, t)
            parser.onEvent(EV_KEY, BTN_TOUCH, 0, t)
            syn(t)
        }
    }
}
