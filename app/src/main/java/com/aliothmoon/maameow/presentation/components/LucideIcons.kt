package com.aliothmoon.maameow.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** lucide 线性图标（ISC），Material 里没有对应款 */
object LucideIcons {

    val Swords: ImageVector by lazy {
        stroke(
            "Swords",
            "M13 19 L19 13",
            "M14.5 17.5 L3.586 6.586 A2 2 0 0 1 3 5.172 V3 h2.172 a2 2 0 0 1 1.414 0.586 L17.5 14.5",
            "M14.828 6.172 l2.586 -2.586 A2 2 0 0 1 18.828 3 H21 v2.172 a2 2 0 0 1 -0.586 1.414 l-2.586 2.586",
            "M16 16 l4 4",
            "M19 21 l2 -2",
            "M5 14 l4 4",
            "M5 21 l-2 -2",
            "M7.5 16.5 L4 20",
        )
    }

    val ListTodo: ImageVector by lazy {
        stroke(
            "ListTodo",
            "M13 5 h8",
            "M13 12 h8",
            "M13 19 h8",
            "M3 17 l2 2 l4 -4",
            "M4 4 h4 a1 1 0 0 1 1 1 v4 a1 1 0 0 1 -1 1 H4 a1 1 0 0 1 -1 -1 V5 a1 1 0 0 1 1 -1 z",
        )
    }

    private fun stroke(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
