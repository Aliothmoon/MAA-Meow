package com.aliothmoon.maameow.presentation.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.constant.Routes

sealed class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object HOME : BottomNavTab(
        route = Routes.HOME,
        label = "首页",
        icon = Icons.Default.Home
    )

    data object BACKGROUND : BottomNavTab(
        route = Routes.BACKGROUND_TASK,
        label = "后台任务",
        icon = Icons.Default.PlayArrow
    )

    companion object {
        val all = listOf(HOME, BACKGROUND)
    }
}

@Composable
fun AppBottomNavigation(
    currentRoute: String,
    onTabSelected: (BottomNavTab) -> Unit
) {
    Surface(color = Color.White) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = Color(0xFFe5e7eb))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.all.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val contentColor = if (selected)
                        MaterialTheme.colorScheme.primary
                    else
                        Color(0xFF9ca3af)

                    Column(
                        modifier = Modifier
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(22.dp),
                            tint = contentColor
                        )
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}
