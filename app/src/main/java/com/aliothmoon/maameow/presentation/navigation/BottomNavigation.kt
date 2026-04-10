package com.aliothmoon.maameow.presentation.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.theme.MaaDesignTokens

sealed class BottomNavTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    data object HOME : BottomNavTab(
        route = Routes.HOME,
        labelRes = R.string.nav_home,
        icon = Icons.Default.Home
    )

    data object BACKGROUND : BottomNavTab(
        route = Routes.BACKGROUND_TASK,
        labelRes = R.string.nav_background_task,
        icon = Icons.Default.PlayArrow
    )

    data object SCHEDULE : BottomNavTab(
        route = Routes.SCHEDULE,
        labelRes = R.string.nav_schedule,
        icon = Icons.Default.DateRange
    )

    data object NOTIFICATION : BottomNavTab(
        route = Routes.NOTIFICATION,
        labelRes = R.string.nav_notification,
        icon = Icons.Default.Notifications
    )

    companion object {
        val all = listOf(HOME, BACKGROUND, SCHEDULE, NOTIFICATION)
    }
}

@Composable
fun AppBottomNavigation(
    currentRoute: String,
    onTabSelected: (BottomNavTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(thickness = MaaDesignTokens.Separator.thickness, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.all.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val contentColor = if (selected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

                    Column(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(tab) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = stringResource(tab.labelRes),
                            modifier = Modifier.size(20.dp),
                            tint = contentColor
                        )
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}
