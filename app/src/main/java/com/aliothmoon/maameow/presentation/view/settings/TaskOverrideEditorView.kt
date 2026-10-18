package com.aliothmoon.maameow.presentation.view.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.LocalToaster
import com.aliothmoon.maameow.presentation.components.JsonCodeEditor
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.TaskOverrideEditorViewModel
import com.aliothmoon.maameow.utils.i18n.resolve
import com.dokar.sonner.ToastType
import org.koin.androidx.compose.koinViewModel

@Composable
fun TaskOverrideEditorView(
    navController: NavController,
    viewModel: TaskOverrideEditorViewModel = koinViewModel(),
) {
    val loadedText by viewModel.loadedText.collectAsStateWithLifecycle()
    val isJsonValid by viewModel.isJsonValid.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val toaster = LocalToaster.current
    LaunchedEffect(Unit) {
        viewModel.load()
    }
    val msg = stringResource(R.string.editor_save_success)
    LaunchedEffect(saveState) {
        when (val s = saveState) {
            is TaskOverrideEditorViewModel.SaveState.Success -> {
                toaster.show(msg, type = ToastType.Success)
                viewModel.clearSaveState()
            }

            is TaskOverrideEditorViewModel.SaveState.Error -> {
                toaster.show(s.text.resolve(context), type = ToastType.Error)
                viewModel.clearSaveState()
            }

            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_tasks_override_edit_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() },
                actions = {
                    IconButton(
                        onClick = { viewModel.onSave() },
                        enabled = isJsonValid &&
                                saveState !is TaskOverrideEditorViewModel.SaveState.Saving
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
        JsonCodeEditor(
            loadedText = loadedText,
            isJsonValid = isJsonValid,
            onTextChange = viewModel::onTextChange,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        )
    }
}
