package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.remote.AppAliveStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

interface AppAliveChecker {
    suspend fun isAppAlive(packageName: String): Int
}

class RemoteAppAliveChecker : AppAliveChecker {
    override suspend fun isAppAlive(packageName: String): Int = withContext(Dispatchers.IO) {
        try {
            val service = RemoteServiceManager.getInstanceOrNull() ?: return@withContext AppAliveStatus.UNKNOWN
            service.isAppAlive(packageName)
        } catch (e: Exception) {
            Timber.w(e, "AppAliveChecker: isAppAlive call failed for %s", packageName)
            AppAliveStatus.UNKNOWN
        }
    }
}
