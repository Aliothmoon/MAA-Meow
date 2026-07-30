package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.remote.AppAliveStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

interface AppAliveChecker {
    suspend fun isAppAlive(packageName: String): Int
    // null = 无法判断（宽松放行），false = 确认不在 VD 上
    suspend fun isAppOnBackgroundDisplay(packageName: String): Boolean?

    // null = 无法执行，true = 已把游戏拉回虚拟屏
    suspend fun moveAppToBackgroundDisplay(packageName: String): Boolean? = null
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

    override suspend fun isAppOnBackgroundDisplay(packageName: String): Boolean? =
        withContext(Dispatchers.IO) {
            runCatching {
                RemoteServiceManager.getInstanceOrNull()?.isAppOnVirtualDisplay(packageName)
            }.onFailure {
                Timber.w(it, "isAppOnBackgroundDisplay: IPC failure for %s", packageName)
            }.getOrNull()
        }

    override suspend fun moveAppToBackgroundDisplay(packageName: String): Boolean? =
        withContext(Dispatchers.IO) {
            runCatching {
                RemoteServiceManager.getInstanceOrNull()?.moveAppToVirtualDisplay(packageName)
            }.onFailure {
                Timber.w(it, "moveAppToBackgroundDisplay: IPC failure for %s", packageName)
            }.getOrNull()
        }
}
