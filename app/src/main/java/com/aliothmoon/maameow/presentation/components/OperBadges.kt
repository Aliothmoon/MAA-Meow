package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Rarity6 = Color(0xFFFF6B35)
private val Rarity5 = Color(0xFFFFD700)
private val Rarity4 = Color(0xFF9C7CFF)
private val Rarity3 = Color(0xFF4FC3F7)
private val Rarity2 = Color(0xFFA5D6A7)

/** 干员星级配色，1 星与未知走主题弱色 */
@Composable
@ReadOnlyComposable
fun operRarityColor(rarity: Int): Color = when (rarity) {
    6 -> Rarity6
    5 -> Rarity5
    4 -> Rarity4
    3 -> Rarity3
    2 -> Rarity2
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 单技能格的品字三圆：专 1 亮上圆，专 2 加亮右下圆，专 3 全亮 */
@Composable
fun MasteryBadge(level: Int, modifier: Modifier = Modifier.size(12.dp)) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 5f
        drawCircle(
            if (level >= 1) on else off, radius, Offset(size.width / 2f, radius)
        )
        drawCircle(
            if (level >= 2) on else off, radius, Offset(size.width - radius, size.height - radius)
        )
        drawCircle(
            if (level >= 3) on else off, radius, Offset(radius, size.height - radius)
        )
    }
}
