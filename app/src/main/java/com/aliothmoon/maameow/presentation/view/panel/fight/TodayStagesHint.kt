package com.aliothmoon.maameow.presentation.view.panel.fight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.resource.StageGroup

/**
 * 今日开放关卡提示
 * 显示当日开放的活动关卡和资源本
 */
@Composable
fun TodayStagesHint(
    stageGroups: List<StageGroup>,
    isResourceCollectionOpen: Boolean,
    stageTips: List<String>,
    todayName: String = ""
) {
    // TODO: i18n — 用 group.isPermanent 替代硬编码字符串比较
    // 收集活动关卡分组（包含剩余天数）
    val activities = stageGroups.filter { !it.isPermanent }

    val todayOpenStages = stageGroups
        .find { it.isPermanent }
        ?.stages
        ?.filter { it.isOpenToday }
        ?: emptyList()

    // 如果没有活动关卡也没有今日开放的资源关卡，不显示
    if (activities.isEmpty() && todayOpenStages.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val title = stringResource(R.string.panel_fight_today_stage_hint_title, todayName)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 将 stageTips 分为活动相关（常驻显示）和其他（可折叠）
            val activityCodes = remember(activities) {
                activities.flatMap { g -> g.stages.map { it.code } }.toSet()
            }
            val (activityTips, regularTips) = remember(stageTips, activityCodes) {
                stageTips.partition { tip ->
                    tip.startsWith("｢") ||
                            activityCodes.any { code -> code.isNotEmpty() && tip.startsWith("$code:") }
                }
            }

            // ========== 常驻显示部分：活动提示和活动关卡 ==========
            if (activityTips.isNotEmpty() || isResourceCollectionOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    activityTips.forEach { tip ->
                        val color = when {
                            tip.startsWith("｢") -> MaterialTheme.colorScheme.tertiary // 活动提示用橙色
                            else -> MaterialTheme.colorScheme.onTertiaryContainer // 活动关卡掉落用深橙色
                        }
                        Text(
                            text = "· $tip",
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                    }
                    // 资源收集活动提示
                    if (isResourceCollectionOpen && activityTips.none { it.contains("资源收集") }) {
                        Text(
                            text = stringResource(R.string.panel_fight_resource_collection_open_tip),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (regularTips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // ========== 可折叠部分：常驻关卡提示 ==========
            if (regularTips.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (expanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        regularTips.forEach { tip ->
                            val color = when {
                                tip.trimStart().startsWith("(") -> MaterialTheme.colorScheme.onSurfaceVariant // 仓库信息用灰色
                                else -> MaterialTheme.colorScheme.onSecondaryContainer // 资源提示用绿色
                            }
                            Text(
                                text = "· $tip",
                                style = MaterialTheme.typography.labelSmall,
                                color = color
                            )
                        }
                    }
                }
            } else if (activityTips.isEmpty() && !isResourceCollectionOpen) {
                // 无活动也无常驻提示时显示标题行
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
