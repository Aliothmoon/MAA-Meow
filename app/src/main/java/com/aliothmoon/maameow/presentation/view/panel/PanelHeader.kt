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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 面板标题栏
 */
@Composable
fun PanelHeader(
    selectedTab: PanelTab = PanelTab.TASKS,
    onTabSelected: (PanelTab) -> Unit = {},
    showActions: Boolean = true,
    isLocked: Boolean = false,
    onLockToggle: (Boolean) -> Unit = {},
    onHome: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = if (showActions) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tabContent = @Composable {
            PanelTab.entries.forEach { tab ->
                Text(
                    text = tab.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedTab == tab)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) }
                )
            }
        }

        if (showActions) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabContent()
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onHome,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "回到主界面",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        tint = if (isLocked)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            tabContent()
        }
    }
}
