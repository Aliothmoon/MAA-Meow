package com.aliothmoon.maameow.presentation.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.utils.LauncherApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().byteCount
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current.applicationContext
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val key = "$packageName@$sizePx"
    val icon = produceState<ImageBitmap?>(iconCache.get(key), key) {
        value = iconCache.get(key) ?: withContext(Dispatchers.IO) {
            LauncherApps.icon(context, packageName, sizePx).asImageBitmap()
                .also { iconCache.put(key, it) }
        }
    }.value
    if (icon == null) {
        Spacer(modifier.size(size))
    } else {
        Image(bitmap = icon, contentDescription = null, modifier = modifier.size(size))
    }
}
