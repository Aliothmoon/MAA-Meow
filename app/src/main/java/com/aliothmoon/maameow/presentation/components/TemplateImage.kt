package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import com.aliothmoon.maameow.data.resource.TemplateImageLoader

/** 加载中或资源缺失时留同尺寸空位，避免布局抖动 */
@Composable
internal fun TemplateImage(
    id: String,
    loader: TemplateImageLoader,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    colorFilter: ColorFilter? = null,
) {
    // 首帧直接用缓存，免得滚回来先空一帧；按 id 重建，同一位置换了 id 不沿用上一张
    var bitmap: ImageBitmap? by remember(id) { mutableStateOf(loader.peek(id)) }
    val generation by loader.generation.collectAsState()
    LaunchedEffect(id, generation) {
        if (bitmap == null) bitmap = loader.load(id)
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = contentDescription,
            modifier = modifier,
            colorFilter = colorFilter,
        )
    } else {
        Box(modifier = modifier)
    }
}
