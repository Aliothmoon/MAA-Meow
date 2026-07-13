package com.aliothmoon.maameow.presentation.view.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.preferences.WallpaperBlur
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel

/** Settings page body: preview, sliders, toggles, clear. */
@Composable
internal fun WallpaperSettingsBody(
    navController: NavController,
    viewModel: SettingsViewModel,
    session: WallpaperEditSession,
    wallpaperUri: String,
    wallpaperUpdateVersion: Int,
    wallpaperFrostedGlass: Boolean,
    wallpaperTextContrast: Boolean,
    localAlpha: Float,
    onLocalAlphaChange: (Float) -> Unit,
    localBlur: Float,
    onLocalBlurChange: (Float) -> Unit,
    screenRatio: Float,
    onChangeWallpaper: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_wallpaper_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.popBackStack() },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (wallpaperUri.isNotEmpty()) {
                    WallpaperPreviewCard(
                        wallpaperUri = wallpaperUri,
                        version = wallpaperUpdateVersion,
                        alpha = localAlpha / 100f,
                        blurPercent = localBlur.toInt(),
                        screenRatio = screenRatio,
                            onClick = { session.openExistingForEdit() },
                    )
                    Text(
                        text = stringResource(R.string.settings_wallpaper_edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = MaaDesignTokens.Spacing.listHorizontal,
                                vertical = MaaDesignTokens.Spacing.sm,
                            ),
                        verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sectionGap),
                    ) {
                        SettingsGroupCard {
                            SettingRow(
                                title = stringResource(R.string.settings_wallpaper_change),
                                onClick = { onChangeWallpaper() },
                            )
                        }
                        SettingsGroupCard {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    stringResource(R.string.settings_wallpaper_alpha, localAlpha.toInt()),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Slider(
                                    localAlpha,
                                    onValueChange = onLocalAlphaChange,
                                    valueRange = 0f..100f,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.settings_wallpaper_blur, localBlur.toInt()),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Slider(
                                    localBlur,
                                    onValueChange = onLocalBlurChange,
                                    valueRange = 0f..WallpaperBlur.PERCENT_MAX.toFloat(),
                                )
                            }
                        }
                        SettingsGroupCard {
                            SettingRow(
                                title = stringResource(R.string.settings_wallpaper_frosted_glass),
                                trailing = {
                                    Switch(
                                        checked = wallpaperFrostedGlass,
                                        onCheckedChange = { viewModel.setWallpaperFrostedGlass(it) },
                                    )
                                },
                            )
                        }
                        SettingsGroupCard {
                            SettingRow(
                                title = stringResource(R.string.settings_wallpaper_text_contrast),
                                trailing = {
                                    Switch(
                                        checked = wallpaperTextContrast,
                                        onCheckedChange = { viewModel.setWallpaperTextContrast(it) },
                                    )
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = { session.clearWallpaper() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = MaaDesignTokens.Spacing.listHorizontal,
                                    vertical = 8.dp,
                                ),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.82f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.settings_wallpaper_clear),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaaDesignTokens.Spacing.listHorizontal),
                    ) {
                        SettingsGroupCard {
                            SettingRow(
                                title = stringResource(R.string.settings_wallpaper_desc),
                                onClick = { onChangeWallpaper() },
                            )
                        }
                    }
                }
            }

            if (session.isBusy) {
                // Swallow all pointer input so clear/change/preview cannot re-enter while busy.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.settings_wallpaper_loading),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

