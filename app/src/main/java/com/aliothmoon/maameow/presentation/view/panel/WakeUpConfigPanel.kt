package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import org.koin.compose.koinInject

/**
 * 开始唤醒配置面板
 *
 * see StartUpSettingsUserControl.xaml
 * 包含客户端类型选择功能
 */
@Composable
fun WakeUpConfigPanel(
    config: WakeUpConfig,
    onConfigChange: (WakeUpConfig) -> Unit,
    compositionService: MaaCompositionService = koinInject()
) {
    val state = compositionService.state.collectAsStateWithLifecycle()
    val isTaskActive =
        state.value == MaaExecutionState.STARTING || state.value == MaaExecutionState.RUNNING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 客户端类型选择
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "客户端类型",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WakeUpConfig.CLIENT_TYPE_OPTIONS.forEach { (value, displayName) ->
                    val isSelected = value == config.clientType
                    val chipColor = when {
                        isSelected -> MaterialTheme.colorScheme.primary.copy(
                            alpha = if (isTaskActive) 0.5f else 1f
                        )

                        else -> Color(0xFFE0E0E0)
                    }
                    val textColor = when {
                        isSelected -> Color.White
                        isTaskActive -> Color.Gray
                        else -> Color.DarkGray
                    }
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .then(
                                if (isTaskActive) Modifier
                                else Modifier.clickable { onConfigChange(config.copy(clientType = value)) }
                            ),
                        color = chipColor,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)

        // 启动游戏开关
        CheckBoxWithLabel(
            checked = config.startGameEnabled,
            onCheckedChange = { onConfigChange(config.copy(startGameEnabled = it)) },
            label = "启动游戏"
        )

        // TODO 服务器信息
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.spacedBy(8.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Text(
//                text = "服务器:",
//                style = MaterialTheme.typography.bodySmall,
//                color = Color.Gray
//            )
//            Text(
//                text = config.getServerType(),
//                style = MaterialTheme.typography.bodySmall,
//                fontWeight = FontWeight.Medium,
//                color = MaterialTheme.colorScheme.primary
//            )
//        }
    }
}
