package com.aliothmoon.maameow.presentation.view.settings

import android.content.ClipData
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.LocalToaster
import com.aliothmoon.maameow.presentation.components.SectionHeader
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.web.LanAddress
import com.aliothmoon.maameow.web.LanAddresses
import com.aliothmoon.maameow.web.WebUiController
import com.aliothmoon.maameow.web.WebUiSettingsManager
import com.aliothmoon.maameow.web.auth.WebSession
import com.aliothmoon.maameow.web.auth.WebUiAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun WebUiSettingsView(navController: NavController) {
    val controller: WebUiController = koinInject()
    val settings: WebUiSettingsManager = koinInject()
    val enabled by settings.enabled.collectAsStateWithLifecycle()
    val port by settings.port.collectAsStateWithLifecycle()
    val state by controller.state.collectAsStateWithLifecycle()
    val pairing by controller.auth.pairingCode.collectAsStateWithLifecycle()
    val sessions by controller.auth.sessions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val contentColor = MaterialTheme.colorScheme.onSurface
    var editingPort by remember { mutableStateOf(false) }

    val running = state as? WebUiController.ServerState.Running
    val statusText = when (val s = state) {
        is WebUiController.ServerState.Running -> stringResource(R.string.webui_status_running, s.port)
        is WebUiController.ServerState.Failed -> stringResource(R.string.webui_status_failed, s.reason)
        WebUiController.ServerState.Stopped -> stringResource(R.string.webui_status_stopped)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_webui_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() })
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            contentPadding = PaddingValues(
                horizontal = MaaDesignTokens.Spacing.listHorizontal,
                vertical = MaaDesignTokens.Spacing.sm
            )
        ) {
            item {
                SettingsGroupCard {
                    SettingSwitchItem(
                        title = stringResource(R.string.webui_enable),
                        description = statusText,
                        contentColor = contentColor,
                        checked = enabled,
                        onCheckedChange = controller::setEnabled,
                    )
                    SettingRow(
                        title = stringResource(R.string.webui_port_title),
                        description = port.toString(),
                        titleColor = contentColor,
                        onClick = { editingPort = true },
                    )
                }
            }

            if (running != null) {
                item {
                    Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                    SectionHeader(stringResource(R.string.webui_address_title))
                    AddressCard(running.port)
                }
                item {
                    Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                    SectionHeader(stringResource(R.string.webui_pair_title))
                    PairingCard(
                        pairing = pairing,
                        onGenerate = { scope.launch { controller.auth.newPairingCode() } },
                        onCancel = { scope.launch { controller.auth.cancelPairing() } },
                    )
                }
            }

            item {
                Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                SectionHeader(stringResource(R.string.webui_sessions_title))
                SessionsCard(
                    sessions = sessions,
                    onRemove = { scope.launch { controller.auth.revoke(it.hash) } },
                    onRemoveAll = { scope.launch { controller.auth.revokeAll() } },
                )
            }

            item {
                Text(
                    text = stringResource(R.string.webui_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(
                        start = MaaDesignTokens.Spacing.lg,
                        top = MaaDesignTokens.Spacing.md,
                        end = MaaDesignTokens.Spacing.lg,
                        bottom = MaaDesignTokens.Spacing.lg,
                    )
                )
            }
        }
    }

    if (editingPort) {
        PortDialog(
            current = port,
            onDismiss = { editingPort = false },
            onConfirm = {
                editingPort = false
                scope.launch { settings.setPort(it) }
            },
        )
    }
}

@Composable
private fun AddressCard(port: Int) {
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val copied = stringResource(R.string.webui_address_copied)
    val scope = rememberCoroutineScope()
    // 网络可能已切换，每次进页面重新取
    val addresses by produceState(emptyList<LanAddress>(), port) {
        value = withContext(Dispatchers.IO) { LanAddresses.ipv4() }
    }

    SettingsGroupCard {
        if (addresses.isEmpty()) {
            SettingRow(title = stringResource(R.string.webui_address_empty))
        }
        addresses.forEach { address ->
            val url = LanAddresses.url(address.host, port)
            SettingRow(
                title = url,
                description = address.interfaceName,
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipData.newPlainText("url", url).toClipEntry())
                        toaster.show(copied)
                    }
                },
            )
        }
    }
}

@Composable
private fun PairingCard(
    pairing: WebUiAuth.PairingCode?,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
) {
    SettingsGroupCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaaDesignTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.webui_pair_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pairing == null) {
                FilledTonalButton(onClick = onGenerate) {
                    Text(stringResource(R.string.webui_pair_generate))
                }
            } else {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(pairing) {
                    while (true) {
                        now = System.currentTimeMillis()
                        if (now >= pairing.expiresAt) {
                            onCancel()
                            break
                        }
                        delay(1_000)
                    }
                }
                val remaining = ((pairing.expiresAt - now) / 1_000).coerceAtLeast(0).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // 三位一组好念
                        text = pairing.code.chunked(3).joinToString(" "),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.webui_pair_cancel))
                    }
                }
                Text(
                    text = stringResource(R.string.webui_pair_expires, remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionsCard(
    sessions: List<WebSession>,
    onRemove: (WebSession) -> Unit,
    onRemoveAll: () -> Unit,
) {
    val context = LocalContext.current
    fun format(time: Long) = DateUtils.formatDateTime(
        context, time,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )

    SettingsGroupCard {
        if (sessions.isEmpty()) {
            SettingRow(title = stringResource(R.string.webui_sessions_empty))
            return@SettingsGroupCard
        }
        sessions.sortedByDescending { it.lastSeenAt }.forEach { session ->
            SettingRow(
                title = session.name,
                description = stringResource(
                    R.string.webui_session_meta,
                    format(session.createdAt),
                    format(session.lastSeenAt),
                ),
                trailing = {
                    TextButton(onClick = { onRemove(session) }) {
                        Text(stringResource(R.string.webui_session_remove))
                    }
                },
            )
        }
        if (sessions.size > 1) {
            TextButton(onClick = onRemoveAll) {
                Text(stringResource(R.string.webui_sessions_remove_all))
            }
        }
    }
}

@Composable
private fun PortDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in WebUiSettingsManager.PORT_RANGE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.webui_port_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter(Char::isDigit).take(5) },
                singleLine = true,
                isError = !valid,
                supportingText = {
                    Text(
                        stringResource(
                            R.string.webui_port_invalid,
                            WebUiSettingsManager.PORT_RANGE.first,
                            WebUiSettingsManager.PORT_RANGE.last,
                        )
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { value?.let(onConfirm) }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
