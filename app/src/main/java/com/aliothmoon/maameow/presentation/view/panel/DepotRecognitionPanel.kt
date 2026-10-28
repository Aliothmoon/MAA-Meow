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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.aliothmoon.maameow.data.model.LogColorRole
import com.aliothmoon.maameow.data.repository.DepotDelta
import com.aliothmoon.maameow.data.repository.DepotSnapshot
import com.aliothmoon.maameow.data.repository.delta
import com.aliothmoon.maameow.data.repository.depotItemOrder
import com.aliothmoon.maameow.data.repository.formatDepotDelta
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.data.resource.ItemInfo
import com.aliothmoon.maameow.domain.service.ToolboxExportFileType
import com.aliothmoon.maameow.presentation.components.ItemIcon
import com.aliothmoon.maameow.presentation.viewmodel.ToolboxViewModel
import com.aliothmoon.maameow.theme.themedColor
import com.aliothmoon.maameow.utils.i18n.asString
import com.aliothmoon.maameow.utils.i18n.formatToolboxSyncTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DepotRecognitionPanel(
    modifier: Modifier = Modifier,
    viewModel: ToolboxViewModel = koinInject(),
    itemHelper: ItemHelper = koinInject(),
    iconLoader: ItemIconLoader = koinInject()
) {
    val snapshot by viewModel.depotRepository.snapshot.collectAsStateWithLifecycle()
    val itemMap by itemHelper.items.collectAsStateWithLifecycle()
    var onlyChanged by rememberSaveable { mutableStateOf(false) }
    val delta = remember(snapshot) { snapshot.delta() }
    val rows = remember(snapshot, itemMap, onlyChanged) {
        depotRows(snapshot, itemMap, delta, onlyChanged)
    }
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

    LaunchedEffect(rows) {
        rows.forEach { iconLoader.load(it.id) }
    }

    // 开关隐藏时复位
    LaunchedEffect(delta) {
        if (delta?.changes.isNullOrEmpty()) onlyChanged = false
    }

    if (snapshot.items.isEmpty() && !snapshot.hasSynced) {
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
                if (resolvedStatusMessage.isNotEmpty()) {
                    Text(
                        text = resolvedStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.panel_depot_item_count, snapshot.items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (snapshot.hasSynced) {
                        Text(
                            text = stringResource(
                                R.string.panel_toolbox_last_sync,
                                formatToolboxSyncTime(snapshot.syncTimeMillis),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (delta != null) {
                    DepotDeltaRow(delta, onlyChanged) { onlyChanged = it }
                }
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
        items(rows, key = { it.id }) { row ->
            DepotItemCell(row, itemMap[row.id]?.name, showDelta = !delta?.changes.isNullOrEmpty())
        }
    }
}

/** count 含识别后的掉落，delta 只看两次识别 */
@Immutable
private data class DepotRow(val id: String, val count: Int, val delta: Int)

private fun depotRows(
    snapshot: DepotSnapshot,
    itemMap: Map<String, ItemInfo>,
    delta: DepotDelta?,
    onlyChanged: Boolean,
): List<DepotRow> {
    val changes = delta?.changes.orEmpty()
    // 并上 changes：用完的也显示
    val ids = if (onlyChanged && changes.isNotEmpty()) changes.keys else snapshot.items.keys + changes.keys
    return ids.sortedWith(depotItemOrder(itemMap))
        .map { DepotRow(it, snapshot.items[it] ?: 0, changes[it] ?: 0) }
}

@Composable
private fun DepotDeltaRow(
    delta: DepotDelta,
    onlyChanged: Boolean,
    onOnlyChangedChange: (Boolean) -> Unit,
) {
    val since = formatToolboxSyncTime(delta.fromMillis)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (delta.changes.isEmpty()) {
                stringResource(R.string.panel_depot_delta_none, since)
            } else {
                stringResource(R.string.panel_depot_delta_summary, since, delta.changes.size)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (delta.changes.isNotEmpty()) {
            FilterChip(
                selected = onlyChanged,
                onClick = { onOnlyChangedChange(!onlyChanged) },
                label = {
                    Text(
                        stringResource(R.string.panel_depot_only_changed),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
            )
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
private fun DepotItemCell(row: DepotRow, name: String?, showDelta: Boolean) {
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
            ItemIcon(
                itemId = row.id,
                modifier = Modifier.size(44.dp),
                contentDescription = name,
            )
            Text(
                text = name ?: row.id,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = "x${row.count}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            // 无变化也占位，保持同行格子等高
            if (showDelta) {
                Text(
                    text = if (row.delta != 0) formatDepotDelta(row.delta) else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Medium,
                    // 涨红跌绿
                    color = if (row.delta > 0) LogColorRole.ERROR.themedColor() else LogColorRole.SUCCESS.themedColor(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
