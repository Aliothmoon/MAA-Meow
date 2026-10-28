package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import com.aliothmoon.maameow.presentation.view.panel.common.bringIntoViewOnExpand
import com.aliothmoon.maameow.theme.MaaThemeAlphas
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

private const val IME_SETTLE_MS = 350L

@Composable
fun CoreCharSelector(
    value: String,
    onValueChange: (String) -> Unit,
    theme: String,
    resourceDataManager: ResourceDataManager,
    modifier: Modifier = Modifier,
    /** 该主题的开局配置建议，为 null 时不显示提示入口 */
    themeTip: String? = null,
    title: String = stringResource(R.string.core_char_selector_title),
    enabled: Boolean = true,
) {
    val coroutineScope = rememberCoroutineScope()

    var tipExpanded by remember { mutableStateOf(false) }

    // 输入草稿独立于生效配置
    var inputText by remember(value) { mutableStateOf(value) }
    var cursorToEndRequest by remember { mutableIntStateOf(0) }

    var isValid by remember { mutableStateOf(true) }
    var showSuggestions by remember { mutableStateOf(false) }

    var isValidating by remember { mutableStateOf(false) }

    var recommendedChars by remember { mutableStateOf<List<String>>(emptyList()) }

    var filteredSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    // 元数据可能晚于界面加载，要订阅
    val roguelikeChars by resourceDataManager.roguelikeCoreCharacters.collectAsStateWithLifecycle()
    val themeChars = remember(theme, roguelikeChars) { roguelikeChars[theme].orEmpty() }
    LaunchedEffect(themeChars) {
        recommendedChars = themeChars
        if (inputText.isBlank()) {
            filteredSuggestions = themeChars
        }
    }

    fun handleInputChange(newValue: String) {
        inputText = newValue
        showSuggestions = true
        Timber.d("[CoreCharSelector] handleInputChange: newValue='$newValue', currentValue='$value'")

        if (newValue.isBlank()) {
            isValid = true
            isValidating = false
            filteredSuggestions = recommendedChars
            Timber.d("[CoreCharSelector] 空字符串，设置 isValid=true")
            if (value != newValue) {
                Timber.d("[CoreCharSelector] 更新配置为空字符串")
                onValueChange(newValue)
            }
        } else {
            isValidating = true
            Timber.d("[CoreCharSelector] 开始校验: '$newValue'")
            coroutineScope.launch {
                Timber.d("[CoreCharSelector] 开始校验: isValidOperatorName")
                val validationResult = resourceDataManager.isValidOperatorName(newValue)
                Timber.d("[CoreCharSelector] 校验结果: validationResult=$validationResult, newValue='$newValue'")

                // 补查非推荐干员，避免有效名称搜不到
                val newSuggestions = recommendedChars
                    .filter { it.contains(newValue, ignoreCase = true) }
                    .ifEmpty { resourceDataManager.searchOperatorNames(newValue, 15) }
                Timber.d("[CoreCharSelector] 建议列表计算完成: ${newSuggestions.size} 个结果")

                // 丢弃过期校验结果
                if (inputText != newValue) {
                    Timber.d("[CoreCharSelector] 输入已变化，跳过此次校验结果: 当前='$inputText', 校验='$newValue'")
                    return@launch
                }

                isValid = validationResult
                isValidating = false
                filteredSuggestions = newSuggestions
                Timber.d("[CoreCharSelector] UI更新完成: isValid=$isValid, isValidating=$isValidating")

                // 只有校验通过时才更新配置值
                if (validationResult && value != newValue) {
                    Timber.d("[CoreCharSelector] 校验通过，更新配置: '$newValue'")
                    onValueChange(newValue)
                } else if (!validationResult) {
                    Timber.d("[CoreCharSelector] 校验失败，不更新配置。当前配置值保持: '$value'")
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = MaaThemeAlphas.DISABLED)
        )

        if (themeTip != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpandableTipIcon(
                    expanded = tipExpanded,
                    onExpandedChange = { tipExpanded = it }
                )
                RainbowFlowText(
                    text = stringResource(R.string.panel_roguelike_theme_tip_recommended),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { tipExpanded = !tipExpanded }
                )
            }
            ExpandableTipContent(visible = tipExpanded, tipText = themeTip)
        }

        val showNotFound = !isValid && !isValidating && inputText.isNotBlank()
        val showSuggestionList = enabled && showSuggestions && filteredSuggestions.isNotEmpty()

        // 候选区变化后重新定位，避免键盘遮挡输入框
        val inputAreaRequester = remember { BringIntoViewRequester() }
        var inputFocused by remember { mutableStateOf(false) }
        LaunchedEffect(inputFocused, showNotFound, showSuggestionList, filteredSuggestions) {
            if (!inputFocused) return@LaunchedEffect
            withFrameNanos { }
            withFrameNanos { }
            inputAreaRequester.bringIntoView()
            delay(IME_SETTLE_MS.milliseconds)
            inputAreaRequester.bringIntoView()
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .bringIntoViewRequester(inputAreaRequester)
                // 悬浮窗点候选和图标不清焦
                .inputFocusGroup()
                .onFocusChanged { inputFocused = it.hasFocus }
        ) {
            // 头像使用生效配置，避免输入时闪烁
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (value.isNotBlank()) {
                    OperAvatarByName(
                        name = value,
                        modifier = Modifier.size(40.dp),
                        resourceDataManager = resourceDataManager,
                    )
                }
                ITextField(
                    value = inputText,
                    cursorToEndRequest = cursorToEndRequest,
                    onValueChange = { newValue ->
                        handleInputChange(newValue)
                    },
                    placeholder = stringResource(R.string.core_char_selector_placeholder),
                    outlineColor = if (!isValid && !isValidating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    trailingIcon = if (enabled && (inputText.isNotEmpty() || recommendedChars.isNotEmpty())) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (inputText.isNotEmpty()) {
                                    IconButton(
                                        onClick = { handleInputChange("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.common_clear),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (recommendedChars.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            showSuggestions = !showSuggestions
                                            if (showSuggestions) {
                                                filteredSuggestions = recommendedChars
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (showSuggestions) {
                                                Icons.Default.KeyboardArrowUp
                                            } else {
                                                Icons.Default.KeyboardArrowDown
                                            },
                                            contentDescription = stringResource(
                                                if (showSuggestions) R.string.core_char_selector_collapse
                                                else R.string.core_char_selector_recommended
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    }
                )
            }

            if (showNotFound) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.core_char_selector_not_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (showSuggestionList) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .bringIntoViewOnExpand(true)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    LazyColumn {
                        items(filteredSuggestions) { charName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // 从建议列表选择的干员一定是有效的
                                        inputText = charName
                                        cursorToEndRequest++
                                        showSuggestions = false
                                        isValid = true
                                        isValidating = false
                                        onValueChange(charName)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OperAvatarByName(
                                    name = charName,
                                    modifier = Modifier.size(28.dp),
                                    resourceDataManager = resourceDataManager,
                                )
                                Text(
                                    text = charName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (charName in recommendedChars) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
