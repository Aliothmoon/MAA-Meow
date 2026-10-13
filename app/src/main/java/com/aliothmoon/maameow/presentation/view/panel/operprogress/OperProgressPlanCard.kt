package com.aliothmoon.maameow.presentation.view.panel.operprogress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.OperProgressPlan
import com.aliothmoon.maameow.data.model.maxProgressElite
import com.aliothmoon.maameow.data.model.progressSkillCount
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.presentation.components.OperAvatar
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility

@Composable
internal fun OperProgressPlanCard(
    plan: OperProgressPlan,
    oper: CharacterInfo?,
    displayName: String,
    index: Int,
    expanded: Boolean,
    enabled: Boolean,
    dragModifier: Modifier,
    onToggle: () -> Unit,
    onChange: (OperProgressPlan) -> Unit,
    onRemove: () -> Unit,
) {
    val removeLabel = stringResource(R.string.oper_progress_remove, displayName)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            // 标题与折叠摘要整块可点，涟漪随卡片圆角铺满；展开区的选项自带点击，不纳入
            Column(Modifier.clickable(onClickLabel = stringResource(R.string.oper_progress_expand), onClick = onToggle)) {
                Row(modifier = Modifier.background(if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                    else MaterialTheme.colorScheme.surface).padding(start = 4.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // 只有把手能拖
                    Icon(Icons.Default.DragIndicator, stringResource(R.string.oper_progress_reorder),
                        modifier = Modifier.padding(end = 2.dp).size(32.dp).then(dragModifier).padding(6.dp),
                        tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (oper != null) OperAvatar(oper.id, Modifier.size(36.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(displayName, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.oper_progress_order, index),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                            Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!expanded) {
                    val targets = buildList {
                        if (plan.elite > 0) add(stringResource(R.string.oper_progress_elite_target, plan.elite))
                        if (plan.skillLevel > 0) add(stringResource(R.string.oper_progress_skill_target, plan.skillLevel))
                        if (plan.skillMastery.any { it > 0 }) {
                            add(stringResource(R.string.oper_progress_mastery_summary,
                                plan.skillMastery.take(oper?.progressSkillCount ?: 3).joinToString(" / ") { if (it == 0) "—" else it.toString() }))
                        }
                    }
                    FlowRow(modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (targets.isEmpty()) Text(stringResource(R.string.oper_progress_no_targets),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        targets.forEach { target ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp)) {
                                Text(target, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
                plan.training?.let {
                    Text(stringResource(R.string.oper_progress_training, it.skill, it.level),
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp))
                }
            }
            MaaAnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TargetChoices(stringResource(R.string.oper_progress_elite), plan.elite,
                        listOf(0) + (1..(oper?.maxProgressElite ?: 2)), enabled) {
                        onChange(plan.copy(elite = it))
                    }
                    TargetChoices(stringResource(R.string.oper_progress_skill), plan.skillLevel,
                        listOf(0) + (2..7), enabled) { onChange(plan.withSkillLevel(it)) }
                    if ((oper?.rarity ?: 0) >= 4) {
                        repeat(oper?.progressSkillCount ?: 3) { skill ->
                            TargetChoices(stringResource(R.string.oper_progress_mastery, skill + 1),
                                plan.skillMastery.getOrElse(skill) { 0 }, (0..3).toList(), enabled) {
                                onChange(plan.withMastery(skill, it))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    TextButton(
                        onClick = onRemove, enabled = enabled, modifier = Modifier.align(Alignment.End)
                            .semantics { contentDescription = removeLabel },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.oper_progress_remove_plan), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** 0 表示不设置 */
@Composable
private fun TargetChoices(label: String, value: Int, choices: List<Int>, enabled: Boolean, onSelect: (Int) -> Unit) {
    val notSet = stringResource(R.string.oper_progress_not_set)
    SelectableChipGroup(
        label = label,
        selectedValue = value,
        options = choices.map { it to if (it == 0) notSet else it.toString() },
        onSelected = onSelect,
        enabled = enabled,
    )
}
