package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 面板标题栏
 */
@Composable
fun PanelHeader(
    selectedTab: PanelTab = PanelTab.TASKS,
    onTabSelected: (PanelTab) -> Unit = {},
    isLocked: Boolean = false,
    onLockToggle: (Boolean) -> Unit = {},
    onHome: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧 Tab 切换
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelTab.entries.forEach { tab ->
                Text(
                    text = tab.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedTab == tab) Color(0xFF2196F3) else Color.Gray,
                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(tab) }
                )
            }
        }

        // 右侧按钮组
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onHome,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "回到主界面",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = { onLockToggle(!isLocked) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = if (isLocked) Color(0xFF2196F3) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
