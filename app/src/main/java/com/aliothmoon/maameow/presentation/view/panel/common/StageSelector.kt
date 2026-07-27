package com.aliothmoon.maameow.presentation.view.panel.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.resource.StageAliasMapper
import com.aliothmoon.maameow.data.resource.StageGroup
import com.aliothmoon.maameow.presentation.components.ITextFieldWithFocus

/**
 * 已选关卡徽章：主色底、白字、圆角
 * 与选关状态卡片「当前执行」徽章共用同一样式
 */
@Composable
internal fun StageBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * 关卡代码 → 展示文案（选关徽章、计划摘要等唯一入口）：
 * - 空串 → [emptyLabel]（默认「当前/上次」）
 * - Annihilation 且提供了 [annihilationDisplayName] → 用该名
 * - 否则在 [stageGroups] 里查 displayName，查不到回退代码本身
 */
@Composable
fun stageDisplayName(
    code: String,
    stageGroups: List<StageGroup>,
    emptyLabel: String = stringResource(R.string.panel_fight_stage_reset_current),
    annihilationDisplayName: String? = null,
): String = when {
    code.isEmpty() -> emptyLabel
    code == "Annihilation" && annihilationDisplayName != null -> annihilationDisplayName
    else -> stageGroups.firstNotNullOfOrNull { group ->
        group.stages.firstOrNull { it.code == code }?.displayName
    } ?: code
}

/**
 * 分组关卡选择按钮组（可折叠）
 * 标题行：左侧区块名，右侧「已选关卡」徽章 + 展开/收起箭头；点击标题行切换折叠
 * 展开后显示分组标题 + 每个分组下的关卡自动换行平铺
 * 默认折叠
 */
@Composable
internal fun GroupedStageButtonGroup(
    label: String,
    selectedValue: String,
    stageGroups: List<StageGroup>,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    annihilationDisplayName: String? = null,
    onRemove: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDisplay = stageDisplayName(
        code = selectedValue,
        stageGroups = stageGroups,
        annihilationDisplayName = annihilationDisplayName,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.bringIntoViewOnExpand(expanded),
    ) {
        // 折叠标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            StageBadge(text = selectedDisplay)
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            if (onRemove != null) {
                CollapsibleRowTrailing(onRemove)
            }
        }

        // 分组内容（折叠时隐藏）
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stageGroups.forEach { group ->
                    // TODO: i18n — 用 group.isPermanent 替代硬编码字符串比较
                    val displayTitle = if (group.isPermanent) {
                        stringResource(R.string.panel_fight_stage_group_permanent)
                    } else {
                        group.title
                    }
                    // 分组标题
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        // TODO: i18n — 用 group.isPermanent 替代硬编码字符串比较
                        color = if (group.isPermanent) Color(0xFF388E3C) else Color(0xFFE65100),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // 分组内的关卡（自动换行平铺）
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        group.stages.forEach { stage ->
                            val isSelected = stage.code == selectedValue
                            val isOpen = stage.isOpenToday
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onItemSelected(stage.code) },
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    !isOpen -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = if (stage.code == "Annihilation" && annihilationDisplayName != null) {
                                        annihilationDisplayName
                                    } else {
                                        stage.displayName
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        !isOpen -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StageRow(
    onRemove: (() -> Unit)?,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()
        if (onRemove != null) {
            CollapsibleRowTrailing(onRemove)
        }
    }
}

@Composable
private fun CollapsibleRowTrailing(
    onRemove: () -> Unit,
) {
    IconButton(
        onClick = onRemove,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.panel_fight_remove_stage),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 关卡代码输入框
 * 支持别名自动映射：失去焦点时自动转换别名为实际关卡代码
 *
 * 例如：龙门币 → CE-6，经验 → LS-6
 *
 */
@Composable
internal fun StageInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    stageCodes: List<String>,
    modifier: Modifier = Modifier
) {
    var textValue by remember(value) { mutableStateOf(value) }
    var showConvertedHint by remember { mutableStateOf(false) }
    var convertedCode by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ITextFieldWithFocus(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                // 检查是否是已知别名，显示转换提示
                val mapped = StageAliasMapper.mapToStageCode(newValue, stageCodes)
                if (mapped != newValue.uppercase() && newValue.isNotBlank()) {
                    showConvertedHint = true
                    convertedCode = mapped
                } else {
                    showConvertedHint = false
                }
            },
            onFocusLost = {
                if (textValue.isNotBlank()) {
                    // 失去焦点时应用别名映射
                    val mapped = StageAliasMapper.mapToStageCode(textValue, stageCodes)
                    textValue = mapped
                    onValueChange(mapped)
                    showConvertedHint = false
                }
            },
            label = label,
            placeholder = placeholder,
            singleLine = true,
            supportingText = if (showConvertedHint) {
                {
                    Text(
                        stringResource(R.string.panel_fight_converted_prefix, convertedCode),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else null
        )
    }
}
