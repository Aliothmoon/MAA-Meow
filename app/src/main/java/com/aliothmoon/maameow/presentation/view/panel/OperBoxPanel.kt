package com.aliothmoon.maameow.presentation.view.panel

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.presentation.viewmodel.ToolboxViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun OperBoxPanel(
    modifier: Modifier = Modifier,
    viewModel: ToolboxViewModel = koinInject()
) {
    val result by viewModel.collector.operBoxResult.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 0 = 已拥有, 1 = 未拥有
    var selectedTab by remember { mutableIntStateOf(0) }

    val data = result
    if (data == null) {
        OperBoxEmptyState(modifier, statusMessage)
        return
    }

    val operators = if (selectedTab == 0) data.owned else data.notOwned

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 顶部：Tab 切换 + 导出
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val tabs = listOf(
                        stringResource(R.string.operators_owned_count, data.owned.size),
                        stringResource(R.string.operators_not_owned_count, data.notOwned.size)
                    )
                    tabs.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedTab == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { selectedTab = index }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                TextButton(onClick = {
                    scope.launch {
                        val text = viewModel.exportOperBox()
                        val entry = ClipData.newPlainText("label", text).toClipEntry()
                        clipboard.setClipEntry(entry)
                    }
                    Toast.makeText(context, context.getString(R.string.operators_data_copied), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.export), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 干员列表
        items(operators, key = { it.id }) { oper ->
            OperatorRow(oper)
        }
    }
}

@Composable
private fun OperBoxEmptyState(modifier: Modifier, statusMessage: String) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.maa_oper_box),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(16.dp))
        OperBoxHintRow(stringResource(R.string.oper_box_hint_scan))
        Spacer(Modifier.height(12.dp))
        OperBoxHintRow(stringResource(R.string.oper_box_hint_export))
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
private fun OperBoxHintRow(text: String) {
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
private fun OperatorRow(oper: OperBoxOperator) {
    val rarityColor = when (oper.rarity) {
        6 -> Color(0xFFFF6B35)
        5 -> Color(0xFFFFD700)
        4 -> Color(0xFF9C7CFF)
        3 -> Color(0xFF4FC3F7)
        2 -> Color(0xFFA5D6A7)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${oper.rarity}★",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.Bold,
                    color = rarityColor
                )
                Text(
                    text = oper.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (oper.own) {
                Text(
                    text = stringResource(R.string.oper_level_summary, oper.elite, oper.level, oper.potential),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
