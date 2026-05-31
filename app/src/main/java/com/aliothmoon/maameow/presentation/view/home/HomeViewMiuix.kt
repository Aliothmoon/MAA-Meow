package com.aliothmoon.maameow.presentation.view.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.viewmodel.HomeViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.theme.MaaThemeAlphas
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

@Composable
fun MiuixHomeView(
    homeViewModel: HomeViewModel,
    navController: NavController
) {
    val uiState by homeViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.home_app_title),
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Routes.SETTINGS)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.home_cd_open_settings)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = MaaDesignTokens.Spacing.lg)
        ) {
            // Status card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(MaaDesignTokens.Card.innerPadding)
                ) {
                    Text(
                        text = stringResource(R.string.home_service_status),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(MaaDesignTokens.Spacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.home_status_ready),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(MaaDesignTokens.Spacing.md))

            // Screen info
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(MaaDesignTokens.Card.innerPadding)
                ) {
                    Text(
                        text = stringResource(R.string.home_screen_resolution),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(MaaDesignTokens.Spacing.md))

            // Resource version
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(MaaDesignTokens.Card.innerPadding)
                ) {
                    Text(
                        text = stringResource(R.string.home_resource_version_label),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(MaaDesignTokens.Spacing.md))

            // Run mode
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(MaaDesignTokens.Card.innerPadding)
                ) {
                    Text(
                        text = stringResource(R.string.home_run_mode_title),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(MaaDesignTokens.Spacing.md))

            // Permissions
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(MaaDesignTokens.Card.innerPadding)
                ) {
                    Text(
                        text = stringResource(R.string.home_permission_section),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(MaaDesignTokens.Spacing.md))

            // Overlay control
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(MaaDesignTokens.Card.innerPadding)
                ) {
                    Text(
                        text = stringResource(R.string.home_overlay_mode_title),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
