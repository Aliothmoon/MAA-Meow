package com.aliothmoon.maameow.data.datasource

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.AssetManifest
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference


class AssetExtractor(
    private val context: Context,
    private val diagnostics: ResourceInitDiagnostics? = null,
) {

    companion object {
        private const val MANIFEST_FILE_NAME = "MaaSync/asset_manifest.json"
        val PERMIT = Runtime.getRuntime().availableProcessors()
        private const val PROGRESS_INTERVAL_MS = 100L
        private const val MAX_RETRY_WARN_LOGS = 8
    }

    class ExtractFailedException(
        failedFile: String,
        attempts: Int,
        cause: Throwable
    ) : LocalizedException(uiTextOf(R.string.resource_extract_failed, failedFile, attempts), cause)

    private val json = JsonUtils.common

    private class AttemptTrace {
        var phase: String = "init"
        var mkdirsResult: Boolean? = null
        var bytesCopied: Long = 0L
    }

    private class FailedTarget(
        val assetPath: String,
        val targetFile: File,
    )

    data class ExtractProgress(
        val extractedCount: Int,
        val totalCount: Int,
        val currentFile: String
    )

    private object BufferPool {
        private const val BUFFER_SIZE = 128 * 1024
        private val POOL_SIZE = PERMIT
        private val pool = ArrayBlockingQueue<ByteArray>(POOL_SIZE)

        init {
            repeat(POOL_SIZE) {
                pool.offer(ByteArray(BUFFER_SIZE))
            }
        }

        fun acquire(): ByteArray = pool.poll() ?: ByteArray(BUFFER_SIZE)

        fun release(buffer: ByteArray) {
            if (buffer.size == BUFFER_SIZE) {
                pool.offer(buffer)
            }
        }

        inline fun <T> use(block: (ByteArray) -> T): T {
            val buffer = acquire()
            return try {
                block(buffer)
            } finally {
                release(buffer)
            }
        }
    }


    suspend fun extract(
        assetDir: String,
        destDir: File,
        onProgress: (ExtractProgress) -> Unit
    ): Result<Int> {
        val existingRun = diagnostics?.activeRun()
        var ownedRun: ResourceInitDiagnostics.RunSession? = null
        var runSession: ResourceInitDiagnostics.RunSession? = existingRun
        val extractedCount = AtomicInteger(0)
        val firstFinalFailure = AtomicBoolean(false)
        val retryWarnLogs = AtomicInteger(0)
        val primaryFailedTarget = AtomicReference<FailedTarget?>(null)
        var totalFiles = 0

        return try {
            if (runSession == null && diagnostics != null) {
                // 块内赋值，防 withContext 返回处取消丢掉句柄
                withContext(Dispatchers.IO) { ownedRun = diagnostics.beginDirectRun(destDir) }
                runSession = ownedRun
            }
            val startTime = System.currentTimeMillis()
            val lastReportTime = AtomicLong(0)
            val semaphore = Semaphore(PERMIT)

            val manifestStart = System.currentTimeMillis()
            val manifestResult = loadAssetManifest()
            val manifestLoadMs = System.currentTimeMillis() - manifestStart
            val manifest = manifestResult.first
            val manifestError = manifestResult.second
            if (manifest == null) {
                runSession?.recordManifest(
                    loadMs = manifestLoadMs,
                    loaded = false,
                    manifestTotalFiles = 0,
                    filteredFiles = 0,
                    assetDir = assetDir,
                    destDir = destDir,
                    error = manifestError,
                )
                throw IllegalStateException("Assets 清单文件不存在，请重新构建项目", manifestError)
            }
            val allFiles = manifest.files.filter { it.startsWith("$assetDir/") }
            totalFiles = allFiles.size
            runSession?.recordManifest(
                loadMs = manifestLoadMs,
                loaded = true,
                manifestTotalFiles = manifest.files.size,
                filteredFiles = totalFiles,
                assetDir = assetDir,
                destDir = destDir,
                error = null,
            )

            Timber.d("待复制文件数: $totalFiles")
            onProgress(ExtractProgress(0, totalFiles, ""))

            withContext(Dispatchers.IO) {
                coroutineScope {
                    for (assetPath in allFiles) {
                        val relativePath = assetPath.removePrefix("$assetDir/")

                        launch {
                            semaphore.withPermit {
                                val targetFile = File(destDir, relativePath)
                                val trace = AttemptTrace()
                                try {
                                    retryWithBackoff(
                                        assetPath = assetPath,
                                        targetFile = targetFile,
                                        trace = trace,
                                        runSession = runSession,
                                        retryWarnLogs = retryWarnLogs,
                                        maxRetries = 3,
                                        initialDelayMs = 120,
                                    ) {
                                        trace.phase = "mkdirs"
                                        trace.mkdirsResult = targetFile.parentFile?.let { it.mkdirs() || it.isDirectory }
                                        trace.bytesCopied = 0L
                                        BufferPool.use { buffer ->
                                            trace.phase = "assets.open"
                                            context.assets.open(assetPath).use { input ->
                                                trace.phase = "FileOutputStream"
                                                FileOutputStream(targetFile).use { output ->
                                                    trace.phase = "copy"
                                                    input.copyToWithBuffer(output, buffer, trace)
                                                    trace.phase = "close"
                                                }
                                            }
                                        }
                                    }
                                    val count = extractedCount.incrementAndGet()
                                    // 逐文件回调会让界面每帧重组，限到 100ms 一次
                                    val now = System.currentTimeMillis()
                                    val last = lastReportTime.get()
                                    if (count == totalFiles ||
                                        (now - last >= PROGRESS_INTERVAL_MS && lastReportTime.compareAndSet(last, now))
                                    ) {
                                        onProgress(ExtractProgress(extractedCount.get(), totalFiles, relativePath))
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    val done = extractedCount.get()
                                    primaryFailedTarget.compareAndSet(null, FailedTarget(assetPath, targetFile))
                                    runSession?.recordFinalFileFailure(
                                        assetPath = assetPath,
                                        targetFile = targetFile,
                                        attempts = 3,
                                        phase = trace.phase,
                                        extractedSoFar = done,
                                        totalFiles = totalFiles,
                                        error = e,
                                    )
                                    val desc = ResourceInitDiagnostics.describeError(e)
                                    if (firstFinalFailure.compareAndSet(false, true)) {
                                        Timber.e(
                                            e,
                                            "文件复制最终失败: file=%s phase=%s extracted=%d/%d err=%s",
                                            assetPath,
                                            trace.phase,
                                            done,
                                            totalFiles,
                                            desc,
                                        )
                                    } else {
                                        Timber.e(
                                            "文件复制最终失败: file=%s phase=%s extracted=%d/%d err=%s",
                                            assetPath,
                                            trace.phase,
                                            done,
                                            totalFiles,
                                            desc,
                                        )
                                    }
                                    throw ExtractFailedException(assetPath, 3, e)
                                }
                            }
                        }
                    }
                }
            }

            val totalTime = System.currentTimeMillis() - startTime
            Timber.i("复制完成: ${extractedCount.get()} 个文件, 耗时: ${totalTime}ms")
            ownedRun?.let { session -> withContext(Dispatchers.IO) { session.finishSuccess() } }

            Result.success(extractedCount.get())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 等 coroutineScope 取消并回收全部并发协程后再做探针，避免空占信号量引发连锁空跑
            runSession?.let { session ->
                withContext(Dispatchers.IO) {
                    val failed = primaryFailedTarget.get()
                    if (failed != null) {
                        session.runPostFailureProbes(destDir, failed.assetPath, failed.targetFile)
                    } else {
                        session.runStorageOnlyProbes(destDir)
                    }
                }
            }
            if (e is ExtractFailedException) {
                Timber.e("复制失败: %s", ResourceInitDiagnostics.describeError(e.cause ?: e))
            } else {
                Timber.e(e, "复制失败: %s", ResourceInitDiagnostics.describeError(e))
            }
            ownedRun?.let { session ->
                withContext(Dispatchers.IO) {
                    session.finishFailure(e, extractedCount.get(), totalFiles)
                }
            }
            Result.failure(e)
        } finally {
            ownedRun?.finishCancelled()
        }
    }


    private fun loadAssetManifest(): Pair<AssetManifest?, Throwable?> {
        return try {
            context.assets.open(MANIFEST_FILE_NAME).use { input ->
                val jsonText = input.bufferedReader().use { it.readText() }
                json.decodeFromString<AssetManifest>(jsonText) to null
            }
        } catch (e: Exception) {
            Timber.e(e, "读取 Assets 清单失败: %s", ResourceInitDiagnostics.describeError(e))
            null to e
        }
    }

    private suspend fun <T> retryWithBackoff(
        assetPath: String,
        targetFile: File,
        trace: AttemptTrace,
        runSession: ResourceInitDiagnostics.RunSession?,
        retryWarnLogs: AtomicInteger,
        maxRetries: Int = 3,
        initialDelayMs: Long = 100,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                runSession?.recordAttemptFailure(
                    assetPath = assetPath,
                    targetFile = targetFile,
                    attempt = attempt + 1,
                    maxRetries = maxRetries,
                    phase = trace.phase,
                    bytesCopied = trace.bytesCopied,
                    mkdirsResult = trace.mkdirsResult,
                    error = e,
                )
                if (attempt < maxRetries - 1) {
                    val delayMs = initialDelayMs * (1 shl attempt) // 100, 200, 400
                    // 限流且不附完整堆栈，避免并发失败刷爆错误日志；完整统计留在诊断报告中
                    if (retryWarnLogs.incrementAndGet() <= MAX_RETRY_WARN_LOGS) {
                        Timber.w(
                            "重试 %d/%d，等待 %dms: file=%s phase=%s err=%s",
                            attempt + 1,
                            maxRetries,
                            delayMs,
                            assetPath,
                            trace.phase,
                            ResourceInitDiagnostics.describeError(e),
                        )
                    }
                    delay(delayMs)
                }
            }
        }
        throw lastException ?: IllegalStateException("Retry failed without exception")
    }

    private fun InputStream.copyToWithBuffer(out: OutputStream, buffer: ByteArray, trace: AttemptTrace): Long {
        var bytesCopied: Long = 0
        var bytes = read(buffer)
        while (bytes >= 0) {
            out.write(buffer, 0, bytes)
            bytesCopied += bytes
            trace.bytesCopied = bytesCopied
            bytes = read(buffer)
        }
        return bytesCopied
    }
}
