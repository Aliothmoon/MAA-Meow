package com.aliothmoon.maameow.presentation.view.settings

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.presentation.components.InstalledAppPickerDialog
import com.aliothmoon.maameow.presentation.components.ListItemDivider
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.utils.i18n.resolve
import com.aliothmoon.maameow.utils.i18n.wakeUpClientTypeDisplayName
import org.koin.androidx.compose.koinViewModel

/** 按客户端从桌面应用里选包名，任务运行中只读 */
@Composable
fun GamePackageSettingsView(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val overrides by viewModel.gamePackages.collectAsStateWithLifecycle()
    val editable by viewModel.coreIdle.collectAsStateWithLifecycle()
    val settingsMessage by viewModel.settingsMessage.collectAsStateWithLifecycle()
    var pickingClient by remember { mutableStateOf<String?>(null) }

    settingsMessage?.let { msg ->
        Toast.makeText(context, msg.resolve(context), Toast.LENGTH_SHORT).show()
        viewModel.clearSettingsMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_game_package_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            contentPadding = PaddingValues(
                horizontal = MaaDesignTokens.Spacing.listHorizontal,
                vertical = MaaDesignTokens.Spacing.sm,
            ),
        ) {
            if (!editable) {
                item {
                    Text(
                        text = stringResource(R.string.settings_game_package_busy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(MaaDesignTokens.Spacing.sm),
                    )
                }
            }
            item {
                SettingsGroupCard {
                    WakeUpConfig.CLIENT_TYPES.forEachIndexed { index, client ->
                        if (index > 0) ListItemDivider()
                        val custom = overrides[client]
                        SettingRow(
                            title = context.wakeUpClientTypeDisplayName(client),
                            description = custom ?: Packages.defaults[client],
                            descriptionColor = if (custom != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            enabled = editable,
                            trailing = if (custom != null) {
                                {
                                    IconButton(enabled = editable, onClick = { viewModel.setGamePackage(client, null) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.RestartAlt,
                                            contentDescription = stringResource(R.string.settings_game_package_reset),
                                        )
                                    }
                                }
                            } else null,
                            onClick = { pickingClient = client },
                        )
                    }
                }
            }
        }
    }

    pickingClient?.let { client ->
        InstalledAppPickerDialog(
            title = stringResource(
                R.string.settings_game_package_picker_title,
                context.wakeUpClientTypeDisplayName(client),
            ),
            icon = Icons.Rounded.SportsEsports,
            onPick = {
                viewModel.setGamePackage(client, it.packageName)
                pickingClient = null
            },
            onDismiss = { pickingClient = null },
        )
    }
}
