package com.aliothmoon.maameow.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** 模糊滑块 100% 对应的最大模糊半径。 */
val MaxBackgroundBlur: Dp = 24.dp

/**
 * 主界面自定义背景绘制层：全屏铺满背景图 → 遮罩 → 内容。
 *
 * 纯 UI，不含状态获取；由 MainScreen 注入位图与参数并包裹四个 Tab 的 Scaffold。
 *
 * @param scrimColor 遮罩基色（一般取原始不透明 background），配合 [scrimAlpha] 提升前景可读性。
 * @param blurRadius 模糊半径；仅 API 31+ 实际生效，低版本自动忽略。
 * @param parallax 分页归一化偏移（-0.5~0.5），用于切 Tab 时的视差位移。
 */
@Composable
fun MaaBackgroundHost(
    image: ImageBitmap,
    imageAlpha: Float,
    scrimColor: Color,
    scrimAlpha: Float,
    blurRadius: Dp,
    parallax: Float = 0f,
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
                .graphicsLayer {
                    // 切 Tab 时随分页偏移轻微横移并放大，避免露出边缘。
                    val fraction = abs(parallax)
                    val zoom = 1f + fraction * 0.2f
                    scaleX = zoom
                    scaleY = zoom
                    translationX = parallax * size.width * 0.08f
                }
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

/**
 * 应用级背景包装：有背景图时套用玻璃配色并在内容之下绘制背景，无图时透传内容。
 *
 * 挂到导航根层（[com.aliothmoon.maameow.presentation.navigation.AppNavigation]），
 * 让主界面与所有子页面共用同一背景与玻璃配色；[monetFromWallpaper] 为真时用背景图做原生莫奈取色。
 */
@Composable
fun AppBackgroundHost(
    image: ImageBitmap?,
    imageAlpha: Float,
    scrimAlpha: Float,
    blurRadius: Dp,
    monetFromWallpaper: Boolean,
    parallax: MutableFloatState,
    content: @Composable () -> Unit,
) {
    // 有图与无图分属两个组合位置，各调一次 content 会让 Compose 整棵拆掉重建：
    // pager 页码、子树里所有 remember 全丢。movableContent 让子树带着状态原样搬过去
    val hostedContent = remember(content) { movableContentOf { content() } }

    if (image == null) {
        hostedContent()
        return
    }
    val baseScheme = MaterialTheme.colorScheme
    val isDark = baseScheme.background.luminance() < 0.5f
    // 取色（含缩放与大图取样）较重，放后台线程算；算完前先用原配色，避免组合期阻塞主线程。
    var monetScheme by remember(image, isDark, monetFromWallpaper) {
        mutableStateOf<ColorScheme?>(null)
    }
    LaunchedEffect(image, isDark, monetFromWallpaper) {
        monetScheme = if (monetFromWallpaper) {
            withContext(Dispatchers.Default) { monetColorScheme(image, isDark) }
        } else {
            null
        }
    }
    val effectiveBase = monetScheme ?: baseScheme
    val glassScheme = remember(effectiveBase) { effectiveBase.toGlass() }
    // 让 OpaqueTheme 复用当前配色（含莫奈取色结果），而不是外层 MaaMeowTheme 的原配色。
    CompositionLocalProvider(LocalOpaqueColorScheme provides effectiveBase) {
        ProvideColorScheme(glassScheme) {
            MaaBackgroundHost(
                image = image,
                imageAlpha = imageAlpha,
                scrimColor = effectiveBase.background,
                scrimAlpha = scrimAlpha,
                blurRadius = blurRadius,
                parallax = parallax.floatValue,
                content = hostedContent,
            )
        }
    }
}
