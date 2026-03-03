package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.presentation.components.CheckBoxWithExpandableTip
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import com.aliothmoon.maameow.presentation.viewmodel.CopilotViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

@Composable
fun AutoBattlePanel(
    modifier: Modifier = Modifier,
    viewModel: CopilotViewModel = koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maaState by viewModel.maaState.collectAsStateWithLifecycle()
    val isRunning = maaState == MaaExecutionState.RUNNING
    val isStarting = maaState == MaaExecutionState.STARTING

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            val tabs = listOf(
                "主线/活动",
                "保全派驻",
                "悖论模拟"
            ).mapIndexed { index, title -> index to title }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEach { (index, title) ->
                        val selected = state.tabIndex == index
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color(0xFFF3F3F3)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.onTabChanged(index) }
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        item { HorizontalDivider() }

        item {
            ITextField(
                value = state.inputText,
                onValueChange = viewModel::onInputChanged,
                label = "作业路径/神秘代码",
                placeholder = "输入 maa://1234、1234",
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFF5F5F5)
            ) {
                Text(
                    text = state.statusMessage.ifBlank { "等待操作..." },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::onParseSingleInput,
                    enabled = !state.isLoading && !isStarting
                ) {
                    Text(if (state.isLoading) "读取中..." else "读取作业")
                }
                Button(
                    onClick = viewModel::onParseSetInput,
                    enabled = !state.isLoading && !isStarting && state.tabIndex != 1
                ) {
                    Text("读取作业集")
                }
            }
        }

        // 作业详情 + 链接跳转
        if (state.currentCopilot != null) {
            val doc = state.currentCopilot!!.doc
            val hasDetail = doc.title.isNotBlank() || doc.details.isNotBlank() || state.operatorSummary.isNotBlank()
            val hasLinks = state.copilotUrl.isNotBlank() || state.mapUrl.isNotBlank() || state.videoUrl.isNotBlank()
            if (hasDetail || hasLinks) {
                item {
                    val uriHandler = LocalUriHandler.current
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (doc.title.isNotBlank()) {
                                Text(
                                    text = doc.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            if (doc.details.isNotBlank()) {
                                Text(
                                    text = doc.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            if (state.operatorSummary.isNotBlank()) {
                                Text(
                                    text = state.operatorSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            if (hasLinks) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (state.copilotUrl.isNotBlank()) {
                                        Text(
                                            text = "作业详情",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.clickable { uriHandler.openUri(state.copilotUrl) }
                                        )
                                    }
                                    if (state.mapUrl.isNotBlank()) {
                                        Text(
                                            text = "地图",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.clickable { uriHandler.openUri(state.mapUrl) }
                                        )
                                    }
                                    if (state.videoUrl.isNotBlank()) {
                                        Text(
                                            text = "视频",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.clickable { uriHandler.openUri(state.videoUrl) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            val formationVisible = state.tabIndex == 0
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (formationVisible) {
                    CheckBoxWithExpandableTip(
                        checked = state.config.formation,
                        onCheckedChange = {
                            viewModel.onConfigChanged(state.config.copy(formation = it))
                        },
                        label = "自动编队",
                        tipText = "自动编队可能无法识别带有 ｢特别关注｣ 标记的干员"
                    )
                }
                if (formationVisible && state.config.formation) {
                    CheckBoxWithLabel(
                        checked = state.config.useFormation,
                        onCheckedChange = { enabled ->
                            viewModel.onConfigChanged(
                                state.config.copy(
                                    useFormation = enabled,
                                    formationIndex = state.config.formationIndex.coerceIn(1, 4)
                                )
                            )
                        },
                        label = "使用编队"
                    )

                    if (state.config.useFormation) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 4).forEach { index ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        viewModel.onConfigChanged(
                                            state.config.copy(
                                                formationIndex = index
                                            )
                                        )
                                    }
                                ) {
                                    RadioButton(
                                        selected = state.config.formationIndex == index,
                                        onClick = {
                                            viewModel.onConfigChanged(
                                                state.config.copy(
                                                    formationIndex = index
                                                )
                                            )
                                        }
                                    )
                                    Text(index.toString())
                                }
                            }
                        }
                    }

                    CheckBoxWithExpandableTip(
                        checked = state.config.ignoreRequirements,
                        onCheckedChange = {
                            viewModel.onConfigChanged(state.config.copy(ignoreRequirements = it))
                        },
                        label = "忽略干员属性要求",
                        tipText = "某些作业需要特定模组等作为前置条件\n勾选此项将跳过这些检查，但可能导致作业无法正常运行"
                    )

                    CheckBoxWithExpandableTip(
                        checked = state.config.useSupportUnit,
                        onCheckedChange = {
                            viewModel.onConfigChanged(state.config.copy(useSupportUnit = it))
                        },
                        label = "借助战",
                        tipText = "缺一个还能用用，缺两个以上还是换份作业吧"
                    )

                    if (state.config.useSupportUnit) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "补漏", 3 to "随机").forEach { (value, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        viewModel.onConfigChanged(state.config.copy(supportUnitUsage = value))
                                    }
                                ) {
                                    RadioButton(
                                        selected = state.config.supportUnitUsage == value,
                                        onClick = {
                                            viewModel.onConfigChanged(
                                                state.config.copy(
                                                    supportUnitUsage = value
                                                )
                                            )
                                        }
                                    )
                                    Text(label)
                                }
                            }
                        }
                    }

                    CheckBoxWithLabel(
                        checked = state.config.addTrust,
                        onCheckedChange = { viewModel.onConfigChanged(state.config.copy(addTrust = it)) },
                        label = "补充低信赖干员"
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.tabIndex != 1) {
                    CheckBoxWithExpandableTip(
                        checked = state.useCopilotList,
                        onCheckedChange = viewModel::onToggleListMode,
                        enabled = state.tabIndex == 0 || state.tabIndex == 2,
                        label = "战斗列表",
                        tipText = """
仅支持以下模式:
  1. 主线: 同一章节内导航
  2. SideStory: 当前页面内导航（普通/EX/S 不能互跳）
  3. 故事集: 当前页面内导航
  4. 悖论模拟: 从干员列表启动
请在对应界面启动，不支持跨章节导航

当 ｢战斗列表｣ 启用后, 选择单个作业时会自动添加到 ｢战斗列表｣
                        """.trimIndent()
                    )
                }

                if (state.useCopilotList && state.tabIndex == 0) {
                    CheckBoxWithLabel(
                        checked = state.config.useSanityPotion,
                        onCheckedChange = {
                            viewModel.onConfigChanged(state.config.copy(useSanityPotion = it))
                        },
                        label = "使用理智药"
                    )
                }

                if (!state.useCopilotList && state.tabIndex != 0 && state.tabIndex != 2) {
                    CheckBoxWithLabel(
                        checked = state.config.loop,
                        onCheckedChange = {
                            viewModel.onConfigChanged(state.config.copy(loop = it))
                        },
                        label = "循环次数"
                    )
                    if (state.config.loop) {
                        ITextField(
                            value = state.config.loopTimes.toString(),
                            onValueChange = { text ->
                                text.toIntOrNull()?.let {
                                    viewModel.onConfigChanged(
                                        state.config.copy(
                                            loopTimes = it.coerceAtLeast(
                                                1
                                            )
                                        )
                                    )
                                }
                            },
                            label = "循环次数",
                            placeholder = "1"
                        )
                    }
                }
            }
        }

        item {
            if (state.useCopilotList && (state.tabIndex == 0 || state.tabIndex == 2)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "战斗列表",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    var sequenceTipExpanded by remember { mutableStateOf(false) }
                    ExpandableTipIcon(
                        expanded = sequenceTipExpanded,
                        onExpandedChange = { sequenceTipExpanded = it })
                    ExpandableTipContent(
                        visible = sequenceTipExpanded,
                        tipText = "标签顺序可拖动"
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF8F8F8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.taskList.isEmpty()) {
                        Text(
                            "暂无条目",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        ReorderableColumn(
                            list = state.taskList,
                            onSettle = { from, to -> viewModel.onReorderList(from, to) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) { index, item, isDragging ->
                            key("${item.filePath}-${item.name}-$index") {
                                ReorderableItem {
                                    Surface(
                                        tonalElevation = if (isDragging) 4.dp else 0.dp,
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .longPressDraggableHandle()
                                            .fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CheckBoxWithLabel(
                                                checked = item.isChecked,
                                                onCheckedChange = { viewModel.onToggleListItem(index) },
                                                label = item.name + if (item.isRaid) " (突袭)" else "",
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedButton(
                                                onClick = { viewModel.onSelectListItem(index) },
                                                contentPadding = PaddingValues(
                                                    horizontal = 8.dp,
                                                    vertical = 0.dp
                                                )
                                            ) { Text("加载") }
                                            IconButton(
                                                onClick = { viewModel.onRemoveFromList(index) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "删除",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // TODO: 恢复手动输入关卡名 + 添加普通/添加突袭功能
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = viewModel::onCleanUnchecked) { Text("清除未勾选") }
                    OutlinedButton(onClick = viewModel::onClearList) { Text("清空列表") }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = viewModel::onStart,
                    enabled = !isRunning && !isStarting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isStarting) "启动中..." else "开始")
                }
                OutlinedButton(
                    onClick = viewModel::onStop,
                    enabled = isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("停止")
                }
            }
        }

        item {
            if (state.canLike) {
                Text(
                    text = "作业怎么样？评价下吧！",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.onRate(true) }) { Text("点赞") }
                    OutlinedButton(onClick = { viewModel.onRate(false) }) { Text("点踩") }
                }
            }
        }


        item {
            var expanded by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("小提示", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    ExpandableTipIcon(expanded = expanded, onExpandedChange = { expanded = it })
                }
                ExpandableTipContent(
                    visible = expanded,
                    tipText = """
1. 使用前请确认作业与所选的关卡类型一致。
2. 主线、故事集、SideStory: 请在关卡界面的右下角存在 ｢开始行动｣ 按钮界面启动。
3. 保全派驻: resource/copilot 文件夹内置多份作业。请先手动编队，在右下角存在 ｢开始部署｣ 按钮界面启动，可配合 ｢循环次数｣。
4. 悖论模拟: 选好技能后，在技能选择界面存在 ｢开始模拟｣ 按钮界面启动，1/2 星干员（无技能）在右下角存在 ｢开始模拟｣ 按钮界面开始。若使用 ｢战斗列表｣，请从干员列表 ｢等级/稀有度｣ 筛选下启动。
5. 使用好友助战时，请关闭 ｢自动编队｣ 和 ｢战斗列表｣，手动选择干员后，在编队界面右下角存在 ｢开始行动｣ 按钮界面启动。
6. 干员若被标记为 ｢特别关注｣，可能影响 ｢自动编队｣ 的识别与选择。建议使用 ｢自动编队｣ 时移除关注，或在报错后关闭 ｢自动编队｣，根据提示手动补充缺失的干员。
7. Copilot 作业站的神秘代码可在输入框输入后读取：
● 点击「读取作业」= 读取单作业。
● 点击「读取作业集」= 导入作业集。
8. 战斗列表:
● 选择作业后，检查下方关卡名是否正确 (例: CV-EX-1)。
● 可通过「添加普通/添加突袭」按钮添加任务（突袭仅主线）。
● 可通过「清空列表/清除未勾选」按钮快速整理任务。
● 请在能看到目标关卡名的界面启动，不支持跨章节导航。
● 遇到理智不足、战斗失败、未能三星结算时将自动中止。
                    """.trimIndent()
                )
            }
        }

    }
}


