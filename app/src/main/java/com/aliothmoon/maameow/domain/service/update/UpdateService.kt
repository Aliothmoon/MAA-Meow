package com.aliothmoon.maameow.domain.service.update

import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateProcessState
import com.aliothmoon.maameow.data.model.update.UpdateSource
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 更新服务统一门面
 * 提供资源更新和 App 更新的统一入口
 * 保持 API 兼容，内部委托给具体 Handler
 */
class UpdateService(
    private val resourceHandler: ResourceUpdateHandler,
    private val appHandler: AppUpdateHandler,
) {
    // ==================== 资源更新 ====================

    val resourceProcessState: StateFlow<UpdateProcessState>
        get() = resourceHandler.processState

    suspend fun checkResourceUpdate(currentVersion: String, cdk: String = ""): UpdateCheckResult {
        return resourceHandler.checkUpdate(currentVersion, cdk)
    }

    suspend fun confirmAndDownloadResource(
        source: UpdateSource,
        cdk: String,
        currentVersion: String,
        dir: File
    ): Result<Unit> {
        return resourceHandler.confirmAndDownload(source, cdk, currentVersion, dir)
    }

    fun resetResourceProcess() {
        resourceHandler.resetState()
    }

    // ==================== App 更新 ====================

    val appProcessState: StateFlow<UpdateProcessState>
        get() = appHandler.processState

    suspend fun checkAppUpdate(cdk: String = ""): UpdateCheckResult {
        return appHandler.checkUpdate(cdk)
    }

    suspend fun confirmAndDownloadApp(source: UpdateSource, cdk: String): Result<Unit> {
        return appHandler.confirmAndDownload(source, cdk)
    }

    fun resetAppProcess() {
        appHandler.resetState()
    }
}
