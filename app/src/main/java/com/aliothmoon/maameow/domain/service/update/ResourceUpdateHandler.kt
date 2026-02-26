package com.aliothmoon.maameow.domain.service.update

import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.datasource.ResourceDownloader
import com.aliothmoon.maameow.data.datasource.ZipExtractor
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateError
import com.aliothmoon.maameow.data.model.update.UpdateProcessState
import com.aliothmoon.maameow.data.model.update.UpdateSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File

class ResourceUpdateHandler(
    private val downloader: ResourceDownloader,
    private val extractor: ZipExtractor,
) {
    private val _processState = MutableStateFlow<UpdateProcessState>(UpdateProcessState.Idle)
    val processState: StateFlow<UpdateProcessState> = _processState.asStateFlow()

    /**
     * 检查资源更新（纯函数，不修改任何状态）
     */
    suspend fun checkUpdate(currentVersion: String, cdk: String = ""): UpdateCheckResult {
        return downloader.checkVersion(currentVersion, cdk)
    }

    /**
     * 确认并下载资源更新
     * 根据下载源解析下载链接，MirrorChyan 需要 CDK 验证
     */
    suspend fun confirmAndDownload(
        source: UpdateSource,
        cdk: String,
        currentVersion: String,
        target: File
    ): Result<Unit> {
        _processState.value = UpdateProcessState.Downloading(0, "准备下载...", 0L, 0L)
        val url = when (source) {
            UpdateSource.MIRROR_CHYAN -> {
                when (val result = downloader.resolveDownloadUrl(currentVersion, cdk)) {
                    is UpdateCheckResult.Available -> {
                        if (result.info.downloadUrl.isBlank()) {
                            _processState.value = UpdateProcessState.Failed(UpdateError.CdkRequired)
                            return Result.failure(Exception("CDK 验证失败"))
                        }
                        result.info.downloadUrl
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

            UpdateSource.GITHUB -> MaaApi.GITHUB_RESOURCE
        }
        return downloadAndInstall(target, url)
    }

    /**
     * 下载并安装资源更新
     */
    private suspend fun downloadAndInstall(target: File, url: String): Result<Unit> {

        val downloadResult = downloader.downloadToTempFile(url) { progress ->
            _processState.value = UpdateProcessState.Downloading(
                progress = progress.progress,
                speed = progress.speed,
                downloaded = progress.downloaded,
                total = progress.total
            )
        }

        val tempFile = downloadResult.getOrElse { e ->
            _processState.value =
                UpdateProcessState.Failed(UpdateError.NetworkError(e.message ?: "下载失败"))
            return Result.failure(e)
        }

        // 2. 解压
        _processState.value = UpdateProcessState.Extracting(0, 0, 0)

        if (!target.exists()) {
            target.mkdirs()
        }

        val extractResult = extractor.extract(
            zipFile = tempFile,
            destDir = target,
            pathFilter = { entryName ->
                // 过滤并映射路径：MaaResource-main/resource/xxx -> xxx
                val name = entryName.removePrefix("MaaResource-main/")
                if (name.startsWith("resource/")) {
                    val rf = name.removePrefix("resource/")
                    rf.ifEmpty { null }
                } else {
                    null
                }
            },
            onProgress = { progress ->
                _processState.value = UpdateProcessState.Extracting(
                    progress = progress.progress,
                    current = progress.current,
                    total = progress.total
                )
            }
        )

        // 清理临时文件
        tempFile.delete()

        return extractResult.fold(
            onSuccess = {
                _processState.value = UpdateProcessState.Success
                Timber.i("资源更新完成")
                Result.success(Unit)
            },
            onFailure = { e ->
                _processState.value =
                    UpdateProcessState.Failed(UpdateError.UnknownError("解压失败"))
                Result.failure(e)
            }
        )
    }

    fun resetState() {
        _processState.value = UpdateProcessState.Idle
    }
}
