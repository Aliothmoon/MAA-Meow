package com.aliothmoon.maameow.presentation.view.background

import android.text.InputType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.models.RunDurationLimit
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.ITextFieldWithFocus
import com.aliothmoon.maameow.theme.MaaThemeAlphas

// 快捷选项与配置管理共用的运行时长上限控件

/** 与开关行文字对齐的缩进行 */
@Composable
internal fun RunDurationSubRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(start = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun RunDurationStepperRow(
    minutes: Int,
    enabled: Boolean,
    onMinutesChange: (Int) -> Unit,
    onEditClick: () -> Unit,
) {
    RunDurationSubRow {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    enabled = enabled,
                    onClickLabel = stringResource(R.string.bg_auto_run_duration_limit_edit),
                    onClick = onEditClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val valueColor = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MaaThemeAlphas.DISABLED)
            }
            Text(
                text = stringResource(R.string.bg_auto_run_duration_limit_value, minutes),
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = valueColor,
            )
        }
        IconButton(
            onClick = { onMinutesChange(RunDurationStep.decrease(minutes)) },
            enabled = enabled && RunDurationStep.canDecrease(minutes),
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.bg_auto_run_duration_limit_decrease),
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(
            onClick = { onMinutesChange(RunDurationStep.increase(minutes)) },
            enabled = enabled && RunDurationStep.canIncrease(minutes),
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.bg_auto_run_duration_limit_increase),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun RunDurationInputDialog(
    minutes: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var input by remember { mutableStateOf(minutes.toString()) }
    AdaptiveTaskPromptDialog(
        visible = true,
        title = stringResource(R.string.bg_auto_run_duration_limit_input_title),
        onDismissRequest = onDismissRequest,
        onConfirm = {
            val parsed = RunDurationStep.parseInput(input)
            if (parsed == null) onDismissRequest() else onConfirm(parsed)
        },
        icon = Icons.Filled.Timer,
    ) {
        val hint = stringResource(
            R.string.bg_auto_run_duration_limit_input_hint,
            RunDurationLimit.MIN_MINUTES,
            RunDurationLimit.MAX_MINUTES,
        )
        ITextFieldWithFocus(
            value = input,
            onValueChange = { input = it },
            onFocusLost = {},
            label = stringResource(R.string.bg_auto_run_duration_limit_input_label),
            placeholder = hint,
            supportingText = { Text(hint) },
            inputFilter = RunDurationStep::acceptsInput,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            inputType = InputType.TYPE_CLASS_NUMBER,
        )
    }
}
