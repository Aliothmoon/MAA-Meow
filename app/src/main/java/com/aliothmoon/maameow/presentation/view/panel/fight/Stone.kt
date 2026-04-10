package com.aliothmoon.maameow.presentation.view.panel.fight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon


/**
 * 允许保存源石使用区域
 * 使用内嵌式确认面板替代 AlertDialog（悬浮窗不支持 Dialog）
 */
@Composable
private fun AllowUseStoneSaveSection(
    config: FightConfig,
    onConfigChange: (FightConfig) -> Unit
) {
    var showWarningPanel by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckBoxWithLabel(
            checked = config.allowUseStoneSave,
            onCheckedChange = { checked ->
                if (checked) {
                    // 启用前显示警告面板
                    showWarningPanel = true
                } else {
                    onConfigChange(config.copy(allowUseStoneSave = false))
                }
            },
            label = stringResource(R.string.allow_stone_use)
        )

        // 内嵌式警告确认面板
        AnimatedVisibility(
            visible = showWarningPanel,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.warning),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(R.string.stone_warning_msg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        OutlinedButton(
                            onClick = { showWarningPanel = false },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = {
                                onConfigChange(config.copy(allowUseStoneSave = true))
                                showWarningPanel = false
                            },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.confirm_enable), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}


/**
 * 使用源石区域
 * 带小i图标展开提示（未保存设置警告）
 */
@Composable
private fun UseStoneSection(
    config: FightConfig,
    onConfigChange: (FightConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var tipExpanded by remember { mutableStateOf(false) }
    val tipText = stringResource(R.string.stone_not_saved_tip)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CheckBoxWithLabel(
                checked = config.useStone,
                onCheckedChange = {
                    onConfigChange(
                        config.copy(
                            useStone = it,
                            // 启用源石时，理智药自动设为 999
                            medicineNumber = if (it) 999 else config.medicineNumber,
                            useMedicine = if (it) true else config.useMedicine
                        )
                    )
                },
                label = stringResource(R.string.use_stone)
            )
            // 未保存设置时显示小i图标
            if (!config.allowUseStoneSave) {
                ExpandableTipIcon(
                    expanded = tipExpanded,
                    onExpandedChange = { tipExpanded = it }
                )
            }
        }
        // 未保存设置的警告提示
        ExpandableTipContent(
            visible = tipExpanded && !config.allowUseStoneSave,
            tipText = tipText
        )
    }
}