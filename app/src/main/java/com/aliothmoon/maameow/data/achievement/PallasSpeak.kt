package com.aliothmoon.maameow.data.achievement

import kotlin.random.Random

object PallasSpeak {

    private val CHARS = listOf("💃", "🕺", "🍷", "🍸", "🍺", "🍻", "🥃", "🍶")

    fun random(
        low: Int = 3,
        high: Int = 6,
        cap: Int = Int.MAX_VALUE,
        random: Random = Random,
    ): String {
        if (cap <= 0) return ""
        val drawn = if (high <= low) low else random.nextInt(low, high)
        val len = minOf(drawn, cap)
        return buildString(len * 2) {
            repeat(len) { append(CHARS.random(random)) }
        }
    }
}
