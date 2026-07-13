package com.aliothmoon.maameow.presentation.view.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import com.aliothmoon.maameow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import androidx.compose.foundation.Canvas as ComposeCanvas

@Stable
internal class WallpaperCropState {
    var scale by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var rotationDegrees by mutableFloatStateOf(0f)
    var initialized by mutableStateOf(false)

    // 布局几何：由裁剪页在组合应用后（SideEffect）写入，仅供手势约束与裁剪输出读取，不驱动重组。
    var screenW = 0f
    var screenH = 0f
    var cropW = 0f
    var cropH = 0f
    var cropLeft = 0f
    var cropTop = 0f
    var displayW = 0f
    var displayH = 0f
    var restoredScreenW = 0f
    var restoredScreenH = 0f

    fun resetTransform(initialScale: Float) {
        scale = initialScale
        panX = 0f
        panY = 0f
        rotationDegrees = 0f
    }

    /** 按当前旋转角度重算最小缩放，并把缩放/平移约束到裁剪框无留白的范围内。 */
    fun applyConstraints() {
        val minScale = WallpaperCropMath.minScaleForRotation(
            cropW, cropH, displayW, displayH, rotationDegrees,
        )
        val constrained = WallpaperCropMath.constrainTransform(
            scale = scale,
            panX = panX,
            panY = panY,
            rotationDegrees = rotationDegrees,
            displayWidth = displayW,
            displayHeight = displayH,
            cropWidth = cropW,
            cropHeight = cropH,
            minimumScale = minScale,
        )
        scale = constrained.scale
        panX = constrained.panX
        panY = constrained.panY
    }

    fun getCroppedBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val sw = screenW
        val sh = screenH
        if (sw <= 0f || sh <= 0f || cropW <= 0f || cropH <= 0f) return null

        val bw = source.width.toFloat()
        val bh = source.height.toFloat()
        val baseScale = min(sw / bw, sh / bh)

        val matrix = Matrix().apply {
            postScale(baseScale, baseScale)
            postTranslate((sw - bw * baseScale) / 2f, (sh - bh * baseScale) / 2f)
            postTranslate(-sw / 2f, -sh / 2f)
            postScale(scale, scale)
            postRotate(rotationDegrees)
            postTranslate(sw / 2f, sh / 2f)
            postTranslate(panX, panY)
            // pan 以裁剪框中心为锚点，这里补上裁剪框中心相对屏幕中心的偏移。
            postTranslate(cropLeft + cropW / 2f - sw / 2f, cropTop + cropH / 2f - sh / 2f)
            postTranslate(-cropLeft, -cropTop)
            postScale(targetWidth / cropW, targetHeight / cropH)
        }

        val output = createBitmap(targetWidth, targetHeight)
        return try {
            Canvas(output).drawBitmap(source, matrix, null)
            output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
    }

    companion object {
        val Saver = listSaver<WallpaperCropState, Any>(
            save = {
                listOf(
                    it.scale, it.panX, it.panY, it.rotationDegrees, it.initialized,
                    it.screenW, it.screenH,
                )
            },
            restore = {
                WallpaperCropState().apply {
                    scale = it[0] as Float
                    panX = it[1] as Float
                    panY = it[2] as Float
                    rotationDegrees = it[3] as Float
                    initialized = it[4] as Boolean
                    restoredScreenW = it[5] as Float
                    restoredScreenH = it[6] as Float
                }
            },
        )
    }
}

/**
 * 全屏裁剪页：以全屏 Dialog 呈现，覆盖底部导航栏；返回键在非保存状态下等同取消。
 */
