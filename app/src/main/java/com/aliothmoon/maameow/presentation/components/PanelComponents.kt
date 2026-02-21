package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon

/**
 * 带可展开提示的复选框
 *
 * 复选框旁边显示小i图标，点击图标展开/收起提示文字
 *
 * @param checked 是否选中
 * @param onCheckedChange 选中状态变化回调
 * @param label 标签文本
 * @param tipText 提示文本
 * @param modifier 修饰符
 * @param enabled 是否启用
 */
@Composable
fun CheckBoxWithExpandableTip(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    tipText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var tipExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CheckBoxWithLabel(
                checked = checked,
                onCheckedChange = onCheckedChange,
                label = label,
                enabled = enabled
            )
            ExpandableTipIcon(
                expanded = tipExpanded,
                onExpandedChange = { tipExpanded = it }
            )
        }
        ExpandableTipContent(
            visible = tipExpanded,
            tipText = tipText,
            modifier = Modifier.padding(start = 32.dp)
        )
    }
}

/**
 * 带标签的复选框
 *
 * @param checked 是否选中
 * @param onCheckedChange 选中状态变化回调
 * @param label 标签文本
 * @param modifier 修饰符
 * @param enabled 是否启用
 * @param subtitle 副标题（可选）
 */
@Composable
fun CheckBoxWithLabel(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) Color.Unspecified else Color.Gray
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}



/**
 * 空配置提示
 */
@Composable
fun EmptyConfigHint() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "<- 请选择任务进行配置",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )
    }
}

/**
 * 占位内容
 */
@Composable
fun PlaceholderContent(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}
