package com.aliothmoon.maameow.presentation.components

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.aliothmoon.maameow.theme.MaaDesignTokens

/** 卡片水平内边距：行按它外扩，涟漪铺满卡片 */
val LocalSettingRowBleed = staticCompositionLocalOf { 0.dp }

/** 横向外扩 [bleed]，占位宽度不变 */
fun Modifier.horizontalBleed(bleed: Dp): Modifier =
    if (bleed == 0.dp) this else layout { measurable, constraints ->
        val extra = (bleed * 2).roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = extra))
        layout((placeable.width - extra).coerceAtLeast(0), placeable.height) {
            placeable.place(-extra / 2, 0)
        }
    }

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    info: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    verticalPadding: Dp = MaaDesignTokens.Spacing.listItemVertical,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    toggled: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    val bleed = LocalSettingRowBleed.current
    var infoExpanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalBleed(bleed)
                .then(
                    when {
                        onClick == null -> Modifier
                        toggled != null -> Modifier.toggleable(
                            value = toggled,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = { onClick() },
                        )

                        else -> Modifier.clickable(enabled = enabled, onClick = onClick)
                    }
                )
                .heightIn(min = 56.dp)
                .padding(horizontal = bleed, vertical = verticalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(MaaDesignTokens.Spacing.lg))
            }
            Column(
                modifier = if (leading != null || trailing != null) {
                    Modifier
                        .weight(1f)
                        .padding(end = MaaDesignTokens.Spacing.lg)
                } else {
                    Modifier
                },
                verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.rowTitleGap),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) titleColor else titleColor.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (info != null) {
                        SettingInfoToggle(infoExpanded) { infoExpanded = !infoExpanded }
                    }
                }
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) descriptionColor else descriptionColor.copy(alpha = 0.6f),
                    )
                }
            }
            trailing?.invoke()
        }
        footer?.invoke()
        if (info != null) {
            SettingInfoText(info, infoExpanded, Modifier.padding(bottom = verticalPadding))
        }
    }
}

@Composable
fun SettingInfoToggle(expanded: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = stringResource(R.string.settings_row_info), onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.QuestionMark,
            contentDescription = stringResource(R.string.settings_row_info),
            tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
fun SettingInfoText(text: String, expanded: Boolean, modifier: Modifier = Modifier) {
    MaaAnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
