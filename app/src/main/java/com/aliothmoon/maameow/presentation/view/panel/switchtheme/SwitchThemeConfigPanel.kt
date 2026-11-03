package com.aliothmoon.maameow.presentation.view.panel.switchtheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.SwitchThemeConfig
import com.aliothmoon.maameow.presentation.components.GuideAnchor
import com.aliothmoon.maameow.presentation.components.guideHighlight
import com.aliothmoon.maameow.presentation.components.guideHighlighted
import com.aliothmoon.maameow.presentation.components.outlinedFieldLabelInset

@Composable
fun SwitchThemeConfigPanel(
    config: SwitchThemeConfig,
    onConfigChange: (SwitchThemeConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.panel_switch_theme_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 增删后重建输入节点，避免焦点和选区移到相邻候选
        key(config.themes.size) {
            config.themes.forEachIndexed { index, theme ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = theme,
                        onValueChange = { value ->
                            onConfigChange(config.copy(themes = config.themes.toMutableList().apply {
                                this[index] = value
                            }))
                        },
                        label = { Text(stringResource(R.string.panel_switch_theme_candidate, index + 1)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .guideHighlight(
                                GuideAnchor.THEME_FIELD,
                                index.toString(),
                                corner = 4.dp,
                                insetTop = outlinedFieldLabelInset(),
                            ),
                    )
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        onConfigChange(config.copy(themes = config.themes.filterIndexed { i, _ -> i != index }))
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.panel_switch_theme_remove, index + 1),
                        )
                    }
                }
            }
        }
        // 描边时去掉最小触控留白让框贴着按钮，按钮在末尾变矮不挤别的
        val addGuided = guideHighlighted(GuideAnchor.THEME_ADD)
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides
                if (addGuided) Dp.Unspecified else LocalMinimumInteractiveComponentSize.current,
        ) {
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    onConfigChange(config.copy(themes = config.themes + ""))
                },
                modifier = Modifier.guideHighlight(addGuided, corner = 8.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.panel_switch_theme_add))
            }
        }
    }
}
