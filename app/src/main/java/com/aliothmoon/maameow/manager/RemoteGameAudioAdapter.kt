package com.aliothmoon.maameow.manager

import com.aliothmoon.maameow.domain.service.GameAudioAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

internal object RemoteGameAudioAdapter : GameAudioAdapter {
    override val connected: Flow<Boolean> = RemoteServiceManager.state
        .map { it is RemoteServiceManager.ServiceState.Connected }
        .distinctUntilChanged()

    override suspend fun setMuted(packageName: String, muted: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                RemoteServiceManager.getInstanceOrNull()
                    ?.setPlayAudioOpAllowed(packageName, !muted) == true
            }.onFailure {
                Timber.w(
                    it,
                    "setPlayAudioOpAllowed(%s, muted=%s) IPC failed",
                    packageName,
                    muted,
                )
            }.getOrDefault(false)
        }
}
