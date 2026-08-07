package com.aliothmoon.maameow.presentation.view.panel.fight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.InlineConfirmPanel
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon


/**
 * 允许保存源石使用区域
 * 启用前二次确认走 InlineConfirmPanel（悬浮窗弹不出 Dialog）
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
            label = stringResource(R.string.panel_stone_allow_save)
        )

        InlineConfirmPanel(
            visible = showWarningPanel,
            title = stringResource(R.string.common_warning),
            message = stringResource(R.string.panel_stone_warning_message),
            confirmText = stringResource(R.string.panel_stone_enable_confirm),
            onConfirm = {
                onConfigChange(config.copy(allowUseStoneSave = true))
                showWarningPanel = false
            },
            onDismiss = { showWarningPanel = false },
        )
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
    val tipText = stringResource(R.string.panel_stone_unsaved_tip)

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
                label = stringResource(R.string.panel_stone_use)
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
