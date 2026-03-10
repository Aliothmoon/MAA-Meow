package com.aliothmoon.maameow.domain.service.update.resolver

import com.aliothmoon.maameow.data.model.update.UpdateChannel

interface AppDownloadUrlResolver {
    suspend fun resolve(version: String, channel: UpdateChannel): Result<String>
}
