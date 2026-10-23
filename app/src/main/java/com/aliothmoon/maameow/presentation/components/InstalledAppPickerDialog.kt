package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.utils.LauncherApps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 桌面可见应用选择，先弹窗再异步查列表，免得点了没反应 */
@Composable
fun InstalledAppPickerDialog(
    title: String,
    onPick: (LauncherApps.App) -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector = Icons.Rounded.Build,
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var options by remember { mutableStateOf<List<LauncherApps.App>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        options = try {
            withContext(Dispatchers.IO) { LauncherApps.load(context.applicationContext) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadFailed = true
            emptyList()
        }
    }

    val searchText = search.trim()
    val filteredOptions = options
        ?.filter { option ->
            searchText.isBlank() ||
                    option.label.contains(searchText, ignoreCase = true) ||
                    option.packageName.contains(searchText, ignoreCase = true)
        }
        .orEmpty()

    AdaptiveTaskPromptDialog(
        visible = true,
        title = title,
        icon = icon,
        confirmText = stringResource(R.string.common_close),
        dismissText = "",
        onConfirm = onDismiss,
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    options == null -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.settings_shizuku_launch_app_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    loadFailed -> {
                        Text(
                            text = stringResource(R.string.settings_shizuku_launch_app_picker_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> {
                        ITextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = stringResource(R.string.settings_shizuku_launch_app_search_hint),
                            singleLine = true
                        )

                        if (filteredOptions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_shizuku_launch_app_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredOptions, key = { it.packageName }) { option ->
                                    AppListItem(
                                        label = option.label,
                                        packageName = option.packageName,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onPick(option) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
