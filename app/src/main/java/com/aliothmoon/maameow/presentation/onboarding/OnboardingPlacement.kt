package com.aliothmoon.maameow.presentation.onboarding

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToInt

/** 引导卡片相对挖洞的摆放，纯函数便于单测 */
object OnboardingPlacement {

    /**
     * 优先洞下方，其次上方；上下都放不下试左右；仍不够则贴空间更大的一侧、允许压住洞
     *
     * @param area 可用区域（已扣安全区与边距），像素
     * @param hole 挖洞矩形，null 时卡片居中
     * @param gap  卡片与洞的间距
     */
    fun resolve(
        area: IntRect,
        hole: Rect?,
        cardWidth: Int,
        cardHeight: Int,
        gap: Int,
    ): IntOffset {
        if (hole == null) {
            return IntOffset(
                x = area.left + (area.width - cardWidth) / 2,
                y = area.top + (area.height - cardHeight) / 2,
            )
        }
        val holeLeft = hole.left.roundToInt()
        val holeTop = hole.top.roundToInt()
        val holeRight = hole.right.roundToInt()
        val holeBottom = hole.bottom.roundToInt()

        val centeredX = clamp(
            hole.center.x.roundToInt() - cardWidth / 2,
            area.left,
            area.right - cardWidth,
        )
        val spaceBelow = area.bottom - holeBottom
        val spaceAbove = holeTop - area.top
        val needVertical = cardHeight + gap
        if (spaceBelow >= needVertical) return IntOffset(centeredX, holeBottom + gap)
        if (spaceAbove >= needVertical) return IntOffset(centeredX, holeTop - gap - cardHeight)

        val centeredY = clamp(
            hole.center.y.roundToInt() - cardHeight / 2,
            area.top,
            area.bottom - cardHeight,
        )
        val needHorizontal = cardWidth + gap
        if (area.right - holeRight >= needHorizontal) return IntOffset(holeRight + gap, centeredY)
        if (holeLeft - area.left >= needHorizontal) return IntOffset(holeLeft - gap - cardWidth, centeredY)

        val y = if (spaceBelow >= spaceAbove) area.bottom - cardHeight else area.top
        return IntOffset(centeredX, clamp(y, area.top, area.bottom - cardHeight))
    }

    /** 卡片比区域还大时贴左/上，不抛 */
    private fun clamp(value: Int, min: Int, max: Int): Int =
        if (max < min) min else value.coerceIn(min, max)
}
