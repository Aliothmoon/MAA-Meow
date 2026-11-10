package com.aliothmoon.maameow.presentation.components

import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility

/** 凭证输入框：标题行兼折叠开关，填过后默认收起，尾部清除键太容易误触 */
@Composable
fun CollapsibleSecretField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    action: (@Composable () -> Unit)? = null,
    status: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf(value) }
    // 每次按键各自落盘，输入期间回流的是上一拍的旧值，接了就会冲掉刚打的字；失焦后再对齐
    LaunchedEffect(value, focused) {
        if (!focused && value != local) local = value
    }

    // 用户手动展开后以其选择为准；输入中不收，否则打第一个字落盘后就收起了
    var userExpanded by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val expanded = userExpanded ?: (value.isEmpty() || focused)
    val expandLabel = stringResource(R.string.common_expand)
    val collapseLabel = stringResource(R.string.common_collapse)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) collapseLabel else expandLabel,
                ) {
                    // 收起时把明文一并藏回去
                    if (expanded) visible = false
                    userExpanded = !expanded
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = titleColor,
                modifier = Modifier.weight(1f),
            )
            // 收起时才提示填没填，展开后输入框自己会说
            if (!expanded) {
                Text(
                    text = stringResource(
                        if (value.isEmpty()) R.string.update_cdk_unset else R.string.update_cdk_saved
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = if (expanded) collapseLabel else expandLabel,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MaaAnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = local,
                    onValueChange = {
                        local = it
                        onValueChange(it)
                    },
                    // 标题在上面那行，这里再挂 label 就重了
                    placeholder = { Text(placeholder) },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Row {
                            if (local.isNotEmpty()) {
                                IconButton(onClick = {
                                    local = ""
                                    onValueChange("")
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.common_clear),
                                    )
                                }
                            }
                            IconButton(onClick = { visible = !visible }) {
                                Icon(
                                    imageVector = if (visible) Icons.Outlined.Lock else Icons.Filled.Lock,
                                    contentDescription = stringResource(
                                        if (visible) R.string.settings_secret_hide else R.string.settings_secret_show
                                    ),
                                )
                            }
                        }
                    },
                )
                action?.invoke()
            }
        }

        status()
        footer()
    }
}
