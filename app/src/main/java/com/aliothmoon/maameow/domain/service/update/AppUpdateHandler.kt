package com.aliothmoon.maameow.domain.service.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.aliothmoon.maameow.data.datasource.AppDownloader
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateError
import com.aliothmoon.maameow.data.model.update.UpdateInfo
import com.aliothmoon.maameow.data.model.update.UpdateProcessState
import com.aliothmoon.maameow.data.model.update.UpdateSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File

class AppUpdateHandler(
    private val context: Context,
    private val downloader: AppDownloader,
) {
    private val _processState = MutableStateFlow<UpdateProcessState>(UpdateProcessState.Idle)
    val processState: StateFlow<UpdateProcessState> = _processState.asStateFlow()


    suspend fun checkUpdate(cdk: String = ""): UpdateCheckResult {
        return downloader.checkVersionFromMirrorChyan(cdk)
    }

    /**
     * 确认并下载 App 更新
     * 根据下载源解析下载链接，MirrorChyan 需要 CDK 验证
     */
    suspend fun confirmAndDownload(source: UpdateSource, cdk: String): Result<Unit> {
        _processState.value = UpdateProcessState.Downloading(0, "准备下载...", 0L, 0L)
        val info: UpdateInfo = when (source) {
            UpdateSource.MIRROR_CHYAN -> {
                when (val result = downloader.checkVersionFromMirrorChyan(cdk)) {
                    is UpdateCheckResult.Available -> {
                        if (result.info.downloadUrl.isBlank()) {
                            _processState.value = UpdateProcessState.Failed(UpdateError.CdkRequired)
                            return Result.failure(Exception("CDK 验证失败"))
                        }
                        result.info
                    }

                    is UpdateCheckResult.Error -> {
                        _processState.value = UpdateProcessState.Failed(result.error)
                        return Result.failure(Exception(result.error.message))
                    }

                    is UpdateCheckResult.UpToDate -> {
                        return Result.failure(Exception("已是最新版本"))
                    }
                }
            }

            UpdateSource.GITHUB -> {
                when (val result = downloader.checkVersionFromGitHub()) {
                    is UpdateCheckResult.Available -> result.info
                    is UpdateCheckResult.Error -> {
                        _processState.value = UpdateProcessState.Failed(result.error)
                        return Result.failure(Exception(result.error.message))
                    }

                    is UpdateCheckResult.UpToDate -> {
                        return Result.failure(Exception("已是最新版本"))
                    }
                }
            }
        }
        return downloadAndInstall(info.downloadUrl, info.version)
    }

    /**
     * 下载并安装 APK，优先使用已缓存的完整文件
     */
    private suspend fun downloadAndInstall(url: String, version: String): Result<Unit> {
        val cached = downloader.getCachedApk(version)
        if (cached != null) {
            Timber.i("APK already cached: ${cached.name}, skipping download")
            return doInstall(cached)
        }

        downloader.cleanOldApks(version)


        val downloadResult = downloader.downloadToTempFile(url, version) { progress ->
            _processState.value = UpdateProcessState.Downloading(
                progress = progress.progress,
                speed = progress.speed,
                downloaded = progress.downloaded,
                total = progress.total
            )
        }

        val apkFile = downloadResult.getOrElse { e ->
            _processState.value =
                UpdateProcessState.Failed(UpdateError.NetworkError(e.message ?: "下载失败"))
            return Result.failure(e)
        }

        return doInstall(apkFile)
    }

    private fun doInstall(apkFile: File): Result<Unit> {
        _processState.value = UpdateProcessState.Installing
        return try {
            installApk(apkFile)
            _processState.value = UpdateProcessState.Success
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "安装 APK 失败")
            _processState.value =
                UpdateProcessState.Failed(UpdateError.UnknownError("安装失败: ${e.message}"))
            Result.failure(e)
        }
    }

    private fun installApk(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun resetState() {
        _processState.value = UpdateProcessState.Idle
    }
}
