package com.aliothmoon.maameow.domain.service

import kotlinx.coroutines.flow.Flow

internal interface GameAudioAdapter {
    val connected: Flow<Boolean>

    suspend fun setMuted(packageName: String, muted: Boolean): Boolean
}
