package com.aliothmoon.maameow.presentation.view.panel.roguelike

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.RoguelikeConfig
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.enums.RoguelikeMode
import com.aliothmoon.maameow.presentation.components.CoreCharSelector
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.aliothmoon.maameow.domain.enums.UiUsageConstants.Roguelike as RoguelikeUi

/**
 * 自动肉鸽配置面板 - 迁移自 WPF RoguelikeSettingsUserControl.xaml
 *
 * 布局结构：
 * - Tab 1 (常规设置): 主题/难度/模式/分队/阵容/开局干员
 * - Tab 2 (高级设置): 投资/助战/开局次数/模式特殊选项
 */
@Composable
fun RoguelikeConfigPanel(
    config: RoguelikeConfig,
    onConfigChange: (RoguelikeConfig) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Tab 行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "常规设置",
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.primary else Color.Gray,
                fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                }
            )
            Text(
                text = "高级设置",
                style = MaterialTheme.typography.bodyMedium,
                color = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.primary else Color.Gray,
                fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))

        HorizontalPager(
            pageSize = PageSize.Fill,
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            userScrollEnabled = true
        ) { page ->
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = 12.dp, bottom = 8.dp)
            ) {
                when (page) {
                    0 -> {
                        item { BasicRoguelikeSettings(config, onConfigChange) }
                    }

                    1 -> {
                        item { AdvancedRoguelikeSettings(config, onConfigChange) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicRoguelikeSettings(
    config: RoguelikeConfig,
    onConfigChange: (RoguelikeConfig) -> Unit,
    resourceDataManager: ResourceDataManager = koinInject()
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 主题选择 - 使用按钮组
        RoguelikeButtonGroup(
            label = "肉鸽主题",
            selectedValue = config.theme,
            options = RoguelikeUi.THEME_OPTIONS,
            onValueChange = { newTheme ->
                // 切换主题时重置分队和模式（如果当前值不在新主题支持列表中）
                // WPF: UpdateRoguelikeSquadList (line 244-275)
                val newSquads = RoguelikeUi.getSquadOptionsForTheme(newTheme, config.mode)
                val newSquad =
                    if (config.squad in newSquads) config.squad else "指挥分队"

                // 验证当前模式是否在新主题支持的模式列表中
                val newMode = if (RoguelikeUi.isModeValidForTheme(config.mode, newTheme)) {
                    config.mode
                } else {
                    val fallbackName =
                        RoguelikeUi.getModeOptionsForTheme(newTheme).firstOrNull()?.first ?: "Exp"
                    RoguelikeMode.valueOf(fallbackName)
                }

                // WPF: Theme setter (line 383-401) - 验证难度是否超过新主题的最大难度
                val maxDiff = RoguelikeUi.getMaxDifficultyForTheme(newTheme)
                val newDifficulty = if (config.difficulty in 0..maxDiff ||
                    config.difficulty == -1 || config.difficulty == Int.MAX_VALUE
                ) {
                    config.difficulty
                } else {
                    -1  // WPF: 超出范围时重置为"不切换"
                }

                // 验证当前阵容是否在新主题支持的阵容列表中
                // WPF: UpdateRoguelikeRolesList (line 165)
                val newRolesList = RoguelikeUi.getRolesOptionsForTheme(newTheme)
                val newRoles =
                    if (newRolesList.any { it.first == config.roles }) config.roles else "稳扎稳打"

                // WPF: CollectibleModeSquad 验证 (line 274)
                val newCollectibleModeSquad =
                    if (config.collectibleModeSquad in newSquads) config.collectibleModeSquad
                    else newSquad

                // 过滤掉新主题不支持的开局奖励
                val validAwardKeys = RoguelikeUi.getCollectibleAwardOptions(newTheme).map { it.first }.toSet()
                val newCollectibleStartAwards = config.collectibleStartAwards.intersect(validAwardKeys)

                onConfigChange(
                    config.copy(
                        theme = newTheme,
                        squad = newSquad,
                        mode = newMode,
                        difficulty = newDifficulty,
                        roles = newRoles,
                        collectibleModeSquad = newCollectibleModeSquad,
                        collectibleStartAwards = newCollectibleStartAwards
                    )
                )
            }
        )

        // 难度选择 - 使用按钮组
        RoguelikeDifficultyButtonGroup(
            label = "难度",
            selectedValue = config.difficulty,
            theme = config.theme,
            onValueChange = { onConfigChange(config.copy(difficulty = it)) }
        )

        // 策略模式选择 - 使用按钮组（根据主题动态变化）
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RoguelikeButtonGroup(
                label = "策略",
                selectedValue = config.mode.name,
                options = RoguelikeUi.getModeOptionsForTheme(config.theme),
                onValueChange = {
                    val newMode = RoguelikeMode.valueOf(it)
                    var newConfig = config.copy(mode = newMode)
                    // WPF: Mode setter (line 416-419) 投资模式强制启用投资
                    if (newMode == RoguelikeMode.Investment) {
                        newConfig = newConfig.copy(investmentEnabled = true)
                    }
                    // WPF: Mode setter (line 422) 切换模式时验证分队有效性
                    val newSquads = RoguelikeUi.getSquadOptionsForTheme(config.theme, newMode)
                    val newSquad = if (newConfig.squad in newSquads) newConfig.squad else "指挥分队"
                    val newCollectibleSquad = if (newConfig.collectibleModeSquad in newSquads) newConfig.collectibleModeSquad else newSquad
                    newConfig = newConfig.copy(squad = newSquad, collectibleModeSquad = newCollectibleSquad)
                    onConfigChange(newConfig)
                }
            )

            // 模式说明 - 紧跟在策略按钮组下方
            val modeDescription =
                RoguelikeUi.MODE_OPTIONS.find { it.first == config.mode.name }?.second ?: ""
            if (modeDescription.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFE3F2FD),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        modeDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1976D2),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        // 分队选择 - 使用按钮组
        RoguelikeSquadButtonGroup(
            label = "开局分队",
            selectedValue = config.squad,
            theme = config.theme,
            mode = config.mode,
            onValueChange = { onConfigChange(config.copy(squad = it)) }
        )

        // 职业阵容 - 使用按钮组（根据主题动态变化）
        RoguelikeButtonGroup(
            label = "开局职业组",
            selectedValue = config.roles,
            options = RoguelikeUi.getRolesOptionsForTheme(config.theme),
            onValueChange = { onConfigChange(config.copy(roles = it)) }
        )

        // 核心干员选择 - 带校验和自动补全
        CoreCharSelector(
            value = config.coreChar,
            onValueChange = { onConfigChange(config.copy(coreChar = it)) },
            theme = config.theme,
            resourceDataManager = resourceDataManager,
            modifier = Modifier.fillMaxWidth()
        )
    }
}











