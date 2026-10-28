package com.aliothmoon.maameow.remote.internal

import android.view.Display
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.domain.models.UnlockStep
import com.aliothmoon.maameow.maa.InputControlUtils
import kotlin.math.roundToInt

internal sealed interface InjectAction {
    data class Sleep(val ms: Long) : InjectAction
    data class Down(val x: Int, val y: Int) : InjectAction
    data class Move(val x: Int, val y: Int) : InjectAction
    data class Up(val x: Int, val y: Int) : InjectAction
}

/** 步骤序列 → 注入时间轴；分辨率有出入时按比例缩放 */
internal object UnlockGestureReplay {

    const val TAP_HOLD_MS = 60

    fun timeline(gesture: UnlockGesture, targetWidth: Int, targetHeight: Int): List<InjectAction> {
        if (gesture.screenWidth <= 0 || gesture.screenHeight <= 0) return emptyList()
        val scaleX = targetWidth.toDouble() / gesture.screenWidth
        val scaleY = targetHeight.toDouble() / gesture.screenHeight

        fun mapX(x: Int) = (x * scaleX).roundToInt().coerceIn(0, targetWidth - 1)
        fun mapY(y: Int) = (y * scaleY).roundToInt().coerceIn(0, targetHeight - 1)

        val out = mutableListOf<InjectAction>()
        for (step in gesture.steps) {
            if (step.delayBeforeMs > 0) out += InjectAction.Sleep(step.delayBeforeMs.toLong())
            when (step) {
                is UnlockStep.Tap -> out += press(mapX(step.x), mapY(step.y), TAP_HOLD_MS.toLong())

                is UnlockStep.LongPress ->
                    out += press(mapX(step.x), mapY(step.y), step.holdMs.toLong())

                is UnlockStep.Swipe -> {
                    // 轨迹来自持久化文件，少于两点按点击兜底
                    val points = step.points
                    if (points.size < 2) {
                        points.firstOrNull()?.let {
                            out += press(mapX(it.x), mapY(it.y), TAP_HOLD_MS.toLong())
                        }
                    } else {
                        out += InjectAction.Down(mapX(points[0].x), mapY(points[0].y))
                        for (i in 1 until points.size) {
                            val dt = (points[i].tMs - points[i - 1].tMs).toLong()
                            if (dt > 0) out += InjectAction.Sleep(dt)
                            val x = mapX(points[i].x)
                            val y = mapY(points[i].y)
                            out += if (i == points.lastIndex) {
                                InjectAction.Up(x, y)
                            } else {
                                InjectAction.Move(x, y)
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    /**
     * 按时间轴注入到主屏，与任务共用 [InputControlUtils]
     * 回放时不会有任务在跑：定时启动先跳过或停掉在跑的任务，设置页自测与录制有 TASK_RUNNING 拦截
     */
    fun execute(actions: List<InjectAction>, touch: ReplayTouch = MainDisplayTouch): ReplayStats {
        // 残留的多指槽位会把这一笔规划成 POINTER_DOWN，先清掉
        touch.cancel()
        var downs = 0
        var downsOk = 0
        var events = 0
        var eventsOk = 0
        try {
            for (action in actions) {
                val ok = when (action) {
                    is InjectAction.Sleep -> {
                        Thread.sleep(action.ms)
                        continue
                    }

                    is InjectAction.Down -> touch.down(action.x, action.y).also {
                        downs++
                        if (it) downsOk++
                    }

                    is InjectAction.Move -> touch.move(action.x, action.y)
                    is InjectAction.Up -> touch.up(action.x, action.y)
                }
                events++
                if (ok) eventsOk++
            }
        } finally {
            // 中途被打断或 UP 没送达，别把手指留在屏上
            touch.cancel()
        }
        return ReplayStats(downs = Delivery.of(downsOk, downs), events = Delivery.of(eventsOk, events))
    }

    private fun press(x: Int, y: Int, holdMs: Long): List<InjectAction> = listOf(
        InjectAction.Down(x, y),
        InjectAction.Sleep(holdMs.coerceAtLeast(0)),
        InjectAction.Up(x, y),
    )
}

/** 送达情况只分全部/部分/没有，不带个数：点按录的手势个数就是 PIN 位数 */
internal enum class Delivery(val label: String) {
    NONE("none"),
    PARTIAL("partial"),
    ALL("all");

    companion object {
        fun of(ok: Int, total: Int) = when {
            ok == 0 -> NONE
            ok == total -> ALL
            else -> PARTIAL
        }
    }
}

/** 一次回放的注入结果；不带坐标，轨迹等价于凭证 */
internal data class ReplayStats(val downs: Delivery, val events: Delivery) {
    val allAccepted: Boolean get() = events == Delivery.ALL

    override fun toString() = "downs ${downs.label}, events ${events.label}"
}

/** 回放的注入出口，单测替换 */
internal interface ReplayTouch {
    fun down(x: Int, y: Int): Boolean
    fun move(x: Int, y: Int): Boolean
    fun up(x: Int, y: Int): Boolean
    fun cancel()
}

/** 主屏单指 */
private object MainDisplayTouch : ReplayTouch {
    override fun down(x: Int, y: Int) =
        InputControlUtils.down(x, y, InputControlUtils.SINGLE_CONTACT, Display.DEFAULT_DISPLAY)

    override fun move(x: Int, y: Int) =
        InputControlUtils.move(x, y, InputControlUtils.SINGLE_CONTACT, Display.DEFAULT_DISPLAY)

    override fun up(x: Int, y: Int) =
        InputControlUtils.up(x, y, InputControlUtils.SINGLE_CONTACT, Display.DEFAULT_DISPLAY)

    override fun cancel() = InputControlUtils.cancel(Display.DEFAULT_DISPLAY)
}
