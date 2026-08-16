package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.domain.models.GesturePoint
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.domain.models.UnlockStep
import kotlin.math.min
import kotlin.math.roundToInt

/** 原始轨迹 → 可展示、可回放的步骤序列 */
internal object UnlockGestureCodec {

    /** 位移在此之内视为没动 */
    const val TAP_MOVE_TOLERANCE_PX = 20

    /** 没动且超过这个时长算长按 */
    const val LONG_PRESS_MIN_MS = 500

    /** 步骤间隔上限，免得把用户的犹豫也照抄回放 */
    const val MAX_GAP_MS = 1500

    /** 抽稀：时间间隔达标或位移够大才保点 */
    const val MIN_POINT_INTERVAL_MS = 8
    const val SHARP_MOVE_PX = 16

    const val MAX_POINTS_PER_SWIPE = 400
    const val MAX_STEPS = 64

    fun build(strokes: List<TouchStroke>, screen: ScreenGeometry): UnlockGesture = UnlockGesture(
        screenWidth = screen.width,
        screenHeight = screen.height,
        rotation = screen.rotation,
        steps = toSteps(strokes),
    )

    fun toSteps(strokes: List<TouchStroke>): List<UnlockStep> {
        val steps = mutableListOf<UnlockStep>()
        var prevEnd: Int? = null
        for (stroke in strokes) {
            if (steps.size >= MAX_STEPS) break
            if (stroke.points.isEmpty()) continue
            val start = stroke.points.first().tMs
            val delay = prevEnd?.let { (start - it).coerceIn(0, MAX_GAP_MS) } ?: 0
            steps += classify(stroke, delay)
            prevEnd = stroke.endTMs
        }
        return steps
    }

    private fun classify(stroke: TouchStroke, delayBeforeMs: Int): UnlockStep {
        val points = stroke.points
        val first = points.first()
        val duration = (stroke.endTMs - first.tMs).coerceAtLeast(0)
        val moved = points.any { farther(it.x - first.x, it.y - first.y, TAP_MOVE_TOLERANCE_PX) }
        val trace = if (moved) decimate(points) else emptyList()
        return when {
            // 抽稀后不足两点的滑动没法回放，按点击处理
            moved && trace.size >= 2 -> UnlockStep.Swipe(trace, delayBeforeMs)

            duration >= LONG_PRESS_MIN_MS ->
                UnlockStep.LongPress(first.x, first.y, duration, delayBeforeMs)

            else -> UnlockStep.Tap(first.x, first.y, delayBeforeMs)
        }
    }

    /** 抽稀并把时间戳转成相对本步骤起点 */
    private fun decimate(points: List<TouchPoint>): List<GesturePoint> {
        val kept = mutableListOf<TouchPoint>()
        for ((i, p) in points.withIndex()) {
            val last = kept.lastOrNull()
            val keep = last == null ||
                i == points.lastIndex ||
                p.tMs - last.tMs >= MIN_POINT_INTERVAL_MS ||
                farther(p.x - last.x, p.y - last.y, SHARP_MOVE_PX - 1)
            if (keep) kept += p
        }
        val sampled = if (kept.size > MAX_POINTS_PER_SWIPE) resample(kept) else kept
        val t0 = sampled.first().tMs
        return sampled.map { GesturePoint(it.x, it.y, it.tMs - t0) }
    }

    /** 等间隔重采样，始终保留首尾 */
    private fun resample(points: List<TouchPoint>): List<TouchPoint> {
        val out = ArrayList<TouchPoint>(MAX_POINTS_PER_SWIPE)
        val step = (points.size - 1).toDouble() / (MAX_POINTS_PER_SWIPE - 1)
        for (i in 0 until MAX_POINTS_PER_SWIPE - 1) {
            out += points[min((i * step).roundToInt(), points.lastIndex)]
        }
        out += points.last()
        return out
    }

    /** 距离只用来和阈值比大小，平方比较即可，省掉每个点两次开方 */
    private fun farther(dx: Int, dy: Int, thresholdPx: Int): Boolean =
        dx.toLong() * dx + dy.toLong() * dy > thresholdPx.toLong() * thresholdPx
}
