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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.resource.ActivityManager.StageGroup
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * 今日开放关卡提示
 * 显示当日开放的活动关卡和资源本
 */
@Composable
fun TodayStagesHint(
    stageGroups: List<StageGroup>,
    isResourceCollectionOpen: Boolean,
    stageTips: List<String>
) {
    // 收集活动关卡分组（包含剩余天数）
    val activities = stageGroups.filter { it.title != "常驻关卡" }

    val todayOpenStages = stageGroups
        .find { it.title == "常驻关卡" }
        ?.stages
        ?.filter { it.isOpenToday }
        ?: emptyList()

    // 如果没有活动关卡也没有今日开放的资源关卡，不显示
    if (activities.isEmpty() && todayOpenStages.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val todayName = remember {
        when (LocalDate.now().dayOfWeek) {
            MONDAY -> "周一"
            TUESDAY -> "周二"
            WEDNESDAY -> "周三"
            THURSDAY -> "周四"
            FRIDAY -> "周五"
            SATURDAY -> "周六"
            SUNDAY -> "周日"
            else -> ""
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFE8F5E9),
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
                            tip.startsWith("｢") -> Color(0xFFE65100) // 活动提示用橙色
                            else -> Color(0xFFD84315) // 活动关卡掉落用深橙色
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
                            text = "· 资源收集活动进行中（资源本全开放）",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1976D2)
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
                        text = "今日关卡小提示（$todayName）",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E7D32)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = Color(0xFF2E7D32),
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
                                tip.trimStart().startsWith("(") -> Color(0xFF757575) // 仓库信息用灰色
                                else -> Color(0xFF388E3C) // 资源提示用绿色
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
                    text = "今日关卡小提示（$todayName）",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}