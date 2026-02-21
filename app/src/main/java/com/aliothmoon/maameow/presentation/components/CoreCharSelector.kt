package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun CoreCharSelector(
    value: String,
    onValueChange: (String) -> Unit,
    theme: String,
    resourceDataManager: ResourceDataManager,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // 内部输入状态（与配置值分离，用于显示用户正在输入的内容）
    var inputText by remember(value) { mutableStateOf(value) }

    // 校验状态
    var isValid by remember { mutableStateOf(true) }
    var showSuggestions by remember { mutableStateOf(false) }

    // 是否正在校验（用于显示加载状态）
    var isValidating by remember { mutableStateOf(false) }

    // 推荐开局干员列表（按主题）
    var recommendedChars by remember { mutableStateOf<List<String>>(emptyList()) }

    // 过滤后的建议列表
    var filteredSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    // 加载推荐开局干员列表
    val themeChars = remember(theme) { resourceDataManager.getRoguelikeCoreCharList(theme) }
    LaunchedEffect(themeChars) {
        recommendedChars = themeChars
        if (inputText.isBlank()) {
            filteredSuggestions = themeChars
        }
    }

    // 处理输入变化的函数
    fun handleInputChange(newValue: String) {
        inputText = newValue
        // 输入时不显示建议列表
        showSuggestions = false
        Timber.d("[CoreCharSelector] handleInputChange: newValue='$newValue', currentValue='$value'")

        if (newValue.isBlank()) {
            // 空字符串始终有效
            isValid = true
            isValidating = false
            filteredSuggestions = recommendedChars
            Timber.d("[CoreCharSelector] 空字符串，设置 isValid=true")
            // 立即更新配置
            if (value != newValue) {
                Timber.d("[CoreCharSelector] 更新配置为空字符串")
                onValueChange(newValue)
            }
        } else {
            // 开始校验
            isValidating = true
            Timber.d("[CoreCharSelector] 开始校验: '$newValue'")
            coroutineScope.launch {
                Timber.d("[CoreCharSelector] 开始校验: isValidCharacterName")
                val validationResult = resourceDataManager.isValidCharacterName(newValue)
                Timber.d("[CoreCharSelector] 校验结果: validationResult=$validationResult, newValue='$newValue'")

                // 计算建议列表
                val newSuggestions = if (validationResult) {
                    recommendedChars.filter { it.contains(newValue, ignoreCase = true) }
                } else {
                    resourceDataManager.search(newValue, 15)
                }
                Timber.d("[CoreCharSelector] 建议列表计算完成: ${newSuggestions.size} 个结果")

                // 检查输入值是否仍然匹配（防止竞态条件）
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

    // 添加状态变化日志
    Timber.d("[CoreCharSelector] 渲染状态: inputText='$inputText', isValid=$isValid, isValidating=$isValidating, showError=${!isValid && !isValidating && inputText.isNotBlank()}")

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // 标签行 + 展开按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "开局干员",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            if (recommendedChars.isNotEmpty()) {
                Text(
                    text = if (showSuggestions) "收起" else "推荐",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        showSuggestions = !showSuggestions
                        if (showSuggestions) {
                            filteredSuggestions = recommendedChars
                        }
                    }
                )
            }
        }

        // 输入框
        Box {
            ITextField(
                value = inputText,
                onValueChange = { newValue ->
                    handleInputChange(newValue)
                },
                placeholder = "干员名称（可选）",
                outlineColor = if (!isValid && !isValidating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 错误提示 - 只有校验完成且失败时显示
        if (!isValid && !isValidating && inputText.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "未找到该干员，输入不会生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 建议列表
        if (showSuggestions && filteredSuggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = Color.LightGray,
                        shape = RoundedCornerShape(8.dp)
                    ),
                color = Color.White
            ) {
                LazyColumn {
                    items(filteredSuggestions) { charName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 从建议列表选择的干员一定是有效的
                                    inputText = charName
                                    showSuggestions = false
                                    isValid = true
                                    isValidating = false
                                    // 立即更新配置
                                    onValueChange(charName)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = charName,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (charName in recommendedChars) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.DarkGray
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
