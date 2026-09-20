package com.aliothmoon.maameow.presentation

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.PallasDrunkState
import com.aliothmoon.maameow.data.achievement.PallasPrompt
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import kotlinx.coroutines.launch

private val LocalSoberResources = compositionLocalOf<Resources?> { null }


@Composable
fun StaySober(content: @Composable () -> Unit) {
    val sober = LocalSoberResources.current
    if (sober == null) {
        content()
    } else {
        CompositionLocalProvider(LocalResources provides sober, content = content)
    }
}


@Composable
fun PallasDrunkHost(
    state: PallasDrunkState,
    content: @Composable () -> Unit,
) {
    val isDrunk by state.isDrunk.collectAsStateWithLifecycle()
    val prompt by state.prompt.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { state.restorePendingHangover() }

    val sober = LocalResources.current
    val configuration = LocalConfiguration.current
    val drunk = remember(sober, configuration, isDrunk) {
        if (isDrunk) DrunkResources(sober) else null
    }

    CompositionLocalProvider(
        LocalResources provides (drunk ?: sober),
        LocalSoberResources provides sober,
    ) {
        content()

        prompt?.let {
            AdaptiveTaskPromptDialog(
                visible = true,
                title = stringResource(R.string.settings_pallas_burping),
                message = stringResource(
                    when (it) {
                        PallasPrompt.DRUNK -> R.string.settings_pallas_drunk_hint
                        PallasPrompt.HANGOVER -> R.string.settings_pallas_hangover
                    }
                ),
                onDismissRequest = { scope.launch { state.confirmPrompt() } },
                onConfirm = { scope.launch { state.confirmPrompt() } },
                dismissText = "",
                dismissOnOutsideClick = it != PallasPrompt.DRUNK,
            )
        }
    }
}
