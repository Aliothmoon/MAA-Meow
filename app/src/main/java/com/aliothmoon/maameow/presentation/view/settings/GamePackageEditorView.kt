package com.aliothmoon.maameow.presentation.view.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.LocalToaster
import com.aliothmoon.maameow.presentation.components.JsonCodeEditor
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.GamePackageEditorViewModel
import com.aliothmoon.maameow.utils.i18n.resolve
import com.dokar.sonner.ToastType
import org.koin.androidx.compose.koinViewModel

@Composable
fun GamePackageEditorView(
    navController: NavController,
    viewModel: GamePackageEditorViewModel = koinViewModel(),
) {
    val loadedText by viewModel.loadedText.collectAsStateWithLifecycle()
    val isJsonValid by viewModel.isJsonValid.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canEdit by viewModel.canEdit.collectAsStateWithLifecycle()
    val defaultPackage by viewModel.defaultPackage.collectAsStateWithLifecycle()
    var showReset by remember { mutableStateOf(false) }

    val toaster = LocalToaster.current
    LaunchedEffect(Unit) { viewModel.load() }
    val msg = stringResource(R.string.game_package_saved)
    LaunchedEffect(saveState) {
        when (val s = saveState) {
            is GamePackageEditorViewModel.SaveState.Success -> {
                toaster.show(msg, type = ToastType.Success)
                viewModel.clearSaveState()
            }

            is GamePackageEditorViewModel.SaveState.Error -> {
                toaster.show(s.text.resolve(context), type = ToastType.Error)
                viewModel.clearSaveState()
            }

            else -> Unit
        }
    }

    AdaptiveTaskPromptDialog(
        visible = showReset,
        title = stringResource(R.string.game_package_reset),
        message = stringResource(R.string.game_package_reset_message),
        confirmText = stringResource(R.string.common_confirm),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = { showReset = false; viewModel.onReset() },
        onDismissRequest = { showReset = false },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.game_package_edit),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() },
                actions = {
                    IconButton(
                        onClick = { showReset = true },
                        enabled = canEdit && loadedText != null && saveState !is GamePackageEditorViewModel.SaveState.Saving,
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.game_package_reset))
                    }
                    IconButton(
                        onClick = { viewModel.onSave() },
                        enabled = canEdit && loadedText != null && isJsonValid &&
                                saveState !is GamePackageEditorViewModel.SaveState.Saving
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.common_save),
                            tint = if (isJsonValid) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.game_package_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.game_package_file_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.game_package_default, defaultPackage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!canEdit) Text(
                stringResource(R.string.resource_editor_busy),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            JsonCodeEditor(
                loadedText = loadedText,
                isJsonValid = isJsonValid,
                onTextChange = viewModel::onTextChange,
                enabled = canEdit && loadedText != null && saveState !is GamePackageEditorViewModel.SaveState.Saving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
