package com.aliothmoon.maameow.domain.service.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.data.api.CdkRequiredException
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.MirrorChyanApiClient
import com.aliothmoon.maameow.data.api.MirrorChyanBizException
import com.aliothmoon.maameow.data.datasource.AppDownloader
import com.aliothmoon.maameow.data.datasource.ResourceDownloader
import com.aliothmoon.maameow.data.datasource.ZipExtractor
import com.aliothmoon.maameow.data.datasource.update.GitHubAppDownloadUrlResolver
import com.aliothmoon.maameow.data.datasource.update.GitHubResourceDownloadUrlResolver
import com.aliothmoon.maameow.data.datasource.update.MirrorChyanAppDownloadUrlResolver
import com.aliothmoon.maameow.data.datasource.update.MirrorChyanResourceDownloadUrlResolver
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateError
import com.aliothmoon.maameow.data.model.update.UpdateProcessState
import com.aliothmoon.maameow.data.model.update.UpdateSource
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.service.update.checker.AppVersionChecker
import com.aliothmoon.maameow.domain.service.update.checker.ResourceVersionChecker
import com.aliothmoon.maameow.domain.service.update.resolver.AppDownloadUrlResolver
import com.aliothmoon.maameow.domain.service.update.resolver.ResourceDownloadUrlResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File

/**
 * 更新服务 — 直接编排版本检查、下载链接解析、下载安装全流程
 */
