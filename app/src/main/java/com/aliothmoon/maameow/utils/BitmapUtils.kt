package com.aliothmoon.maameow.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.max

object BitmapUtils {
    fun loadDownsampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048,
    ): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            val maxDim = max(bounds.outWidth, bounds.outHeight)
            val sample = if (maxDim > maxDimension) {
                (maxDim / maxDimension).coerceAtLeast(1)
            } else {
                1
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadDownsampledBitmap(
        context: Context,
        uriString: String,
        maxDimension: Int = 2048,
    ): Bitmap? {
        val uri = parseUri(uriString)
        return loadDownsampledBitmap(context, uri, maxDimension)
    }

    private fun parseUri(uriString: String): Uri {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == null) Uri.fromFile(File(uriString)) else uri
    }
}
