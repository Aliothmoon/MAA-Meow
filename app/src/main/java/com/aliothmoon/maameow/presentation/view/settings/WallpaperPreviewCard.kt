package com.aliothmoon.maameow.presentation.view.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.theme.rememberWallpaperDisplayBitmap
import com.aliothmoon.maameow.theme.wallpaperBlurModifier
import com.aliothmoon.maameow.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun WallpaperPreviewCard(
    wallpaperUri: String,
    version: Int = 0,
    alpha: Float,
    blurPercent: Int,
    screenRatio: Float,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Same file path is overwritten on every recrop; version forces reload.
    // Clear immediately so we never keep showing a stale frame while decoding.
    LaunchedEffect(wallpaperUri, version) {
        previewBitmap = null
        if (wallpaperUri.isEmpty()) return@LaunchedEffect
        val requestUri = wallpaperUri
        val requestVersion = version
        val loaded = withContext(Dispatchers.IO) {
            BitmapUtils.loadDownsampledBitmap(context, requestUri, maxDimension = 512)
        }
        // Drop late results from an older recrop / URI.
        if (requestUri == wallpaperUri && requestVersion == version) {
            previewBitmap = loaded
        }
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
            .wrapContentSize(Alignment.Center)
            .width(180.dp)
            .aspectRatio(screenRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Software path pre-blurs; hardware path uses Modifier.blur.
        val bitmap = rememberWallpaperDisplayBitmap(
            source = previewBitmap,
            blurPercent = blurPercent,
            softwareMaxDimension = 360,
        )
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = alpha,
                modifier = Modifier
                    .fillMaxSize()
                    .then(wallpaperBlurModifier(blurPercent)),
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}