class UpdateService(
    private val context: Context,
    private val appVersionChecker: AppVersionChecker,
    private val resourceVersionChecker: ResourceVersionChecker,
    private val apiClient: MirrorChyanApiClient,
    private val appSettingsManager: AppSettingsManager,
    private val httpClient: HttpClientHelper,
    private val appDownloader: AppDownloader,
    private val resourceDownloader: ResourceDownloader,
    private val extractor: ZipExtractor,
) {
    private val appDownloadResolvers: Map<UpdateSource, AppDownloadUrlResolver> = mapOf(
        UpdateSource.MIRROR_CHYAN to MirrorChyanAppDownloadUrlResolver(
            apiClient,
            appSettingsManager
        ),
        UpdateSource.GITHUB to GitHubAppDownloadUrlResolver(httpClient)
    )

    private val resourceDownloadResolvers: Map<UpdateSource, ResourceDownloadUrlResolver> = mapOf(
        UpdateSource.MIRROR_CHYAN to MirrorChyanResourceDownloadUrlResolver(
            apiClient,
            appSettingsManager
        ),
        UpdateSource.GITHUB to GitHubResourceDownloadUrlResolver()
    )

    // ==================== App 更新 ====================

    private val _appProcessState = MutableStateFlow<UpdateProcessState>(UpdateProcessState.Idle)
    val appProcessState: StateFlow<UpdateProcessState> = _appProcessState.asStateFlow()

    suspend fun checkAppUpdate(channel: UpdateChannel = UpdateChannel.STABLE): UpdateCheckResult {
        return appVersionChecker.check(BuildConfig.VERSION_NAME, channel)
    }

    suspend fun downloadApp(
        source: UpdateSource,
        version: String,
        channel: UpdateChannel = UpdateChannel.STABLE
    ): Result<Unit> {
        Timber.i("downloadApp start: source=%s, version=%s, channel=%s", source, version, channel)
        _appProcessState.value = UpdateProcessState.Downloading(0, "准备下载...", 0L, 0L)

        val resolver = appDownloadResolvers[source]
            ?: return failApp(UpdateError.UnknownError("不支持的下载源: $source"))

        val url = resolver.resolve(version, channel).getOrElse { e ->
            _appProcessState.value = UpdateProcessState.Failed(mapToUpdateError(e))
            return Result.failure(e)
        }
        Timber.i("downloadApp resolved URL: host=%s", safeHost(url))
        return downloadAndInstallApp(url, version)
    }

    fun resetAppProcess() {
        _appProcessState.value = UpdateProcessState.Idle
    }

    private suspend fun downloadAndInstallApp(url: String, version: String): Result<Unit> {
        val cached = appDownloader.getCachedApk(version)
        if (cached != null) {
            Timber.i("APK already cached: ${cached.name}, skipping download")
            return doInstallApp(cached)
        }

        appDownloader.cleanOldApks(version)

        val downloadResult = appDownloader.downloadToTempFile(url, version) { progress ->
            _appProcessState.value = UpdateProcessState.Downloading(
                progress = progress.progress,
                speed = progress.speed,
                downloaded = progress.downloaded,
                total = progress.total
            )
        }

        val apkFile = downloadResult.getOrElse { e ->
            _appProcessState.value =
                UpdateProcessState.Failed(UpdateError.NetworkError(e.message ?: "下载失败"))
            return Result.failure(e)
        }

        return doInstallApp(apkFile)
    }

    private fun doInstallApp(apkFile: File): Result<Unit> {
        _appProcessState.value = UpdateProcessState.Installing
        return try {
            installApk(apkFile)
            _appProcessState.value = UpdateProcessState.Success
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "安装 APK 失败")
            _appProcessState.value =
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

    private fun failApp(error: UpdateError): Result<Unit> {
        _appProcessState.value = UpdateProcessState.Failed(error)
        return Result.failure(Exception(error.message))
    }

    // ==================== 资源更新 ====================

    private val _resourceProcessState =
        MutableStateFlow<UpdateProcessState>(UpdateProcessState.Idle)
    val resourceProcessState: StateFlow<UpdateProcessState> = _resourceProcessState.asStateFlow()

    suspend fun checkResourceUpdate(currentVersion: String): UpdateCheckResult {
        return resourceVersionChecker.check(currentVersion)
    }

    suspend fun downloadResource(
        source: UpdateSource,
        currentVersion: String,
        target: File
    ): Result<Unit> {
        Timber.i("downloadResource start: source=%s", source)
        _resourceProcessState.value = UpdateProcessState.Downloading(0, "准备下载...", 0L, 0L)

        val resolver = resourceDownloadResolvers[source]
            ?: return failResource(UpdateError.UnknownError("不支持的下载源: $source"))

        val url = resolver.resolve(currentVersion).getOrElse { e ->
            _resourceProcessState.value = UpdateProcessState.Failed(mapToUpdateError(e))
            return Result.failure(e)
        }
        Timber.i("downloadResource resolved URL: host=%s", safeHost(url))
        return downloadAndExtractResource(target, url)
    }

    fun resetResourceProcess() {
        _resourceProcessState.value = UpdateProcessState.Idle
    }

    private suspend fun downloadAndExtractResource(target: File, url: String): Result<Unit> {
        val downloadResult = resourceDownloader.downloadToTempFile(url) { progress ->
            _resourceProcessState.value = UpdateProcessState.Downloading(
                progress = progress.progress,
                speed = progress.speed,
                downloaded = progress.downloaded,
                total = progress.total
            )
        }

        val tempFile = downloadResult.getOrElse { e ->
            _resourceProcessState.value =
                UpdateProcessState.Failed(UpdateError.NetworkError(e.message ?: "下载失败"))
            return Result.failure(e)
        }

        _resourceProcessState.value = UpdateProcessState.Extracting(0, 0, 0)

        target.mkdirs()

        val extractResult = extractor.extract(
            zipFile = tempFile,
            destDir = target,
            pathFilter = { entryName ->
                val name = entryName.removePrefix("MaaResource-main/")
                if (name.startsWith("resource/")) {
                    val rf = name.removePrefix("resource/")
                    rf.ifEmpty { null }
                } else {
                    null
                }
            },
            onProgress = { progress ->
                _resourceProcessState.value = UpdateProcessState.Extracting(
                    progress = progress.progress,
                    current = progress.current,
                    total = progress.total
                )
            }
        )

        tempFile.delete()

        return extractResult.fold(
            onSuccess = {
                _resourceProcessState.value = UpdateProcessState.Success
                Timber.i("资源更新完成")
                Result.success(Unit)
            },
            onFailure = { e ->
                _resourceProcessState.value =
                    UpdateProcessState.Failed(UpdateError.UnknownError("解压失败"))
                Result.failure(e)
            }
        )
    }

    private fun failResource(error: UpdateError): Result<Unit> {
        _resourceProcessState.value = UpdateProcessState.Failed(error)
        return Result.failure(Exception(error.message))
    }

    // ==================== 工具方法 ====================

    private fun mapToUpdateError(e: Throwable): UpdateError = when (e) {
        is CdkRequiredException -> UpdateError.CdkRequired
        is MirrorChyanBizException -> e.toUpdateError()
        else -> UpdateError.NetworkError(e.message ?: "未知错误")
    }

    private fun safeHost(url: String): String {
        if (url.isBlank()) return "<blank>"
        return runCatching { Uri.parse(url).host ?: "<no-host>" }.getOrDefault("<invalid>")
    }
}
