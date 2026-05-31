package com.aliothmoon.maameow.presentation.view.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.state.HomeUiState
import com.aliothmoon.maameow.presentation.viewmodel.HomeViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixHomeView(
    homeViewModel: HomeViewModel,
    navController: NavController,
    backgroundTaskViewModel: com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
) {
    val uiState by homeViewModel.uiState.collectAsState()

    Scaffold {
        Column(modifier = Modifier.fillMaxSize()) {
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MaaDesignTokens.Spacing.lg,
                    end = MaaDesignTokens.Spacing.lg,
                    top = MaaDesignTokens.Spacing.sm,
                    bottom = MaaDesignTokens.Spacing.lg
                ),
                verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm)
            ) {
                // Status card
                item {
                    MiuixStatusCard(uiState = uiState)
                }

                // Task list placeholder
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.home_welcome_message),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(MaaDesignTokens.Spacing.md)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixStatusCard(
    uiState: HomeUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(MaaDesignTokens.Spacing.lg)
        ) {
            Text(
                text = stringResource(R.string.home_status_title),
                style = MiuixTheme.textStyles.headline2,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(MaaDesignTokens.Spacing.xs))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(MaaDesignTokens.Spacing.xs))
                Text(
                    text = when (uiState) {
                        is HomeUiState.Ready -> stringResource(R.string.home_status_idle)
                        is HomeUiState.Loading -> stringResource(R.string.home_status_loading)
                        is HomeUiState.Error -> stringResource(R.string.home_status_error)
                    },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }
}
