package com.aliothmoon.maameow.presentation.view.panel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.models.pixelart.PixelArtPlan
import com.aliothmoon.maameow.domain.models.pixelart.PixelDitherMode
import com.aliothmoon.maameow.domain.models.pixelart.PixelFitMode
import com.aliothmoon.maameow.domain.service.pixelart.PixelPaintHelper
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.components.SelectableCardButton
import com.aliothmoon.maameow.presentation.viewmodel.GRID_DELAY_MAX_MS
import com.aliothmoon.maameow.presentation.viewmodel.PixelArtDelegate
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.asString
import kotlin.math.roundToInt

/** SAF 文件选择器的过滤类型，比图片选择器自由，能挑到任意目录下的图 */
private val IMAGE_MIME_TYPES = arrayOf("image/*")

/** 低于这个宽度并排会把按钮挤到换行 */
private val SIDE_BY_SIDE_MIN_WIDTH = 340.dp

private const val PREVIEW_WIDTH_RATIO = 0.42f

/**
 * 牛杂里的像素画配置段
 * 嵌在 LazyColumn 的 item 内，不能再套 verticalScroll
 */
@Composable
fun PixelArtSection(
    delegate: PixelArtDelegate,
    modifier: Modifier = Modifier,
) {
    val state by delegate.state.collectAsStateWithLifecycle()
    val progress by delegate.progress.collectAsStateWithLifecycle()
    val locked by delegate.parametersLocked.collectAsStateWithLifecycle()
    val isInFloatingWindow = LocalFloatingWindowContext.current
    val context = LocalContext.current

    // 悬浮窗没有 ActivityResultRegistryOwner，选图只能在 App 内做
    val picker = if (!isInFloatingWindow) {
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val name = Misc.queryFileName(context, uri) ?: uri.lastPathSegment.orEmpty()
            delegate.onImagePicked(uri, name)
        }
    } else {
        null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.plan?.let { plan ->
            val preview: @Composable (Modifier) -> Unit = { previewModifier ->
                Box(modifier = previewModifier.aspectRatio(1f)) {
                    PixelArtPreview(
                        plan = plan,
                        enabled = !locked,
                        onTransform = delegate::onPreviewTransform,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            val convertOptions: @Composable ColumnScope.() -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GroupLabel(stringResource(R.string.pixel_art_group_fit))
                    ChipRow(
                        options = PixelFitMode.entries,
                        selected = state.fit,
                        enabled = !locked,
                        label = { stringResource(fitLabel(it)) },
                        onSelect = delegate::onFitChange,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GroupLabel(stringResource(R.string.pixel_art_group_dither))
                    ChipRow(
                        options = PixelDitherMode.entries,
                        selected = state.dither,
                        enabled = !locked,
                        label = { stringResource(ditherLabel(it)) },
                        onSelect = delegate::onDitherChange,
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= SIDE_BY_SIDE_MIN_WIDTH) {
                    // 右列显式取预览边长，两组选项均分这个高度，左右看齐
                    val previewSide = maxWidth * PREVIEW_WIDTH_RATIO
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        preview(Modifier.width(previewSide))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(previewSide),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            content = convertOptions,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        preview(Modifier
                            .fillMaxWidth(0.5f)
                            .align(Alignment.CenterHorizontally))
                        convertOptions()
                    }
                }
            }
            Text(
                text = stringResource(R.string.pixel_art_view_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 亮度/对比度下限 50%，再低整张图就退化成全黑或全灰，没有可用档位
            PercentSlider(
                label = stringResource(R.string.pixel_art_brightness),
                value = state.brightnessPercent,
                enabled = !locked,
                range = 50f..200f,
                onChange = delegate::onBrightnessChange,
            )
            PercentSlider(
                label = stringResource(R.string.pixel_art_contrast),
                value = state.contrastPercent,
                enabled = !locked,
                range = 50f..200f,
                onChange = delegate::onContrastChange,
            )
            PercentSlider(
                label = stringResource(R.string.pixel_art_saturation),
                value = state.saturationPercent,
                enabled = !locked,
                range = 0f..200f,
                onChange = delegate::onSaturationChange,
            )
        }

        if (picker != null) {
            GroupLabel(stringResource(R.string.pixel_art_group_image))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectableCardButton(
                    text = stringResource(R.string.pixel_art_pick_image),
                    enabled = !locked,
                    onClick = { picker.launch(IMAGE_MIME_TYPES) },
                )
                SelectableCardButton(
                    text = stringResource(R.string.pixel_art_paste),
                    enabled = !locked,
                    onClick = delegate::onPasteText,
                )
                SelectableCardButton(
                    text = stringResource(R.string.pixel_art_reset_view),
                    enabled = !locked && state.plan != null,
                    onClick = delegate::onResetView,
                )
                SelectableCardButton(
                    text = stringResource(R.string.pixel_art_reset_params),
                    enabled = !locked,
                    onClick = delegate::onResetParameters,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.pixel_art_pick_in_app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GroupLabel(stringResource(R.string.pixel_art_group_output))
        ToggleRow(
            label = stringResource(R.string.pixel_art_trim_border),
            checked = state.trimEmptyBorder,
            enabled = !locked,
            onCheckedChange = delegate::onTrimChange,
        )
        ToggleRow(
            label = stringResource(R.string.pixel_art_skip_white),
            checked = state.skipWhite,
            enabled = !locked,
            onCheckedChange = delegate::onSkipWhiteChange,
        )
        ToggleRow(
            label = stringResource(R.string.pixel_art_swipe),
            checked = state.swipeEnabled,
            enabled = !locked,
            onCheckedChange = delegate::onSwipeChange,
        )
        Text(
            text = stringResource(R.string.pixel_art_swipe_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PercentSlider(
            label = stringResource(R.string.pixel_art_grid_delay),
            value = state.gridDelayMs.toFloat(),
            enabled = !locked,
            range = 0f..GRID_DELAY_MAX_MS.toFloat(),
            onChange = { delegate.onGridDelayChange(it.roundToInt()) },
            valueText = stringResource(R.string.pixel_art_grid_delay_value, state.gridDelayMs),
        )
        Text(
            text = stringResource(R.string.pixel_art_grid_delay_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.plan?.let {
            Text(
                text = stringResource(
                    R.string.pixel_art_cell_count,
                    it.paintedCellCount,
                    it.groups.size
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        progress?.let {
            val fraction = if (it.total > 0) it.done.toFloat() / it.total else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.pixel_art_progress, it.done, it.total),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = stringResource(R.string.pixel_art_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (locked) {
            Text(
                text = stringResource(R.string.pixel_art_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val status = state.statusMessage.asString()
        if (status.isNotBlank()) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 功能分组小标题，与牛杂里各组标题同一套样式 */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    enabled: Boolean,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            SelectableCardButton(
                text = label(option),
                selected = selected == option,
                enabled = enabled,
                onClick = { onSelect(option) },
            )
        }
    }
}

/** 右侧读数默认带 %，非百分比量用 valueText 覆盖 */
@Composable
private fun PercentSlider(
    label: String,
    value: Float,
    enabled: Boolean,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    valueText: String = "${value.roundToInt()}%",
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            enabled = enabled,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun PixelArtPreview(
    plan: PixelArtPlan,
    enabled: Boolean,
    onTransform: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gestures = if (enabled) {
        Modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                onTransform(pan.x / size.width, pan.y / size.height, zoom)
            }
        }
    } else {
        Modifier
    }
    Canvas(modifier = modifier.then(gestures)) {
        val cell = size.minDimension / plan.size
        for (row in 0 until plan.size) {
            for (col in 0 until plan.size) {
                drawRect(
                    color = Color(
                        PixelPaintHelper.PALETTE[plan.indexAt(
                            col,
                            row
                        )] or 0xFF000000.toInt()
                    ),
                    topLeft = Offset(col * cell, row * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}

private fun fitLabel(mode: PixelFitMode): Int = when (mode) {
    PixelFitMode.CROP -> R.string.pixel_art_fit_crop
    PixelFitMode.CONTAIN -> R.string.pixel_art_fit_contain
    PixelFitMode.STRETCH -> R.string.pixel_art_fit_stretch
}

private fun ditherLabel(mode: PixelDitherMode): Int = when (mode) {
    PixelDitherMode.ILLUSTRATION -> R.string.pixel_art_dither_illustration
    PixelDitherMode.NONE -> R.string.pixel_art_dither_none
    PixelDitherMode.FLOYD_STEINBERG -> R.string.pixel_art_dither_fs
    PixelDitherMode.ATKINSON -> R.string.pixel_art_dither_atkinson
}
