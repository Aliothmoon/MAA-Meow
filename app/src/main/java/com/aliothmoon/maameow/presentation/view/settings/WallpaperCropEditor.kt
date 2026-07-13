package com.aliothmoon.maameow.presentation.view.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.WallpaperBlur
import com.aliothmoon.maameow.theme.rememberWallpaperDisplayBitmap
import com.aliothmoon.maameow.theme.wallpaperBlurModifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Which bottom-bar adjust control is expanded under the tool buttons. */
private enum class CropAdjustTool {
    Opacity,
    Blur,
}

/** Full-screen wallpaper crop / pan / rotate editor. */
@Composable
internal fun WallpaperCropEditor(
    session: WallpaperEditSession,
    localAlpha: Float,
    onLocalAlphaChange: (Float) -> Unit,
    localBlur: Float,
    onLocalBlurChange: (Float) -> Unit,
    screenRatio: Float,
) {
    BackHandler(enabled = true) {
        if (!session.isBusy) session.exitEditMode()
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val totalW = constraints.maxWidth.toFloat()
        val totalH = constraints.maxHeight.toFloat()

        // Crop frame uses phone aspect ratio and occupies ~70%x50% of the editor.
        val maxCropW = totalW * 0.70f
        val maxCropH = totalH * 0.50f
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
        val cropTop = (totalH - cropH) / 2f

        SideEffect {
            // Only write geometry here. ensureCoverScale must NOT run every frame:
            // composition during two-finger rotate would clamp/boost mid-gesture.
            session.cropState.apply {
                screenW = totalW
                screenH = totalH
                this.cropW = cropW
                this.cropH = cropH
                this.cropLeft = cropLeft
                this.cropTop = cropTop
                imageW = session.sourceBitmap!!.width.toFloat()
                imageH = session.sourceBitmap!!.height.toFloat()
            }
        }

        val bw = session.sourceBitmap!!.width.toFloat()
        val bh = session.sourceBitmap!!.height.toFloat()
        val baseScale = min(totalW / bw, totalH / bh)
        val displayW = bw * baseScale
        val displayH = bh * baseScale
        val computedInitScale = max(cropW / displayW, cropH / displayH)
        LaunchedEffect(session.sourceBitmap) {
            session.initScale = computedInitScale
            val restored = withContext(Dispatchers.IO) {
                session.fileStore.loadCropParams(session.fileStore.sourceIdentity(session.sourceFile))
            }
            if (restored != null) {
                session.cropState.restore(
                    restoredScale = restored.scale,
                    restoredPanX = restored.panX,
                    restoredPanY = restored.panY,
                    restoredRotation = restored.rotationDegrees,
                    fallbackScale = computedInitScale,
                )
            } else {
                session.cropState.reset(computedInitScale)
            }
        }

        val s = session.cropState
        var isTouching by remember { mutableStateOf(false) }
        // null = only tool buttons; select one to expand its slider under the row.
        var activeAdjustTool by remember { mutableStateOf<CropAdjustTool?>(null) }
        // Resize / first layout: re-cover when not mid-gesture.
        LaunchedEffect(totalW, totalH, session.sourceBitmap) {
            if (!isTouching) {
                s.ensureCoverScale()
            }
        }
        // After rotate/pinch/pan, re-cover the crop window once the gesture ends.
        LaunchedEffect(isTouching) {
            if (!isTouching) {
                s.ensureCoverScale()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(session.isBusy) {
                    if (session.isBusy) return@pointerInput
                    // One-finger pan; two-finger: first finger = pivot, second orbits
                    // for 1:1 rotation (Gallery-style absolute angle tracking).
                    // isTouching is owned only by this detector so session.isBusy cancellation
                    // always clears it via finally (avoids stuck mid-gesture cover skip).
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        isTouching = true
                        try {
                        var pivotId = first.id
                        var pivotPos = first.position
                        var lastAngle = 0f
                        var lastDist = 0f
                        var multi = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size == 1) {
                                val p = pressed[0]
                                if (multi) {
                                    // Left multi-touch: treat remaining finger as pan base.
                                    pivotId = p.id
                                    pivotPos = p.position
                                    multi = false
                                }
                                val prev = p.previousPosition
                                val dx = p.position.x - prev.x
                                val dy = p.position.y - prev.y
                                if (dx != 0f || dy != 0f) {
                                    s.applyPan(dx, dy)
                                }
                                if (p.positionChanged()) p.consume()
                                pivotPos = p.position
                            } else {
                                // Two or more: use first two pointers.
                                val a = pressed[0]
                                val b = pressed[1]
                                // Keep pivot sticky: prefer previous pivotId if still down.
                                val pivotChange = pressed.find { it.id == pivotId } ?: a
                                val freeChange = pressed.firstOrNull { it.id != pivotChange.id } ?: b
                                pivotId = pivotChange.id

                                val newPivot = pivotChange.position
                                val newFree = freeChange.position
                                val dx = newFree.x - newPivot.x
                                val dy = newFree.y - newPivot.y
                                val dist = hypot(dx, dy).coerceAtLeast(1f)
                                // Screen y grows downward: atan2 increases clockwise,
                                // matching Compose rotationZ / Matrix.postRotate.
                                val angle = atan2(dy, dx)

                                if (!multi) {
                                    multi = true
                                    lastAngle = angle
                                    lastDist = dist
                                    pivotPos = newPivot
                                } else {
                                    val dAngle = angle - lastAngle
                                    // Wrap to [-PI, PI] for stable 1:1 deltas across ±180°.
                                    var wrapped = dAngle
                                    val pi = Math.PI.toFloat()
                                    if (wrapped > pi) wrapped -= 2f * pi
                                    if (wrapped < -pi) wrapped += 2f * pi
                                    val deg = Math.toDegrees(wrapped.toDouble()).toFloat()
                                    val zoom = if (lastDist > 0f) dist / lastDist else 1f
                                    // Pivot finger movement pans the whole transform.
                                    val panX = newPivot.x - pivotPos.x
                                    val panY = newPivot.y - pivotPos.y
                                    s.applyTransform(
                                        zoom = zoom,
                                        panDeltaX = panX,
                                        panDeltaY = panY,
                                        rotationDegreesDelta = deg,
                                        pivotX = newPivot.x,
                                        pivotY = newPivot.y,
                                    )
                                    lastAngle = angle
                                    lastDist = dist
                                    pivotPos = newPivot
                                }
                                if (pivotChange.positionChanged()) pivotChange.consume()
                                if (freeChange.positionChanged()) freeChange.consume()
                            }
                        }
                        } finally {
                            isTouching = false
                        }
                    }
                }
        ) {
            // API 31+: Modifier.blur; API < 31: software Stack Blur on a downscaled copy.
            val imageBitmap = rememberWallpaperDisplayBitmap(
                source = session.sourceBitmap,
                blurPercent = localBlur.toInt(),
                softwareMaxDimension = 720,
            )
            if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = localAlpha / 100f,
                modifier = Modifier
                    .fillMaxSize()
                    .then(wallpaperBlurModifier(localBlur.toInt()))
                    .graphicsLayer {
                        // Keep pivot at screen center so preview matches Matrix export.
                        transformOrigin = TransformOrigin.Center
                        scaleX = session.cropState.scale
                        scaleY = session.cropState.scale
                        translationX = session.cropState.panX
                        translationY = session.cropState.panY
                        rotationZ = session.cropState.rotationDegrees
                    },
            )
            }

            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                val maskAlpha = if (isTouching) 0.4f else 1f
                drawRect(
                    Color.Black.copy(alpha = maskAlpha),
                    topLeft = Offset.Zero,
                    size = Size(size.width, cropTop),
                )
                drawRect(
                    Color.Black.copy(alpha = maskAlpha),
                    topLeft = Offset(0f, cropTop + cropH),
                    size = Size(size.width, size.height - cropTop - cropH),
                )
                drawRect(
                    Color.Black.copy(alpha = maskAlpha),
                    topLeft = Offset(0f, cropTop),
                    size = Size(cropLeft, cropH),
                )
                drawRect(
                    Color.Black.copy(alpha = maskAlpha),
                    topLeft = Offset(cropLeft + cropW, cropTop),
                    size = Size(size.width - cropLeft - cropW, cropH),
                )
                drawRect(
                    Color.White.copy(alpha = 0.8f),
                    topLeft = Offset(cropLeft, cropTop),
                    size = Size(cropW, cropH),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = { if (!session.isBusy) session.exitEditMode() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { if (!session.isBusy) session.cropState.rotateBy(90f) }) {
                    Icon(Icons.Default.RotateRight, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.settings_wallpaper_rotate),
                        color = Color.White,
                    )
                }
                TextButton(onClick = { if (!session.isBusy) session.cropState.reset(session.initScale) }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.settings_wallpaper_reset_crop),
                        color = Color.White,
                    )
                }
                // Save moved from bottom bar so sliders keep full width.
                IconButton(
                    onClick = { if (!session.isBusy) session.confirmCrop() },
                    enabled = !session.isBusy,
                ) {
                    if (session.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.settings_wallpaper_confirm),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Expand/collapse: fadeIn 200 / fadeOut 150 (original feel).
            // Opacity ↔ Blur: sequential — fade out current, then fade in target
            // (same as collapsing one tool then opening the other).
            AnimatedContent(
                targetState = activeAdjustTool,
                transitionSpec = {
                    val switchingTools = initialState != null && targetState != null
                    if (switchingTools) {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = 200,
                                delayMillis = 150,
                            ),
                        ) togetherWith fadeOut(
                            animationSpec = tween(durationMillis = 150),
                        )
                    } else {
                        fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 150))
                    }
                },
                label = "cropAdjustSlider",
                modifier = Modifier.fillMaxWidth(),
            ) { tool ->
                when (tool) {
                    CropAdjustTool.Opacity -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(
                                    R.string.settings_wallpaper_alpha,
                                    localAlpha.toInt(),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                            )
                            Slider(
                                value = localAlpha,
                                onValueChange = onLocalAlphaChange,
                                valueRange = 0f..100f,
                                enabled = !session.isBusy,
                                // ~4/5 screen width; centered by parent Column.
                                modifier = Modifier.fillMaxWidth(0.8f),
                            )
                        }
                    }
                    CropAdjustTool.Blur -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(
                                    R.string.settings_wallpaper_blur,
                                    localBlur.toInt(),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                            )
                            Slider(
                                value = localBlur,
                                onValueChange = onLocalBlurChange,
                                valueRange = 0f..WallpaperBlur.PERCENT_MAX.toFloat(),
                                enabled = !session.isBusy,
                                // ~4/5 screen width; centered by parent Column.
                                modifier = Modifier.fillMaxWidth(0.8f),
                            )
                        }
                    }
                    null -> {
                        Spacer(Modifier.fillMaxWidth().height(0.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                CropAdjustToolButton(
                    icon = Icons.Outlined.Opacity,
                    label = stringResource(R.string.settings_wallpaper_opacity_label),
                    selected = activeAdjustTool == CropAdjustTool.Opacity,
                    enabled = !session.isBusy,
                    onClick = {
                        activeAdjustTool =
                            if (activeAdjustTool == CropAdjustTool.Opacity) {
                                null
                            } else {
                                CropAdjustTool.Opacity
                            }
                    },
                )
                CropAdjustToolButton(
                    icon = Icons.Outlined.BlurOn,
                    label = stringResource(R.string.settings_wallpaper_blur_label),
                    selected = activeAdjustTool == CropAdjustTool.Blur,
                    enabled = !session.isBusy,
                    onClick = {
                        activeAdjustTool =
                            if (activeAdjustTool == CropAdjustTool.Blur) {
                                null
                            } else {
                                CropAdjustTool.Blur
                            }
                    },
                )
            }
        }

        if (session.isBusy) {
            // Consume taps so save/back/tools cannot re-enter while IO is in flight.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.settings_wallpaper_saving),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

}

@Composable
private fun CropAdjustToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val circleColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White.copy(alpha = 0.18f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        Color.White
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}