@Composable
internal fun WallpaperCropFullScreen(
    sourceBitmap: Bitmap,
    cropState: WallpaperCropState,
    onCancel: () -> Unit,
    onConfirm: suspend (Bitmap) -> Unit,
) {
    var isSaving by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (!isSaving) onCancel() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        WallpaperCropContent(
            sourceBitmap = sourceBitmap,
            cropState = cropState,
            isSaving = isSaving,
            onSavingChange = { isSaving = it },
            onCancel = onCancel,
            onConfirm = onConfirm,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallpaperCropContent(
    sourceBitmap: Bitmap,
    cropState: WallpaperCropState,
    isSaving: Boolean,
    onSavingChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: suspend (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var isTouching by remember { mutableStateOf(false) }
    var topBarHeight by remember { mutableIntStateOf(0) }
    var bottomBarHeight by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val totalW = constraints.maxWidth.toFloat()
        val totalH = constraints.maxHeight.toFloat()
        val targetWidth = rootView.width.takeIf { it > 0 } ?: constraints.maxWidth
        val targetHeight = rootView.height.takeIf { it > 0 } ?: constraints.maxHeight
        val screenRatio = targetWidth.toFloat() / targetHeight

        // 裁剪框在顶部工具栏与底部控制栏之间的剩余区域内居中。
        val availableTop = topBarHeight.toFloat()
        val availableH = (totalH - topBarHeight - bottomBarHeight).coerceAtLeast(1f)
        val maxCropW = totalW * 0.70f
        val maxCropH = availableH * 0.62f
        val cropW: Float
        val cropH: Float
        if (maxCropW / screenRatio <= maxCropH) {
            cropW = maxCropW
            cropH = cropW / screenRatio
        } else {
            cropH = maxCropH
            cropW = cropH * screenRatio
        }
        val cropLeft = (totalW - cropW) / 2f
        val cropTop = availableTop + (availableH - cropH) / 2f
        // 裁剪框中心不在整屏中心（底部控制栏更高），图片与变换均以裁剪框中心为锚点，
        // 保证 pan 为 0 时图片与裁剪框同心，且平移约束围绕裁剪框计算。
        val cropCenterY = cropTop + cropH / 2f
        val cropCenterOffsetY = cropCenterY - totalH / 2f

        val bitmapW = sourceBitmap.width.toFloat()
        val bitmapH = sourceBitmap.height.toFloat()
        val baseScale = min(totalW / bitmapW, totalH / bitmapH)
        val displayW = bitmapW * baseScale
        val displayH = bitmapH * baseScale

        // 组合需保持无副作用：布局几何在组合成功应用后（SideEffect）写入状态，
        // 供随后的手势回调与保存流程读取。
        SideEffect {
            cropState.screenW = totalW
            cropState.screenH = totalH
            cropState.cropW = cropW
            cropState.cropH = cropH
            cropState.cropLeft = cropLeft
            cropState.cropTop = cropTop
            cropState.displayW = displayW
            cropState.displayH = displayH
        }

        val initScale = WallpaperCropMath.minScaleForRotation(cropW, cropH, displayW, displayH, 0f)

        LaunchedEffect(sourceBitmap, totalW, totalH, cropW, cropH) {
            if (!cropState.initialized) {
                cropState.resetTransform(initScale)
                cropState.initialized = true
            } else {
                if (cropState.restoredScreenW > 0f && cropState.restoredScreenH > 0f) {
                    cropState.panX *= totalW / cropState.restoredScreenW
                    cropState.panY *= totalH / cropState.restoredScreenH
                    cropState.restoredScreenW = 0f
                    cropState.restoredScreenH = 0f
                }
                cropState.applyConstraints()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isSaving) {
                    if (isSaving) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            isTouching = event.changes.any { it.pressed }
                        }
                    }
                }
                .pointerInput(isSaving) {
                    if (isSaving) return@pointerInput
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        val anchoredPan = WallpaperCropMath.transformAroundCentroid(
                            panX = cropState.panX,
                            panY = cropState.panY,
                            centroidX = centroid.x,
                            centroidY = centroid.y,
                            centerX = totalW / 2f,
                            centerY = cropCenterY,
                            gesturePanX = pan.x,
                            gesturePanY = pan.y,
                            zoom = zoom,
                            rotationDegrees = rotation,
                        )
                        cropState.panX = anchoredPan.x
                        cropState.panY = anchoredPan.y
                        cropState.rotationDegrees += rotation
                        cropState.scale *= zoom
                        cropState.applyConstraints()
                    }
                }
        ) {
            val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = cropState.scale
                        scaleY = cropState.scale
                        translationX = cropState.panX
                        translationY = cropState.panY + cropCenterOffsetY
                        rotationZ = cropState.rotationDegrees
                    },
            )

            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                val maskAlpha = if (isTouching) 0.45f else 0.78f
                drawRect(
                    Color.Black.copy(alpha = maskAlpha),
                    topLeft = Offset.Zero,
                    size = Size(size.width, cropTop)
                )
                drawRect(
                    Color.Black.copy(alpha = maskAlpha),
                    topLeft = Offset(0f, cropTop + cropH),
                    size = Size(size.width, size.height - cropTop - cropH),
                )
                drawRect(
                    Color.Black.copy(alpha = maskAlpha),
                    topLeft = Offset(0f, cropTop),
                    size = Size(cropLeft, cropH)
                )
                drawRect(
                    Color.Black.copy(alpha = maskAlpha),
                    topLeft = Offset(cropLeft + cropW, cropTop),
                    size = Size(size.width - cropLeft - cropW, cropH),
                )
                drawRect(
                    Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(cropLeft, cropTop),
                    size = Size(cropW, cropH),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBarHeight = it.height }
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                enabled = !isSaving,
                shape = MaterialTheme.shapes.medium,
                onClick = onCancel,
            ) {
                Text(stringResource(R.string.common_cancel), color = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomBarHeight = it.height }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 窄窗口（分屏/折叠屏/大字体）下按钮自动换行，避免被裁掉。
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(
                        10.dp,
                        Alignment.CenterHorizontally
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        enabled = !isSaving,
                        shape = MaterialTheme.shapes.medium,
                        onClick = { cropState.resetTransform(initScale) },
                    ) {
                        Text(
                            stringResource(R.string.settings_background_crop_reset),
                            color = Color.White
                        )
                    }
                    OutlinedButton(
                        enabled = !isSaving,
                        shape = MaterialTheme.shapes.medium,
                        onClick = {
                            cropState.rotationDegrees = (cropState.rotationDegrees + 90f) % 360f
                            cropState.applyConstraints()
                        },
                    ) {
                        Text(
                            stringResource(R.string.settings_background_crop_rotate),
                            color = Color.White
                        )
                    }
                    OutlinedButton(
                        enabled = !isSaving,
                        shape = MaterialTheme.shapes.medium,
                        onClick = {
                            cropState.scale *= 0.8f
                            cropState.applyConstraints()
                        },
                    ) { Text("-", color = Color.White) }
                    OutlinedButton(
                        enabled = !isSaving,
                        shape = MaterialTheme.shapes.medium,
                        onClick = {
                            cropState.scale *= 1.25f
                            cropState.applyConstraints()
                        },
                    ) { Text("+", color = Color.White) }
                }
                Button(
                    enabled = !isSaving,
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        if (!isSaving) coroutineScope.launch {
                            onSavingChange(true)
                            try {
                                // isSaving 已锁定手势和按钮，保存期间变换状态不会变化。
                                // 生成位图随 UI 作用域取消即可（无副作用）；
                                // 落盘与偏好提交的原子性由 BackgroundImageStore.saveCropped 保证。
                                val bitmap = withContext(Dispatchers.Default) {
                                    runCatching {
                                        cropState.getCroppedBitmap(
                                            source = sourceBitmap,
                                            targetWidth = targetWidth,
                                            targetHeight = targetHeight,
                                        )
                                    }.getOrNull()
                                }
                                if (bitmap != null) {
                                    onConfirm(bitmap)
                                } else {
                                    Toast.makeText(
                                        context,
                                        R.string.settings_background_import_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } finally {
                                onSavingChange(false)
                            }
                        }
                    },
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.settings_background_crop_save))
                    }
                }
            }
        }
    }
}
