package com.aliothmoon.maameow.presentation.view.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.utils.BitmapUtils
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Stable
class CropState {
    var scale by mutableFloatStateOf(1f)
        internal set
    var panX by mutableFloatStateOf(0f)
        internal set
    var panY by mutableFloatStateOf(0f)
        internal set
    var rotationDegrees by mutableFloatStateOf(0f)
        internal set
    var screenW by mutableFloatStateOf(0f)
        internal set
    var screenH by mutableFloatStateOf(0f)
        internal set
    var cropW by mutableFloatStateOf(0f)
        internal set
    var cropH by mutableFloatStateOf(0f)
        internal set
    var cropLeft by mutableFloatStateOf(0f)
        internal set
    var cropTop by mutableFloatStateOf(0f)
        internal set

    fun reset() {
        scale = 1f
        panX = 0f
        panY = 0f
        rotationDegrees = 0f
    }

    fun getCroppedBitmap(source: Bitmap): Bitmap {
        val sw = screenW
        val sh = screenH
        if (sw <= 0f || sh <= 0f || cropW <= 0f || cropH <= 0f) return source
        val bw = source.width.toFloat()
        val bh = source.height.toFloat()
        val baseScale = min(sw / bw, sh / bh)

        // Compute output size in source image pixel space for full resolution
        val outW = (cropW / baseScale / scale).toInt().coerceAtLeast(1)
        val outH = (cropH / baseScale / scale).toInt().coerceAtLeast(1)

        val m = Matrix()
        // Scale from source pixels to screen pixels
        m.postScale(baseScale, baseScale)
        m.postTranslate((sw - bw * baseScale) / 2f, (sh - bh * baseScale) / 2f)
        // Apply user transforms (scale, rotate, pan)
        m.postTranslate(-sw / 2f, -sh / 2f)
        m.postScale(scale, scale)
        m.postRotate(rotationDegrees)
        m.postTranslate(sw / 2f, sh / 2f)
        m.postTranslate(panX, panY)
        // Move crop region to origin
        m.postTranslate(-cropLeft, -cropTop)
        // Scale output from screen pixels to output pixels
        val scaleFactor = cropW / outW.toFloat()
        m.postScale(1f / scaleFactor, 1f / scaleFactor)

        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, m, null)
        return result
    }
}

