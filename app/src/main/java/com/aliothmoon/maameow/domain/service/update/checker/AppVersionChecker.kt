package com.aliothmoon.maameow.domain.service.update.checker

import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateChannel

interface AppVersionChecker {
    suspend fun check(currentVersion: String, channel: UpdateChannel): UpdateCheckResult
}
