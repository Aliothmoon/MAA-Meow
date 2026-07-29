package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.presentation.viewmodel.ToolboxTab
import com.aliothmoon.maameow.presentation.viewmodel.ToolboxViewModel
import org.koin.compose.koinInject

@Composable
fun ToolboxPanel(
    modifier: Modifier = Modifier,
    viewModel: ToolboxViewModel = koinInject()
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val visibleTabs by viewModel.visibleTabs.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // 子 Tab：等分铺满；前台模式不展示牛牛抽卡
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleTabs.forEach { tab ->
                val selected = currentTab == tab
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clickable { viewModel.onTabChange(tab) }
                ) {
                    // Box 铺满 Surface，文字水平+垂直居中
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }

        // 内容区（前台不会落到 GACHA）
        when (currentTab) {
            ToolboxTab.MINI_GAME -> MiniGamePanel(delegate = viewModel.miniGame, modifier = Modifier.fillMaxSize())
            ToolboxTab.GACHA -> GachaPanel(viewModel = viewModel, modifier = Modifier.fillMaxSize())
            ToolboxTab.RECRUIT_CALC -> RecruitCalcPanel(modifier = Modifier.fillMaxSize())
            ToolboxTab.DEPOT -> DepotRecognitionPanel(modifier = Modifier.fillMaxSize())
            ToolboxTab.OPER_BOX -> OperBoxPanel(modifier = Modifier.fillMaxSize())
        }
    }
}
