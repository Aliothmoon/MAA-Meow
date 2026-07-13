package com.aliothmoon.maameow.presentation.view.settings

import androidx.compose.ui.geometry.Offset
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
    private const val MIN_SCALE_FLOOR = 0.1f
    private const val MAX_SCALE = 5f

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
    ): Offset {
        val relativeX = centroidX - centerX - panX
        val relativeY = centroidY - centerY - panY
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        val transformedX = zoom * (relativeX * cosine - relativeY * sine)
        val transformedY = zoom * (relativeX * sine + relativeY * cosine)
        return Offset(
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
            return MIN_SCALE_FLOOR
        }
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cosine = abs(cos(radians)).toFloat()
        val sine = abs(sin(radians)).toFloat()
        val requiredHalfWidth = cropWidth * cosine / 2f + cropHeight * sine / 2f
        val requiredHalfHeight = cropWidth * sine / 2f + cropHeight * cosine / 2f
        return max(
            requiredHalfWidth / (displayWidth / 2f),
            requiredHalfHeight / (displayHeight / 2f),
        ).coerceAtLeast(MIN_SCALE_FLOOR)
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
        val constrainedScale = scale.coerceIn(minimumScale, max(MAX_SCALE, minimumScale))
        if (displayWidth <= 0f || displayHeight <= 0f || cropWidth <= 0f || cropHeight <= 0f) {
            return WallpaperCropTransform(constrainedScale, panX, panY)
        }

        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        val requiredHalfWidth = (cropWidth * abs(cosine) + cropHeight * abs(sine)) / 2f
        val requiredHalfHeight = (cropWidth * abs(sine) + cropHeight * abs(cosine)) / 2f
        val maxLocalX = (displayWidth * constrainedScale / 2f - requiredHalfWidth).coerceAtLeast(0f)
        val maxLocalY = (displayHeight * constrainedScale / 2f - requiredHalfHeight).coerceAtLeast(0f)

        val localX = panX * cosine + panY * sine
        val localY = -panX * sine + panY * cosine
        val constrainedX = localX.coerceIn(-maxLocalX, maxLocalX)
        val constrainedY = localY.coerceIn(-maxLocalY, maxLocalY)
        return WallpaperCropTransform(
            scale = constrainedScale,
            panX = constrainedX * cosine - constrainedY * sine,
            panY = constrainedX * sine + constrainedY * cosine,
        )
    }
}
