package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.toolbox.RecruitCalcResult
import com.aliothmoon.maameow.presentation.components.INumericField
import com.aliothmoon.maameow.presentation.components.RecruitTimeSelector
import com.aliothmoon.maameow.presentation.viewmodel.ToolboxViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecruitCalcPanel(
    modifier: Modifier = Modifier,
    viewModel: ToolboxViewModel = koinInject()
) {
    val tags by viewModel.collector.recruitTags.collectAsStateWithLifecycle()
    val results by viewModel.collector.recruitResults.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val config by viewModel.recruitConfig.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 总开关：自动设置招募时间
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.auto_set_recruit_time),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = config.autoSetTime,
                    onCheckedChange = {
                        viewModel.onRecruitConfigChange(config.copy(autoSetTime = it))
                    },
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        // 星级配置行
        val starLevels = listOf(3, 4, 5, 6)

        items(starLevels) { level ->
            val checked = when (level) {
                3 -> config.chooseLevel3
                4 -> config.chooseLevel4
                5 -> config.chooseLevel5
                else -> config.chooseLevel6
            }
            val time = when (level) {
                3 -> config.level3Time
                4 -> config.level4Time
                5 -> config.level5Time
                else -> 540
            }
            val timeEnabled = level != 6
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = {
                        val newConfig = when (level) {
                            3 -> config.copy(chooseLevel3 = it)
                            4 -> config.copy(chooseLevel4 = it)
                            5 -> config.copy(chooseLevel5 = it)
                            else -> config.copy(chooseLevel6 = it)
                        }
                        viewModel.onRecruitConfigChange(newConfig)
                    },
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = stringResource(R.string.auto_select_level_tags, level),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )

                if (config.autoSetTime) {
                    RecruitTimeSelector(
                        totalMinutes = time,
                        enabled = timeEnabled,
                        onTimeChange = {
                            val newConfig = when (level) {
                                3 -> config.copy(level3Time = it)
                                4 -> config.copy(level4Time = it)
                                5 -> config.copy(level5Time = it)
                                else -> config
                            }
                            if (timeEnabled) {
                                viewModel.onRecruitConfigChange(newConfig)
                            }
                        }
                    )
                }
            }
        }

        // 检测到的标签
        if (tags.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.detected_tags),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 分隔线（有标签或有结果时显示）
        if (tags.isNotEmpty() || results.isNotEmpty()) {
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        // 计算结果
        if (results.isNotEmpty()) {
            val sorted = results.sortedByDescending { it.level }
            items(sorted, key = { it.tags.joinToString() }) { result ->
                RecruitResultItem(result)
            }
        }

        // 空提示
        if (tags.isEmpty() && results.isEmpty()) {
            item {
                Text(
                    text = statusMessage.ifBlank { stringResource(R.string.recruit_calc_hint) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecruitResultItem(result: RecruitCalcResult) {
    val levelColor = when {
        result.level >= 6 -> Color(0xFFFF6B35)
        result.level >= 5 -> Color(0xFFFFD700)
        result.level >= 4 -> Color(0xFF9C7CFF)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bgColor = when {
        result.level >= 6 -> Color(0xFFFF6B35).copy(alpha = 0.08f)
        result.level >= 5 -> Color(0xFFFFD700).copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 左侧星级徽标
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = levelColor.copy(alpha = 0.15f),
            ) {
                Text(
                    text = "${result.level}★",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = levelColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // 右侧内容
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 标签组合
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    result.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                // 干员列表
                if (result.operators.isNotEmpty()) {
                    Text(
                        text = result.operators.joinToString("  ") { it.name },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
