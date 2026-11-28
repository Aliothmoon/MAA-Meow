package com.aliothmoon.maameow.domain.service

import android.content.Context
import android.os.Environment
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.MaaFiles.ASSET_DIR_NAME
import com.aliothmoon.maameow.constant.MaaFiles.OVERRIDES_ASSET_TASKS
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.datasource.AssetExtractor
import com.aliothmoon.maameow.data.datasource.ResourceInitDiagnostics
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import com.aliothmoon.maameow.utils.i18n.uiTextDynamicOr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ResourceInitService(
    private val context: Context,
    private val assetExtractor: AssetExtractor,
    private val pathConfig: MaaPathConfig,
    private val coreFiles: Lazy<CoreFilesGate>,
    private val diagnostics: ResourceInitDiagnostics? = null,
    private val telemetry: RunTelemetry? = null,
) {
    private val _state = MutableStateFlow<ResourceInitState>(ResourceInitState.NotChecked)
    val state: StateFlow<ResourceInitState> = _state.asStateFlow()

    // App 侧资源目录内容版本，重解或资源更新后自增
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** 资源更新包已解到 App 侧目录 */
    fun onResourceUpdated() {
        _revision.update { it + 1 }
    }

    suspend fun checkAndInit() {
        val cur = _state.value
        if (cur !is ResourceInitState.NotChecked && cur !is ResourceInitState.Failed) {
            return
        }
        if (!_state.compareAndSet(cur, ResourceInitState.Checking)) {
            return
        }

        try {
            if (withContext(Dispatchers.IO) { pathConfig.isResourceReady }) {
                _state.value = ResourceInitState.Ready
                return
            }
            doExtractFromAssets(trigger = "checkAndInit")
        } finally {
            // 调用方被取消时还停在 Checking，后续调用都会早退，放回去让下次重查
            _state.compareAndSet(ResourceInitState.Checking, ResourceInitState.NotChecked)
        }
    }

    /** 会删掉整个资源目录 */
    suspend fun reInitialize() {
        coreFiles.value.whenIdle { doExtractFromAssets(trigger = "reInitialize") }
    }

    private suspend fun doExtractFromAssets(trigger: String) {
        _state.value =
            ResourceInitState.Extracting(0, 0, context.getString(R.string.resource_init_preparing))

        var run: ResourceInitDiagnostics.RunSession? = null
        val lastExtracted = AtomicInteger(0)
        val lastTotal = AtomicInteger(0)

        try {
            val result = try {
                withContext(Dispatchers.IO) {
                    val session = diagnostics?.beginRun(trigger, pathConfig)
                    run = session
                    var ensureError: Throwable? = null
                    val ensureOk = try {
                        pathConfig.ensureDirectories()
                    } catch (e: Exception) {
                        ensureError = e
                        false
                    }
                    // 先撤版本标记，防中途打断后半份资源被判就绪
                    pathConfig.clearAppVersion()
                    val resourceDir = File(pathConfig.resourceDir)
                    val existedBefore = resourceDir.exists()
                    val deleteStart = System.currentTimeMillis()
                    val deleteOk = if (existedBefore) resourceDir.deleteRecursively() else true
                    val deleteMs = System.currentTimeMillis() - deleteStart
                    val leftovers = if (!deleteOk || resourceDir.exists()) {
                        session?.collectLeftovers(resourceDir).orEmpty()
                    } else {
                        emptyList()
                    }
                    if (!deleteOk) {
                        Timber.w(
                            "清理旧资源目录失败: %s (leftovers=%d, sample=%s)",
                            resourceDir.absolutePath,
                            leftovers.size,
                            leftovers.take(5).joinToString(" ; "),
                        )
                    }
                    val mkdirsOk = resourceDir.mkdirs() || resourceDir.isDirectory
                    session?.recordPreClean(
                        ensureOk = ensureOk,
                        ensureError = ensureError,
                        existedBefore = existedBefore,
                        deleteOk = deleteOk,
                        deleteMs = deleteMs,
                        leftovers = leftovers,
                        mkdirsOk = mkdirsOk,
                        resourceDir = resourceDir,
                    )
                }

                assetExtractor.extract(
                    assetDir = ASSET_DIR_NAME,
                    destDir = File(pathConfig.resourceDir),
                    onProgress = { progress ->
                        lastExtracted.set(progress.extractedCount)
                        lastTotal.set(progress.totalCount)
                        _state.value = ResourceInitState.Extracting(
                            extractedCount = progress.extractedCount,
                            totalCount = progress.totalCount,
                            currentFile = progress.currentFile
                        )
                    }
                )
            } finally {
                // 早于发布 Ready/Failed；checkAndInit 不走门禁，解压中的加载靠这次递增重试
                _revision.update { it + 1 }
            }

            result.fold(
                onSuccess = {
                    withContext(Dispatchers.IO) {
                        pathConfig.markAppVersion()
                        doForceSyncOverridesTemplate()
                        run?.finishSuccess()
                    }
                    Timber.i("资源初始化完成")
                    _state.value = ResourceInitState.Ready
                },
                onFailure = { e ->
                    val report = withContext(Dispatchers.IO) {
                        run?.finishFailure(e, lastExtracted.get(), lastTotal.get())
                            ?: fallbackFailureReport(trigger, e, lastExtracted.get(), lastTotal.get())
                    }
                    telemetry?.onResourceInitFailed(report)
                    _state.value = ResourceInitState.Failed(
                        (e as? LocalizedException)?.uiText
                            ?: uiTextDynamicOr(e.message, R.string.resource_init_error_copy_failed)
                    )
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "资源初始化失败")
            val report = withContext(Dispatchers.IO) {
                run?.finishFailure(e, lastExtracted.get(), lastTotal.get())
                    ?: fallbackFailureReport(trigger, e, lastExtracted.get(), lastTotal.get())
            }
            telemetry?.onResourceInitFailed(report)
            _state.value = ResourceInitState.Failed(
                (e as? LocalizedException)?.uiText
                    ?: uiTextDynamicOr(e.message, R.string.resource_init_error_unknown)
            )
        } finally {
            if (_state.value is ResourceInitState.Extracting) {
                _state.value = ResourceInitState.NotChecked
            }
            run?.finishCancelled()
        }
    }

    private fun fallbackFailureReport(
        trigger: String,
        error: Throwable,
        extractedCount: Int,
        totalCount: Int,
    ): ResourceInitFailureReport {
        val unwrapped = (error as? LocalizedException)?.cause ?: error
        val (errorType, errnoName) = ResourceInitDiagnostics.classifyError(unwrapped)
        val extState = runCatching { Environment.getExternalStorageState() }.getOrDefault("unknown")
        val pkgDir = runCatching { File(pathConfig.rootDir).parentFile?.parentFile }.getOrNull()
        return ResourceInitFailureReport(
            trigger = trigger,
            phase = "unknown",
            errorType = errorType,
            errnoName = errnoName,
            cause = ResourceInitDiagnostics.describeError(unwrapped).take(500),
            extractedCount = extractedCount,
            totalCount = totalCount,
            attemptFailures = 0,
            durationMs = 0L,
            externalStorageState = extState,
            externalDirPresent = pkgDir?.exists() ?: false,
            internalWritable = null,
            assetReadable = null,
            availStorageMb = null,
            diagnosticLog = null,
        )
    }

    private fun doForceSyncOverridesTemplate() {
        val dest = pathConfig.overrideTasksFile
        runCatching {
            dest.parentFile?.mkdirs()
            context.assets.open(OVERRIDES_ASSET_TASKS).use { src ->
                dest.outputStream().use { src.copyTo(it) }
            }
            Timber.d("overrides 模板已同步: ${dest.absolutePath}")
        }.onFailure {
            Timber.w(it, "overrides 模板同步失败，跳过")
        }
    }
}
