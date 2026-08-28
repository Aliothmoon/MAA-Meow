package com.aliothmoon.maameow.presentation.state

import com.aliothmoon.maameow.maa.TouchPointerSequence

/**
 * 预览手动触控：Compose 的 PointerId（单调递增 Long）→ 注入用 contact（0..15）；
 * 按下时取最小空闲槽位，抬起后释放，这样第一指总是 contact 0，与 MotionEvent pointer id 语义一致
 */
class PreviewPointerSlots {

    private val owners = LongArray(TouchPointerSequence.MAX_CONTACTS) { NONE }

    /** 幂等；满了返回 -1 */
    fun acquire(pointerId: Long): Int {
        val existing = indexOf(pointerId)
        if (existing >= 0) return existing
        val free = owners.indexOfFirst { it == NONE }
        if (free >= 0) owners[free] = pointerId
        return free
    }

    fun indexOf(pointerId: Long): Int = owners.indexOf(pointerId)

    /** 未占有返回 -1 */
    fun release(pointerId: Long): Int {
        val index = indexOf(pointerId)
        if (index >= 0) owners[index] = NONE
        return index
    }

    private companion object {
        const val NONE = -1L
    }
}
