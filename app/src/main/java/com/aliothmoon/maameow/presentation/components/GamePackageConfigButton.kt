package com.aliothmoon.maameow.presentation.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import org.koin.compose.koinInject
import timber.log.Timber
import java.io.File

@Composable
fun GamePackageConfigButton(
    pathConfig: MaaPathConfig = koinInject(),
    compositionService: MaaCompositionService = koinInject(),
) {
    val context = LocalContext.current
    val state by compositionService.state.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            enabled = state == MaaExecutionState.IDLE,
            onClick = {
                val file = File(pathConfig.resourceDir, "config.json")
                if (!file.isFile) {
                    Toast.makeText(context, R.string.game_package_config_missing, Toast.LENGTH_LONG).show()
                } else {
                    try {
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_EDIT).apply {
                            setDataAndType(uri, "text/plain")
                            clipData = ClipData.newRawUri(file.name, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.game_package_no_editor, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Timber.w(e, "Open game config in external editor failed")
                        Toast.makeText(context, R.string.game_package_open_failed, Toast.LENGTH_LONG).show()
                    }
                }
            },
        ) {
            Text(stringResource(R.string.game_package_edit))
        }
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 1.dp).size(16.dp),
            )
            Text(
                text = stringResource(R.string.game_package_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
