package com.aliothmoon.maameow.presentation.view.panel

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.toolbox.DepotItem
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.domain.service.ToolboxExportFileType
import com.aliothmoon.maameow.presentation.viewmodel.ToolboxViewModel
import com.aliothmoon.maameow.utils.i18n.asString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DepotRecognitionPanel(
    modifier: Modifier = Modifier,
    viewModel: ToolboxViewModel = koinInject(),
    itemHelper: ItemHelper = koinInject(),
    iconLoader: ItemIconLoader = koinInject()
) {
    val items by viewModel.collector.depotItems.collectAsStateWithLifecycle()
    val itemMap by itemHelper.items.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val resolvedStatusMessage = statusMessage.asString()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = LocalToolboxFileExporter.current
    val copyPenguinToast = stringResource(R.string.panel_depot_copy_penguin)
    val copyToolboxToast = stringResource(R.string.panel_depot_copy_toolbox)
    val doCopy: (String, String) -> Unit = { text, toast ->
        scope.launch {
            val entry = ClipData.newPlainText("label", text).toClipEntry()
            clipboard.setClipEntry(entry)
        }
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    if (items.isEmpty()) {
        DepotEmptyState(modifier, resolvedStatusMessage)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 80.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 统计信息 + 导出按钮
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.panel_depot_item_count, items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExportFormatRow(
                    label = stringResource(R.string.panel_depot_format_penguin),
                    onCopy = { doCopy(viewModel.exportDepotArkPlanner(), copyPenguinToast) },
                    onExportFile = exporter?.let {
                        {
                            it.export(
                                "depot_penguin",
                                viewModel.exportDepotArkPlanner(),
                                ToolboxExportFileType.JSON
                            )
                        }
                    }
                )
                ExportFormatRow(
                    label = stringResource(R.string.panel_depot_format_toolbox),
                    onCopy = { doCopy(viewModel.exportDepotLolicon(), copyToolboxToast) },
                    onExportFile = exporter?.let {
                        {
                            it.export(
                                "depot_arktools",
                                viewModel.exportDepotLolicon(),
                                ToolboxExportFileType.JSON
                            )
                        }
                    }
                )
            }
        }

        // 物品网格
        items(items, key = { it.id }) { item ->
            val name = itemMap[item.id]?.name
            DepotItemCell(item, name, iconLoader)
        }
    }
}

@Composable
private fun ExportFormatRow(
    label: String,
    onCopy: () -> Unit,
    onExportFile: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onCopy,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(
                stringResource(R.string.panel_export_copy),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (onExportFile != null) {
            TextButton(
                onClick = onExportFile,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(
                    stringResource(R.string.panel_export_file),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DepotEmptyState(modifier: Modifier, statusMessage: String) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.maa_depot),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(16.dp))
        HintRow(stringResource(R.string.panel_depot_hint_scan))
        Spacer(Modifier.height(12.dp))
        HintRow(stringResource(R.string.panel_depot_hint_results))
        if (statusMessage.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun HintRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rememberItemIcon(itemId: String, loader: ItemIconLoader): State<ImageBitmap?> {
    return produceState(initialValue = null, itemId) {
        value = loader.load(itemId)
    }
}

@Composable
private fun DepotItemCell(item: DepotItem, name: String?, iconLoader: ItemIconLoader) {
    val icon by rememberItemIcon(item.id, iconLoader)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.widthIn(min = 72.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val bitmap = icon
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = name,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                // 加载中 / 无图：同尺寸占位，避免网格抖动
                Box(modifier = Modifier.size(44.dp))
            }
            Text(
                text = name ?: item.id,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = "x${item.count}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
