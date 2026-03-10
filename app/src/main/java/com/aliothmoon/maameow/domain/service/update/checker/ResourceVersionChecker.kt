package com.aliothmoon.maameow.domain.service.update.checker

import com.aliothmoon.maameow.data.model.update.UpdateCheckResult

interface ResourceVersionChecker {
    suspend fun check(currentVersion: String): UpdateCheckResult
}
