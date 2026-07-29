package com.aliothmoon.maameow.presentation.view.panel.userdata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.UserDataUpdateConfig
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.domain.models.UserDataUpdateTriggerInterval
import com.aliothmoon.maameow.presentation.components.CheckBoxWithLabel
import com.aliothmoon.maameow.presentation.components.SelectableChipGroup
import com.aliothmoon.maameow.utils.i18n.formatToolboxSyncTime
import org.koin.compose.koinInject

@Composable
fun UserDataUpdateConfigPanel(
    config: UserDataUpdateConfig,
    onConfigChange: (UserDataUpdateConfig) -> Unit,
    modifier: Modifier = Modifier,
    depotRepository: DepotRepository = koinInject(),
    operBoxRepository: OperBoxRepository = koinInject(),
) {
    val depotSnap by depotRepository.snapshot.collectAsStateWithLifecycle()
    val operSnap by operBoxRepository.snapshot.collectAsStateWithLifecycle()

    val neverSynced = stringResource(R.string.panel_toolbox_never_synced)
    val operSyncText = if (operSnap.hasSynced) {
        stringResource(
            R.string.panel_toolbox_last_sync,
            formatToolboxSyncTime(operSnap.syncTimeMillis),
        )
    } else {
        neverSynced
    }
    val depotSyncText = if (depotSnap.syncTimeMillis > 0L) {
        stringResource(
            R.string.panel_toolbox_last_sync,
            formatToolboxSyncTime(depotSnap.syncTimeMillis),
        )
    } else {
        neverSynced
    }

    val intervalOptions = listOf(
        UserDataUpdateTriggerInterval.EVERY_TIME to stringResource(R.string.panel_userdata_interval_every_time),
        UserDataUpdateTriggerInterval.DAILY to stringResource(R.string.panel_userdata_interval_daily),
        UserDataUpdateTriggerInterval.WEEKLY to stringResource(R.string.panel_userdata_interval_weekly),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 12.dp, vertical = 8.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckBoxWithLabel(
            checked = config.updateOperBox,
            onCheckedChange = { onConfigChange(config.copy(updateOperBox = it)) },
            label = stringResource(R.string.panel_userdata_update_oper_box),
            subtitle = operSyncText,
            modifier = Modifier.fillMaxWidth(),
        )

        CheckBoxWithLabel(
            checked = config.updateDepot,
            onCheckedChange = { onConfigChange(config.copy(updateDepot = it)) },
            label = stringResource(R.string.panel_userdata_update_depot),
            subtitle = depotSyncText,
            modifier = Modifier.fillMaxWidth(),
        )

        SelectableChipGroup(
            label = stringResource(R.string.panel_userdata_trigger_interval),
            selectedValue = config.triggerInterval,
            options = intervalOptions,
            onSelected = { onConfigChange(config.copy(triggerInterval = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        ProfileBoundTip(
            text = stringResource(R.string.panel_userdata_profile_tip),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileBoundTip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
