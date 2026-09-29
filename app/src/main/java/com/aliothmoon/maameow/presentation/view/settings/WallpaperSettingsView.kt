package com.aliothmoon.maameow.presentation.view.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaDesignTokens
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/**
 * 自定义背景二级设置页：选图/裁剪/不透明度/遮罩/模糊。
 */
@Composable
fun WallpaperSettingsView(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val customBackgroundEnabled by viewModel.customBackgroundEnabled.collectAsStateWithLifecycle()
    val customBackgroundImageAlpha by viewModel.customBackgroundImageAlpha.collectAsStateWithLifecycle()
    val customBackgroundScrim by viewModel.customBackgroundScrim.collectAsStateWithLifecycle()
    val customBackgroundBlur by viewModel.customBackgroundBlur.collectAsStateWithLifecycle()
    val backgroundImage by viewModel.backgroundImage.collectAsStateWithLifecycle()
    val customBackgroundMonet by viewModel.customBackgroundMonet.collectAsStateWithLifecycle()

    val backgroundCrop = rememberBackgroundCropController(viewModel)
    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(backgroundCrop::pick) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_background_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() },
            )
        },
    ) { paddingValues ->
        val contentColor = MaterialTheme.colorScheme.onSurface
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            contentPadding = PaddingValues(
                horizontal = MaaDesignTokens.Spacing.listHorizontal,
                vertical = MaaDesignTokens.Spacing.sm,
            ),
        ) {
            item {
                SettingsGroupCard {
                    SettingCustomBackgroundSection(
                        contentColor = contentColor,
                        enabled = customBackgroundEnabled,
                        previewImage = backgroundImage,
                        imageAlpha = customBackgroundImageAlpha,
                        scrim = customBackgroundScrim,
                        blur = customBackgroundBlur,
                        monet = customBackgroundMonet,
                        onEnabledChange = { viewModel.setCustomBackgroundEnabled(it) },
                        onPickImage = {
                            pickBackgroundLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveImage = { viewModel.removeBackgroundImage() },
                        onImageAlphaChange = { viewModel.setCustomBackgroundImageAlpha(it) },
                        onScrimChange = { viewModel.setCustomBackgroundScrim(it) },
                        onBlurChange = { viewModel.setCustomBackgroundBlur(it) },
                        onMonetChange = { viewModel.setCustomBackgroundMonet(it) },
                    )
                }
            }
        }
    }

    // 全屏裁剪弹窗：选图后先裁剪，确认保存为背景，取消则清理源图片缓存。
    val cropSourceBitmap = backgroundCrop.sourceBitmap
    if (backgroundCrop.sourcePath != null && cropSourceBitmap != null) {
        WallpaperCropFullScreen(
            sourceBitmap = cropSourceBitmap,
            cropState = backgroundCrop.cropState,
            onCancel = backgroundCrop::cancel,
            onConfirm = backgroundCrop::confirm,
        )
    }
}

@Composable
private fun SettingCustomBackgroundSection(
    contentColor: Color,
    enabled: Boolean,
    previewImage: ImageBitmap?,
    imageAlpha: Int,
    scrim: Int,
    blur: Int,
    monet: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onImageAlphaChange: (Int) -> Unit,
    onScrimChange: (Int) -> Unit,
    onBlurChange: (Int) -> Unit,
    onMonetChange: (Boolean) -> Unit,
) {
    val hasImage = previewImage != null
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingSwitchItem(
            title = stringResource(R.string.settings_background_title),
            description = stringResource(R.string.settings_background_desc),
            contentColor = contentColor,
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )

        MaaAnimatedVisibility(
            visible = enabled,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(bottom = MaaDesignTokens.Spacing.listItemVertical),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (previewImage != null) {
                    Image(
                        bitmap = previewImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPickImage,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(
                                if (hasImage) R.string.settings_background_replace
                                else R.string.settings_background_pick
                            )
                        )
                    }
                    if (hasImage) {
                        OutlinedButton(
                            onClick = onRemoveImage,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.settings_background_remove))
                        }
                    }
                }
                if (hasImage) {
                    BackgroundPercentSlider(
                        label = stringResource(R.string.settings_background_image_alpha),
                        value = imageAlpha,
                        contentColor = contentColor,
                        onValueChange = onImageAlphaChange
                    )
                    BackgroundPercentSlider(
                        label = stringResource(R.string.settings_background_scrim),
                        value = scrim,
                        contentColor = contentColor,
                        onValueChange = onScrimChange
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BackgroundPercentSlider(
                            label = stringResource(R.string.settings_background_blur),
                            value = blur,
                            contentColor = contentColor,
                            onValueChange = onBlurChange
                        )
                    }
                    SettingSwitchItem(
                        title = stringResource(R.string.settings_background_monet),
                        description = stringResource(R.string.settings_background_monet_desc),
                        contentColor = contentColor,
                        checked = monet,
                        onCheckedChange = onMonetChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundPercentSlider(
    label: String,
    value: Int,
    contentColor: Color,
    onValueChange: (Int) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { sliderValue = value.toFloat() }
    val current = sliderValue.roundToInt().coerceIn(0, 100)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$current%",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                onValueChange(sliderValue.roundToInt().coerceIn(0, 100))
            },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