@Composable
fun WallpaperSettingsView(
    navController: NavController,
    viewModel: SettingsViewModel,
) {
    val wallpaperUri by viewModel.wallpaperUri.collectAsStateWithLifecycle()
    val wallpaperAlpha by viewModel.wallpaperAlpha.collectAsStateWithLifecycle()
    val wallpaperBlur by viewModel.wallpaperBlur.collectAsStateWithLifecycle()
    val wallpaperFrostedGlass by viewModel.wallpaperFrostedGlass.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isEditing by remember { mutableStateOf(false) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingOriginalUri by remember { mutableStateOf<Uri?>(null) }
    val cropState = remember { CropState() }
    val originalFile = remember { File(context.filesDir, "wallpaper_original.jpg") }

    // Remember last confirmed crop params so re-edit restores the same view
    val savedCropScale by viewModel.savedCropScale.collectAsStateWithLifecycle()
    val savedCropPanX by viewModel.savedCropPanX.collectAsStateWithLifecycle()
    val savedCropPanY by viewModel.savedCropPanY.collectAsStateWithLifecycle()
    val savedCropRotation by viewModel.savedCropRotation.collectAsStateWithLifecycle()
    val hasSavedCrop = pendingOriginalUri == null && !savedCropScale.isNaN()

    fun enterEditMode(bitmap: Bitmap) {
        sourceBitmap = bitmap
        isEditing = true
    }

    fun exitEditMode() {
        sourceBitmap?.recycle()
        sourceBitmap = null
        pendingOriginalUri = null
        isEditing = false
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            android.util.Log.w("WallpaperSettings", "takePersistableUriPermission security exception", e)
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("WallpaperSettings", "takePersistableUriPermission bad argument", e)
        }
        val bitmap = BitmapUtils.loadDownsampledBitmap(context, uri, maxDimension = 2560)
        if (bitmap != null) {
            pendingOriginalUri = uri
            enterEditMode(bitmap)
        }
    }

    val configuration = LocalConfiguration.current
    val screenRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()

    if (isEditing && sourceBitmap != null) {
        BackHandler(enabled = true) { exitEditMode() }
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val totalW = constraints.maxWidth.toFloat()
            val totalH = constraints.maxHeight.toFloat()

            // 裁切框 = 手机屏幕比例，占屏幕 70%×50%
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

            cropState.apply {
                screenW = totalW
                screenH = totalH
                this.cropW = cropW
                this.cropH = cropH
                this.cropLeft = cropLeft
                this.cropTop = cropTop
            }

            // 初始缩放：让图片刚好填满裁切框（ContentScale.Fit 的显示尺寸基础上再放大）
            val bw = sourceBitmap!!.width.toFloat()
            val bh = sourceBitmap!!.height.toFloat()
            val baseScale = min(totalW / bw, totalH / bh)
            val displayW = bw * baseScale
            val displayH = bh * baseScale
            val initScale = max(cropW / displayW, cropH / displayH)
            LaunchedEffect(sourceBitmap) {
                if (!hasSavedCrop) {
                    cropState.scale = initScale
                    cropState.panX = 0f
                    cropState.panY = 0f
                    cropState.rotationDegrees = 0f
                } else {
                    cropState.scale = savedCropScale
                    cropState.panX = savedCropPanX
                    cropState.panY = savedCropPanY
                    cropState.rotationDegrees = savedCropRotation
                }
            }

            val s = cropState
            var isTouching by remember { mutableStateOf(false) }

            Box(modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            isTouching = pressedCount > 0
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        s.apply {
                            scale = (scale * zoom).coerceIn(0.3f, 5f)
                            panX += pan.x
                            panY += pan.y
                            rotationDegrees += Math.toDegrees(rotation.toDouble()).toFloat()
                        }
                    }
                }
            ) {
                // Cached image bitmap to avoid re-creating on every recomposition
                val imageBitmap = remember(sourceBitmap) { sourceBitmap!!.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alpha = wallpaperAlpha / 100f,
                    modifier = Modifier.fillMaxSize()
                        .then(if (wallpaperBlur > 0) Modifier.blur(wallpaperBlur.dp) else Modifier)
                        .graphicsLayer {
                            scaleX = cropState.scale
                            scaleY = cropState.scale
                            translationX = cropState.panX
                            translationY = cropState.panY
                            rotationZ = cropState.rotationDegrees
                        },
                )
                
                ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                    // Semi-transparent when touching so user can see uncropped parts
                    val maskAlpha = if (isTouching) 0.4f else 1f
                    // Top
                    drawRect(Color.Black.copy(alpha = maskAlpha),
                        topLeft = Offset.Zero, size = Size(size.width, cropTop))
                    // Bottom
                    drawRect(Color.Black.copy(alpha = maskAlpha),
                        topLeft = Offset(0f, cropTop + cropH),
                        size = Size(size.width, size.height - cropTop - cropH))
                    // Left
                    drawRect(Color.Black.copy(alpha = maskAlpha),
                        topLeft = Offset(0f, cropTop),
                        size = Size(cropLeft, cropH))
                    // Right
                    drawRect(Color.Black.copy(alpha = maskAlpha),
                        topLeft = Offset(cropLeft + cropW, cropTop),
                        size = Size(size.width - cropLeft - cropW, cropH))
                    // White crop border (always visible)
                    drawRect(Color.White.copy(alpha = 0.8f),
                        topLeft = Offset(cropLeft, cropTop),
                        size = Size(cropW, cropH),
                        style = Stroke(width = 2.dp.toPx()))
                }
            }

            Box(modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconButton(onClick = { exitEditMode() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.settings_wallpaper_alpha, wallpaperAlpha),
                    style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Slider(wallpaperAlpha.toFloat(),
                    onValueChange = { viewModel.setWallpaperAlpha(it.toInt()) },
                    valueRange = 0f..100f)
                Text(stringResource(R.string.settings_wallpaper_blur, wallpaperBlur),
                    style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Slider(wallpaperBlur.toFloat(),
                    onValueChange = { viewModel.setWallpaperBlur(it.toInt()) },
                    valueRange = 0f..25f)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            val cropped = cropState.getCroppedBitmap(sourceBitmap!!)
                            // Guard: if getCroppedBitmap returned the original (crop failed), skip
                            if (cropped === sourceBitmap) {
                                exitEditMode()
                            } else {
                                val path = saveBitmapToFile(context, cropped, "wallpaper.jpg")
                                if (path != null) {
                                    pendingOriginalUri?.let { saveOriginalForReEdit(context, it, originalFile) }
                                    viewModel.setCropState(
                                        cropState.scale,
                                        cropState.panX,
                                        cropState.panY,
                                        cropState.rotationDegrees,
                                    )
                                    viewModel.setWallpaperUri(Uri.fromFile(File(path)).toString())
                                }
                                cropped.recycle()
                                exitEditMode()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check,
                        stringResource(R.string.settings_wallpaper_confirm),
                        tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = stringResource(R.string.settings_wallpaper_title),
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    onNavigationClick = { navController.popBackStack() },
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (wallpaperUri.isNotEmpty()) {
                    WallpaperPreview(
                        wallpaperUri = wallpaperUri,
                        alpha = wallpaperAlpha / 100f,
                        blurDp = wallpaperBlur,
                        screenRatio = screenRatio,
                        onClick = {
                            val src = if (originalFile.exists()) originalFile.absolutePath else wallpaperUri
                            val bitmap = BitmapUtils.loadDownsampledBitmap(
                                context,
                                if (src.startsWith("content://") || src.startsWith("file://")) src else "file://$src",
                                maxDimension = 2560,
                            )
                            if (bitmap != null) enterEditMode(bitmap)
                        },
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = MaaDesignTokens.Spacing.listHorizontal,
                                vertical = MaaDesignTokens.Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sectionGap),
                    ) {
                        SettingsGroupCard {
                            SettingRow(
                                title = stringResource(R.string.settings_wallpaper_change),
                                onClick = { wallpaperPicker.launch(arrayOf("image/*")) },
                            )
                        }
                        SettingsGroupCard {
                            SettingRow(
                                title = stringResource(R.string.settings_wallpaper_frosted_glass),
                                description = stringResource(R.string.settings_wallpaper_frosted_glass_desc),
                                trailing = {
                                    Switch(
                                        checked = wallpaperFrostedGlass,
                                        onCheckedChange = { viewModel.setWallpaperFrostedGlass(it) },
                                    )
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.setWallpaperUri("")
                                viewModel.clearCropState()
                                if (originalFile.exists()) originalFile.delete()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MaaDesignTokens.Spacing.listHorizontal, vertical = 8.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.82f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.settings_wallpaper_clear),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = MaaDesignTokens.Spacing.listHorizontal),
                    ) {
                        SettingsGroupCard {
                            SettingRow(
                                title = stringResource(R.string.settings_wallpaper_desc),
                                onClick = { wallpaperPicker.launch(arrayOf("image/*")) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperPreview(
    wallpaperUri: String,
    alpha: Float,
    blurDp: Int,
    screenRatio: Float,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
            .wrapContentSize(Alignment.Center)
            .width(180.dp)
            .aspectRatio(screenRatio)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(wallpaperUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = alpha,
            modifier = Modifier.fillMaxSize()
                .then(if (blurDp > 0) Modifier.blur(blurDp.dp) else Modifier),
        )
    }
}
private fun saveOriginalForReEdit(context: Context, uri: Uri, destFile: File) {
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
    } catch (_: Exception) { }
}

private fun saveBitmapToFile(context: Context, bitmap: Bitmap, filename: String): String? {
    return try {
        val file = File(context.filesDir, filename)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        file.absolutePath
    } catch (_: Exception) { null }
}
