package com.aliothmoon.maameow.presentation.view.settings

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal data class WallpaperCropTransform(
    val scale: Float,
    val panX: Float,
    val panY: Float,
)

internal object WallpaperCropMath {
    fun transformAroundCentroid(
        panX: Float,
        panY: Float,
        centroidX: Float,
        centroidY: Float,
        centerX: Float,
        centerY: Float,
        gesturePanX: Float,
        gesturePanY: Float,
        zoom: Float,
        rotationDegrees: Float,
    ): Pair<Float, Float> {
        val relativeX = centroidX - centerX - panX
        val relativeY = centroidY - centerY - panY
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val transformedX = zoom * (relativeX * cos - relativeY * sin)
        val transformedY = zoom * (relativeX * sin + relativeY * cos)
        return Pair(
            centroidX - centerX + gesturePanX - transformedX,
            centroidY - centerY + gesturePanY - transformedY,
        )
    }

    fun minScaleForRotation(
        cropWidth: Float,
        cropHeight: Float,
        displayWidth: Float,
        displayHeight: Float,
        rotationDegrees: Float,
    ): Float {
        if (cropWidth <= 0f || cropHeight <= 0f || displayWidth <= 0f || displayHeight <= 0f) {
            return 0.1f
        }
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cos = abs(cos(radians)).toFloat()
        val sin = abs(sin(radians)).toFloat()
        val requiredHalfWidth = cropWidth * cos / 2f + cropHeight * sin / 2f
        val requiredHalfHeight = cropWidth * sin / 2f + cropHeight * cos / 2f
        return max(
            requiredHalfWidth / (displayWidth / 2f),
            requiredHalfHeight / (displayHeight / 2f),
        ).coerceAtLeast(0.1f)
    }

    fun constrainTransform(
        scale: Float,
        panX: Float,
        panY: Float,
        rotationDegrees: Float,
        displayWidth: Float,
        displayHeight: Float,
        cropWidth: Float,
        cropHeight: Float,
        minimumScale: Float,
    ): WallpaperCropTransform {
        val constrainedScale = scale.coerceIn(minimumScale, max(5f, minimumScale))
        if (displayWidth <= 0f || displayHeight <= 0f || cropWidth <= 0f || cropHeight <= 0f) {
            return WallpaperCropTransform(constrainedScale, panX, panY)
        }

        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val requiredHalfWidth = (cropWidth * abs(cos) + cropHeight * abs(sin)) / 2f
        val requiredHalfHeight = (cropWidth * abs(sin) + cropHeight * abs(cos)) / 2f
        val maxLocalX = (displayWidth * constrainedScale / 2f - requiredHalfWidth).coerceAtLeast(0f)
        val maxLocalY = (displayHeight * constrainedScale / 2f - requiredHalfHeight).coerceAtLeast(0f)

        val localX = panX * cos + panY * sin
        val localY = -panX * sin + panY * cos
        val constrainedX = localX.coerceIn(-maxLocalX, maxLocalX)
        val constrainedY = localY.coerceIn(-maxLocalY, maxLocalY)
        return WallpaperCropTransform(
            scale = constrainedScale,
            panX = constrainedX * cos - constrainedY * sin,
            panY = constrainedX * sin + constrainedY * cos,
        )
    }
}
