package com.aliothmoon.maameow.remote.internal

import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build

object DisplayHelper {
    private const val MAX_IMAGES = 5
    fun newInstanceImagerReader(width: Int, height: Int): ImageReader {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ImageReader.newInstance(
                width, height,
                PixelFormat.RGBA_8888,
                MAX_IMAGES,
                HardwareBuffer.USAGE_CPU_READ_OFTEN
                        or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
            )
        } else {
            ImageReader.newInstance(
                width, height,
                PixelFormat.RGBA_8888,
                MAX_IMAGES
            )
        }
    }
}