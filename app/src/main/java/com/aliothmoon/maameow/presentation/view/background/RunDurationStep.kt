package com.aliothmoon.maameow.presentation.view.background

import com.aliothmoon.maameow.domain.models.RunDurationLimit
import java.util.Locale

/** 快捷选项里运行时长上限的步进与剩余时间格式 */
internal object RunDurationStep {
    private const val STEP_MINUTES = 10

    // ± 最低减到一档，1–9 分钟靠手动输入
    private const val FLOOR_MINUTES = STEP_MINUTES

    private const val INPUT_MAX_LENGTH = 4

    fun canDecrease(minutes: Int): Boolean = minutes > FLOOR_MINUTES

    fun canIncrease(minutes: Int): Boolean = minutes < RunDurationLimit.MAX_MINUTES

    /** 非整档先对齐到下一档再步进 */
    fun decrease(minutes: Int): Int =
        ((minutes - 1) / STEP_MINUTES * STEP_MINUTES).coerceIn(FLOOR_MINUTES, RunDurationLimit.MAX_MINUTES)

    fun increase(minutes: Int): Int =
        ((minutes / STEP_MINUTES + 1) * STEP_MINUTES).coerceIn(FLOOR_MINUTES, RunDurationLimit.MAX_MINUTES)

    fun acceptsInput(text: String): Boolean =
        text.length <= INPUT_MAX_LENGTH && text.all { it in '0'..'9' }

    /** 空输入返回 null 表示不改，其余夹到合法范围 */
    fun parseInput(text: String): Int? =
        text.toIntOrNull()?.coerceIn(RunDurationLimit.MIN_MINUTES, RunDurationLimit.MAX_MINUTES)

    /** H:MM:SS，向上取整到秒，到点前不显示 0:00:00 */
    fun formatRemaining(remainingMs: Long): String {
        val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999L) / 1000L
        return String.format(
            Locale.ROOT,
            "%d:%02d:%02d",
            totalSeconds / 3600,
            totalSeconds / 60 % 60,
            totalSeconds % 60,
        )
    }
}
