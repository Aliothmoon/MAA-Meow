package com.aliothmoon.maameow.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 模糊滑块 100% 对应的最大模糊半径。 */
val MaxBackgroundBlur: Dp = 24.dp

/**
 * 主界面自定义背景绘制层：全屏铺满背景图 → 遮罩 → 内容。
 *
 * 纯 UI，不含状态获取；由 MainScreen 注入位图与参数并包裹四个 Tab 的 Scaffold。
 *
 * @param scrimColor 遮罩基色（一般取原始不透明 background），配合 [scrimAlpha] 提升前景可读性。
 * @param blurRadius 模糊半径；仅 API 31+ 实际生效，低版本自动忽略。
 */
@Composable
fun MaaBackgroundHost(
    image: ImageBitmap,
    imageAlpha: Float,
    scrimColor: Color,
    scrimAlpha: Float,
    blurRadius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = imageAlpha.coerceIn(0f, 1f),
            modifier = Modifier
                .matchParentSize()
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
        )
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(scrimColor.copy(alpha = scrimAlpha.coerceIn(0f, 1f))),
            )
        }
        content()
    }
}
