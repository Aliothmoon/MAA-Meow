package com.aliothmoon.maameow.data.datasource

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.constant.MaaFiles
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.resource.MaaCoreVersion
import com.aliothmoon.maameow.domain.service.ResourceInitFailureReport
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 采集资源初始化/解压期间的存储、权限、异常栈与探针诊断，
 * 优先落盘到内部 filesDir（外置存储不可写时也能留存）并由日志导出随 ZIP 打包。
 */
class ResourceInitDiagnostics(private val context: Context) {

    companion object {
        const val ENTRY_NAME = "resource_init_diag.txt"
        private const val MAX_RUNS = 3
        private const val MAX_ATTEMPT_RECORDS = 15
        private const val MAX_UNIQUE_STACKS = 3
        private const val MAX_SAMPLE_FILES = 10
        private const val MAX_LEFTOVERS = 20
        private const val MAX_MOUNT_LINES = 20
        private const val PROBE_FILE_NAME = ".maameow_diag_probe"
        private const val COPY_PROBE_FILE_NAME = ".maameow_diag_copy_probe"
        private val TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS (Z)")
        private val MOUNT_FILTER = Regex(
            "(/storage|/sdcard|/mnt/user|/mnt/pass_through|/mnt/androidwritable|/data/media|Android/data|fuse)",
            RegexOption.IGNORE_CASE,
        )

        fun describeError(t: Throwable): String = buildString {
            var cur: Throwable? = t
            var depth = 0
            while (cur != null && depth < 10) {
                if (depth > 0) append(" -> caused by: ")
                append(cur.javaClass.name)
                val msg = cur.message
                if (!msg.isNullOrBlank()) {
                    append(": ").append(msg)
                }
                val errno = extractErrno(cur)
                if (errno != null) {
                    append(" [errno=").append(errno.first)
                    if (errno.second != null) append("/").append(errno.second)
                    append("]")
                }
                cur = cur.cause
                depth++
            }
        }

        private fun describeErrorShort(t: Throwable): String {
            val root = rootCause(t)
            val errno = extractErrno(root) ?: extractErrno(t)
            val errnoTag = errno?.let { " [${it.first}/${it.second ?: "?"}]" }.orEmpty()
            val msg = (root.message ?: t.message).orEmpty().take(120)
            return "${t.javaClass.name}${if (root !== t) " -> ${root.javaClass.name}" else ""}$errnoTag: $msg"
        }

        private fun rootCause(t: Throwable): Throwable {
            var cur = t
            var depth = 0
            while (cur.cause != null && cur.cause !== cur && depth < 10) {
                cur = cur.cause!!
                depth++
            }
            return cur
        }

        private fun extractErrno(t: Throwable): Pair<Int, String?>? {
            if (t !is ErrnoException) return null
            val name = runCatching { OsConstants.errnoName(t.errno) }.getOrNull()
            return t.errno to name
        }

        /** 剥掉自家封装异常（R8 会混淆类名），返回底层可归组的异常类型与 POSIX errno */
        fun classifyError(t: Throwable): Pair<String, String?> {
            var unwrapped: Throwable = t
            var depth = 0
            while (unwrapped is LocalizedException && unwrapped.cause != null && depth < 5) {
                unwrapped = unwrapped.cause!!
                depth++
            }
            var errnoName: String? = null
            var cur: Throwable? = unwrapped
            var walk = 0
            while (cur != null && walk < 10) {
                val found = extractErrno(cur)
                if (found?.second != null) {
                    errnoName = found.second
                    break
                }
                cur = cur.cause
                walk++
            }
            val typeName = stableErrorName(unwrapped)
            return typeName to errnoName
        }

        private fun stableErrorName(t: Throwable): String {
            val full = t.javaClass.name
            val allowed = full.startsWith("java.") ||
                full.startsWith("javax.") ||
                full.startsWith("android.") ||
                full.startsWith("kotlinx.")
            return if (allowed) t.javaClass.simpleName.ifBlank { "Exception" } else "AppException"
        }

        private fun stackTraceOf(t: Throwable): String {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            return sw.toString().trimEnd()
        }
    }

    private val runSeq = AtomicInteger(0)
    private val activeRunCount = AtomicInteger(0)
    private val currentRunRef = AtomicReference<RunSession?>(null)
    private val historyLock = Any()
    private val history = ArrayDeque<String>(MAX_RUNS)

    fun activeRun(): RunSession? = currentRunRef.get()

    fun beginRun(trigger: String, pathConfig: MaaPathConfig): RunSession {
        val session = RunSession(
            runId = runSeq.incrementAndGet(),
            trigger = trigger,
            concurrentAtStart = activeRunCount.incrementAndGet(),
            destDir = File(pathConfig.resourceDir),
            pathConfig = pathConfig,
        )
        currentRunRef.set(session)
        session.captureEnvironmentSnapshot()
        return session
    }

    fun beginDirectRun(destDir: File): RunSession {
        val session = RunSession(
            runId = runSeq.incrementAndGet(),
            trigger = "direct",
            concurrentAtStart = activeRunCount.incrementAndGet(),
            destDir = destDir,
            pathConfig = null,
        )
        currentRunRef.set(session)
        session.captureEnvironmentSnapshot()
        return session
    }

    /** 优先返回内存态最新报告，冷重启后回退读内部 filesDir 持久化副本 */
    fun readReport(): String? {
        val inMemory = synchronized(historyLock) {
            if (history.isEmpty()) null else history.joinToString("\n\n")
        }
        if (inMemory != null) return inMemory
        return runCatching {
            val internalFile = File(context.filesDir, ENTRY_NAME)
            if (internalFile.isFile) internalFile.readText(Charsets.UTF_8) else null
        }.getOrNull()
    }

    private fun releaseRun(session: RunSession) {
        currentRunRef.compareAndSet(session, null)
        activeRunCount.decrementAndGet()
    }

    private fun commitRun(session: RunSession, reportText: String) {
        releaseRun(session)
        val combined = synchronized(historyLock) {
            while (history.size >= MAX_RUNS) history.removeFirst()
            history.addLast(reportText)
            history.joinToString("\n\n")
        }
        persistReport(combined)
    }

    private fun persistReport(content: String) {
        runCatching {
            val internalDir = context.filesDir
            if (!internalDir.exists()) internalDir.mkdirs()
            val target = File(internalDir, ENTRY_NAME)
            val tmp = File(internalDir, "$ENTRY_NAME.tmp")
            tmp.writeText(content, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(content, Charsets.UTF_8)
                tmp.delete()
            }
        }.onFailure {
            Timber.w(it, "写入内部解压诊断文件失败")
        }
    }

    inner class RunSession internal constructor(
        val runId: Int,
        val trigger: String,
        private val concurrentAtStart: Int,
        private val destDir: File,
        private val pathConfig: MaaPathConfig?,
    ) {
        private val startWall = ZonedDateTime.now().format(TIME_FMT)
        private val startMs = System.currentTimeMillis()
        private val startThread = Thread.currentThread().name
        private val finished = AtomicBoolean(false)
        private val probesRan = AtomicBoolean(false)

        private var envSnapshot: String = ""
        private var preCleanSnapshot: String = ""
        private var manifestSnapshot: String = ""
        private var postFailureSnapshot: String = ""

        private val totalAttemptFailures = AtomicInteger(0)
        private val totalFinalFailures = AtomicInteger(0)
        private val attemptLog = ConcurrentLinkedQueue<String>()
        private val attemptLogCount = AtomicInteger(0)
        private val errorHistogram = ConcurrentHashMap<String, AtomicInteger>()
        private val sampleFailedFiles = ConcurrentLinkedQueue<String>()
        private val sampleFailedFileSet = ConcurrentHashMap.newKeySet<String>()
        private val capturedStackSignatures = ConcurrentHashMap.newKeySet<String>()
        private val capturedStacks = ConcurrentLinkedQueue<String>()
        private val primaryFailure = AtomicReference<String?>(null)
        private val primaryPhase = AtomicReference<String?>(null)
        private val primaryError = AtomicReference<Throwable?>(null)

        @Volatile
        private var externalDirPresentAfterFail: Boolean? = null

        @Volatile
        private var internalWritable: Boolean? = null

        @Volatile
        private var assetReadable: Boolean? = null

        @Volatile
        private var availStorageMb: Long? = null

        fun captureEnvironmentSnapshot() {
            envSnapshot = runCatching { buildEnvironmentSnapshot() }
                .getOrElse { "env snapshot failed: ${describeError(it)}" }
        }

        fun recordPreClean(
            ensureOk: Boolean,
            ensureError: Throwable?,
            existedBefore: Boolean,
            deleteOk: Boolean,
            deleteMs: Long,
            leftovers: List<String>,
            mkdirsOk: Boolean,
            resourceDir: File,
        ) {
            preCleanSnapshot = buildString {
                appendLine("ensureDirectories : ok=$ensureOk${ensureError?.let { " err=${describeError(it)}" }.orEmpty()}")
                appendLine("existedBefore     : $existedBefore")
                appendLine("deleteRecursively : ok=$deleteOk (${deleteMs}ms) existsAfter=${resourceDir.exists()}")
                if (leftovers.isNotEmpty()) {
                    appendLine("leftovers (${leftovers.size}, max $MAX_LEFTOVERS):")
                    leftovers.forEach { appendLine("  - $it") }
                }
                appendLine("postCleanMkdirs   : ok=$mkdirsOk ${describePath(resourceDir)}")
            }.trimEnd()
        }

        fun collectLeftovers(resourceDir: File): List<String> {
            if (!resourceDir.exists()) return emptyList()
            return runCatching {
                val out = mutableListOf<String>()
                resourceDir.walkTopDown().forEach { f ->
                    if (out.size >= MAX_LEFTOVERS) return@forEach
                    val rel = runCatching { f.relativeTo(resourceDir).path }.getOrDefault(f.absolutePath)
                    out += "${rel.ifEmpty { "." }} | ${describePath(f)}"
                }
                out
            }.getOrElse { listOf("walk failed: ${describeError(it)}") }
        }

        fun recordManifest(
            loadMs: Long,
            loaded: Boolean,
            manifestTotalFiles: Int,
            filteredFiles: Int,
            assetDir: String,
            destDir: File,
            error: Throwable?,
        ) {
            manifestSnapshot = buildString {
                appendLine("loaded            : $loaded (${loadMs}ms)")
                appendLine("manifestTotalFiles: $manifestTotalFiles")
                appendLine("filteredFiles     : $filteredFiles (prefix=$assetDir/)")
                appendLine("destDir           : ${describePath(destDir)}")
                if (error != null) {
                    appendLine("manifestError     : ${describeError(error)}")
                    appendLine(stackTraceOf(error))
                }
            }.trimEnd()
            if (!loaded) {
                primaryPhase.compareAndSet(null, "manifest")
                if (error != null) primaryError.compareAndSet(null, error)
            }
        }

        fun recordAttemptFailure(
            assetPath: String,
            targetFile: File,
            attempt: Int,
            maxRetries: Int,
            phase: String,
            bytesCopied: Long,
            mkdirsResult: Boolean?,
            error: Throwable,
        ) {
            totalAttemptFailures.incrementAndGet()
            val sig = "$phase | ${describeErrorShort(error)}"
            errorHistogram.computeIfAbsent(sig) { AtomicInteger(0) }.incrementAndGet()

            if (sampleFailedFileSet.size < MAX_SAMPLE_FILES && sampleFailedFileSet.add(assetPath)) {
                sampleFailedFiles.add(assetPath)
            }

            if (attemptLogCount.incrementAndGet() <= MAX_ATTEMPT_RECORDS) {
                val elapsed = System.currentTimeMillis() - startMs
                val parent = targetFile.parentFile
                val entry = buildString {
                    append("+${elapsed}ms [${Thread.currentThread().name}] ")
                    append("attempt=$attempt/$maxRetries phase=$phase bytes=$bytesCopied mkdirs=$mkdirsResult ")
                    append("asset=$assetPath")
                    append("\n  err   : ${describeError(error)}")
                    append("\n  parent: ${parent?.let { describePath(it) } ?: "null"}")
                    append("\n  target: ${describePath(targetFile)}")
                }
                attemptLog.add(entry)
            }

            val stackSig = "$phase|${error.javaClass.name}|${extractErrno(rootCause(error))?.first}"
            if (capturedStackSignatures.size < MAX_UNIQUE_STACKS && capturedStackSignatures.add(stackSig)) {
                capturedStacks.add("--- stack [$stackSig] asset=$assetPath ---\n${stackTraceOf(error)}")
            }
        }

        fun recordFinalFileFailure(
            assetPath: String,
            targetFile: File,
            attempts: Int,
            phase: String,
            extractedSoFar: Int,
            totalFiles: Int,
            error: Throwable,
        ) {
            totalFinalFailures.incrementAndGet()
            primaryPhase.compareAndSet(null, phase)
            primaryError.compareAndSet(null, error)
            primaryFailure.compareAndSet(
                null,
                buildString {
                    appendLine("assetPath     : $assetPath")
                    appendLine("targetFile    : ${describePath(targetFile)}")
                    appendLine("attempts      : $attempts")
                    appendLine("phase         : $phase")
                    appendLine("extractedSoFar: $extractedSoFar / $totalFiles")
                    appendLine("error         : ${describeError(error)}")
                    appendLine("stackTrace:")
                    append(stackTraceOf(error))
                }.trimEnd(),
            )
        }

        fun runPostFailureProbes(destDir: File, failedAssetPath: String, failedTargetFile: File) {
            if (!probesRan.compareAndSet(false, true)) return
            postFailureSnapshot = runCatching {
                buildPostFailureProbes(destDir, failedAssetPath, failedTargetFile)
            }.getOrElse { "post-failure probes failed: ${describeError(it)}" }
        }

        fun runStorageOnlyProbes(destDir: File) {
            if (!probesRan.compareAndSet(false, true)) return
            postFailureSnapshot = runCatching {
                buildPostFailureProbes(destDir, failedAssetPath = null, failedTargetFile = null)
            }.getOrElse { "post-failure probes failed: ${describeError(it)}" }
        }

        fun finishSuccess() {
            if (!finished.compareAndSet(false, true)) return
            // 解压正常时不落盘诊断报告，避免给正常设备留多余文件
            releaseRun(this)
        }

        fun finishFailure(
            error: Throwable,
            extractedCount: Int,
            totalFiles: Int,
        ): ResourceInitFailureReport {
            // 若还没跑过失败后探针（例如 preClean/manifest 阶段抛错），补做一次存储探针
            runStorageOnlyProbes(destDir)
            val reportText = finish("FAILED", extractedCount, totalFiles, error)
            val rootErr = primaryError.get() ?: (error as? LocalizedException)?.cause ?: error
            val (errorType, errnoName) = classifyError(rootErr)
            val phase = primaryPhase.get() ?: "unknown"
            val extState = runCatching { Environment.getExternalStorageState() }.getOrDefault("unknown")
            val extPresent = externalDirPresentAfterFail ?: runCatching {
                val pkgDir = pathConfig?.let { File(it.rootDir).parentFile?.parentFile } ?: destDir
                pkgDir.exists()
            }.getOrDefault(false)
            return ResourceInitFailureReport(
                trigger = trigger,
                phase = phase,
                errorType = errorType,
                errnoName = errnoName,
                cause = describeError(rootErr).take(500),
                extractedCount = extractedCount,
                totalCount = totalFiles,
                attemptFailures = totalAttemptFailures.get(),
                durationMs = System.currentTimeMillis() - startMs,
                externalStorageState = extState,
                externalDirPresent = extPresent,
                internalWritable = internalWritable,
                assetReadable = assetReadable,
                availStorageMb = availStorageMb,
                diagnosticLog = reportText,
            )
        }

        fun finishCancelled() {
            if (!finished.compareAndSet(false, true)) return
            releaseRun(this)
        }

        private fun finish(
            outcome: String,
            extractedCount: Int,
            totalFiles: Int,
            finalError: Throwable?,
        ): String? {
            if (!finished.compareAndSet(false, true)) return null
            val endWall = ZonedDateTime.now().format(TIME_FMT)
            val durationMs = System.currentTimeMillis() - startMs

            val text = buildString {
                appendLine("============================================================")
                appendLine("=== Resource Init Run #$runId ($outcome) ===")
                appendLine("trigger           : $trigger")
                appendLine("startTime         : $startWall")
                appendLine("endTime           : $endWall (${durationMs}ms)")
                appendLine("startThread       : $startThread (concurrentRunsAtStart=$concurrentAtStart)")
                appendLine("progress          : extracted=$extractedCount / total=$totalFiles")
                appendLine("failureCounters   : attemptFailures=${totalAttemptFailures.get()}, finalFileFailures=${totalFinalFailures.get()}")
                if (finalError != null) {
                    appendLine("finalException    : ${describeError(finalError)}")
                }
                appendLine("--- Environment & Storage ---")
                appendLine(envSnapshot)
                if (preCleanSnapshot.isNotEmpty()) {
                    appendLine("--- Pre-Clean ---")
                    appendLine(preCleanSnapshot)
                }
                if (manifestSnapshot.isNotEmpty()) {
                    appendLine("--- Manifest ---")
                    appendLine(manifestSnapshot)
                }
                val primary = primaryFailure.get()
                if (primary != null) {
                    appendLine("--- Primary File Failure ---")
                    appendLine(primary)
                }
                if (errorHistogram.isNotEmpty()) {
                    appendLine("--- Error Histogram ---")
                    errorHistogram.entries
                        .sortedByDescending { it.value.get() }
                        .forEach { (sig, cnt) -> appendLine("  ${cnt.get()}x  $sig") }
                }
                if (sampleFailedFiles.isNotEmpty()) {
                    appendLine("--- Sample Failed Files (${sampleFailedFiles.size}) ---")
                    sampleFailedFiles.forEach { appendLine("  - $it") }
                }
                if (attemptLog.isNotEmpty()) {
                    appendLine("--- Attempt Failures (logged ${attemptLog.size} of ${totalAttemptFailures.get()}) ---")
                    attemptLog.forEach { appendLine(it) }
                }
                if (capturedStacks.isNotEmpty()) {
                    appendLine("--- Unique Stack Traces ---")
                    capturedStacks.forEach { appendLine(it) }
                }
                if (postFailureSnapshot.isNotEmpty()) {
                    appendLine("--- Post-Failure Probes ---")
                    appendLine(postFailureSnapshot)
                }
                if (finalError != null && primary == null) {
                    appendLine("--- Final Exception Stack ---")
                    appendLine(stackTraceOf(finalError))
                }
                append("============================================================")
            }

            commitRun(this, text)
            return text
        }

        private fun buildEnvironmentSnapshot(): String = buildString {
            appendLine("app               : ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) [${BuildConfig.BUILD_TYPE}]")
            appendLine("maaCore           : ${MaaCoreVersion.current.ifBlank { "unknown" }}")
            appendLine("device            : ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) / ABI=${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("process           : ${readProcessIdentity()} cpus=${Runtime.getRuntime().availableProcessors()} permit=${AssetExtractor.PERMIT}")
            appendLine("jvmHeap           : ${readJvmMemory()}")
            appendLine("apk               : ${readApkInfo()}")
            val rawExt = runCatching { context.getExternalFilesDir(null) }.getOrNull()
            appendLine("rawExternalDir    : ${rawExt?.let { describePath(it) } ?: "NULL"}")
            val allExt = runCatching { context.getExternalFilesDirs(null)?.toList().orEmpty() }.getOrNull()
            if (allExt != null) {
                appendLine("allExternalDirs   : ${allExt.joinToString(" ; ") { it?.let { f -> describePath(f) } ?: "NULL" }}")
            }
            val extState = runCatching {
                "state=${Environment.getExternalStorageState()} emulated=${Environment.isExternalStorageEmulated()} removable=${Environment.isExternalStorageRemovable()}"
            }.getOrElse { "unavailable(${describeError(it)})" }
            appendLine("externalStorage   : $extState")
            appendLine("internalFilesDir  : ${runCatching { describePath(context.filesDir) }.getOrElse { "unavailable" }}")
            appendLine("internalCacheDir  : ${runCatching { describePath(context.cacheDir) }.getOrElse { "unavailable" }}")
            if (pathConfig != null) {
                appendLine("coreLocation      : ${pathConfig.coreLocation} (coreRoot=${pathConfig.coreRootDir})")
                appendLine("isResourceReady   : ${runCatching { pathConfig.isResourceReady }.getOrElse { "err:${describeError(it)}" }}")
                appendLine("rootDir           : ${describePath(File(pathConfig.rootDir))}")
                appendLine("resourceDir       : ${describePath(File(pathConfig.resourceDir))}")
                appendLine("debugDir          : ${describePath(File(pathConfig.debugDir))}")
                val extParent = rawExt?.parentFile
                if (extParent != null) {
                    appendLine("androidDataPkgDir : ${describePath(extParent)}")
                }
                appendLine("versionFile       : ${describePath(File(pathConfig.resourceDir, MaaFiles.VERSION_FILE))}")
                appendLine("appVersionFile    : ${describePath(File(pathConfig.rootDir, MaaFiles.APP_VERSION_FILE))}")
            } else {
                appendLine("destDir           : ${describePath(destDir)}")
            }
            appendLine("statFs(dest)      : ${readStatFs(destDir)}")
            appendLine("statFs(internal)  : ${runCatching { readStatFs(context.filesDir) }.getOrElse { "unavailable" }}")
            appendLine("openFds           : ${readFdInfo()}")
        }.trimEnd()

        private fun buildPostFailureProbes(destDir: File, failedAssetPath: String?, failedTargetFile: File?): String = buildString {
            appendLine("openFdsAfterFail  : ${readFdInfo()}")
            val destStat = readStatFsWithAvailMb(destDir)
            availStorageMb = destStat.second
            appendLine("statFsAfter(dest) : ${destStat.first}")
            appendLine("statFsAfter(int)  : ${runCatching { readStatFs(context.filesDir) }.getOrElse { "unavailable" }}")
            val pkgDir = runCatching { pathConfig?.let { File(it.rootDir).parentFile?.parentFile } ?: destDir }.getOrNull()
            externalDirPresentAfterFail = pkgDir?.exists() ?: destDir.exists()
            appendLine("externalPkgPresent: $externalDirPresentAfterFail (${pkgDir?.absolutePath ?: destDir.absolutePath})")
            val parent = failedTargetFile?.parentFile
            if (failedTargetFile != null) {
                appendLine("failedParentState : ${parent?.let { describePath(it) } ?: "null"}")
                if (parent != null && parent.isDirectory) {
                    val children = runCatching { parent.list()?.sorted() }.getOrNull()
                    appendLine("failedParentList  : count=${children?.size ?: -1} sample=${children?.take(20)?.joinToString(", ") ?: "null"}")
                }
                appendLine("failedTargetState : ${describePath(failedTargetFile)}")
            }
            if (failedAssetPath != null) {
                val assetProbe = probeAssetOpen(failedAssetPath)
                assetReadable = assetProbe.startsWith("OK")
                appendLine("assetOpenProbe    : $assetProbe")
                appendLine("assetDirListProbe : ${probeAssetDirList(failedAssetPath)}")
            }
            if (parent != null) {
                appendLine("writeProbe(parent): ${probeWrite(parent, PROBE_FILE_NAME)}")
            }
            appendLine("writeProbe(dest)  : ${probeWrite(destDir, PROBE_FILE_NAME)}")
            val intProbe = runCatching { probeWrite(context.filesDir, PROBE_FILE_NAME) }.getOrElse { describeError(it) }
            internalWritable = intProbe.startsWith("OK")
            appendLine("writeProbe(int)   : $intProbe")
            if (failedAssetPath != null) {
                appendLine("syncCopyProbe(dst): ${probeSyncCopy(failedAssetPath, destDir)}")
                if (parent != null) {
                    appendLine("syncCopyProbe(par): ${probeSyncCopy(failedAssetPath, parent)}")
                }
            }
            val mounts = readRelevantMounts()
            if (mounts.isNotEmpty()) {
                appendLine("mounts (storage, ${mounts.size}):")
                mounts.forEach { appendLine("  $it") }
            }
        }.trimEnd()

        private fun readStatFsWithAvailMb(path: File): Pair<String, Long?> {
            var availMb: Long? = null
            val text = runCatching {
                var target: File? = path
                while (target != null && !target.exists()) {
                    target = target.parentFile
                }
                val resolved = target ?: return@runCatching "no existing ancestor for ${path.path}"
                val sf = StatFs(resolved.absolutePath)
                availMb = sf.availableBytes / (1024L * 1024L)
                val availMbDouble = sf.availableBytes / (1024.0 * 1024.0)
                val freeMb = sf.freeBytes / (1024.0 * 1024.0)
                val totalMb = sf.totalBytes / (1024.0 * 1024.0)
                String.format(
                    Locale.US,
                    "at=%s avail=%.1fMB (%dB) free=%.1fMB total=%.1fMB blockSize=%d availBlocks=%d",
                    resolved.absolutePath,
                    availMbDouble,
                    sf.availableBytes,
                    freeMb,
                    totalMb,
                    sf.blockSizeLong,
                    sf.availableBlocksLong,
                )
            }.getOrElse { "unavailable(${describeError(it)})" }
            return text to availMb
        }
    }

    private fun readProcessIdentity(): String = runCatching {
        "pid=${Process.myPid()} myUid=${Process.myUid()} uid=${Os.getuid()} euid=${Os.geteuid()} gid=${Os.getgid()} egid=${Os.getegid()}"
    }.getOrElse {
        runCatching { "pid=${Process.myPid()} myUid=${Process.myUid()}" }.getOrDefault("unavailable")
    }

    private fun readJvmMemory(): String {
        val rt = Runtime.getRuntime()
        val mb = 1024L * 1024L
        return "used=${(rt.totalMemory() - rt.freeMemory()) / mb}MB total=${rt.totalMemory() / mb}MB max=${rt.maxMemory() / mb}MB"
    }

    private fun readApkInfo(): String = runCatching {
        val ai = context.applicationInfo
        val base = ai.sourceDir?.let { describePath(File(it)) } ?: "null"
        val splits = ai.splitSourceDirs?.joinToString(", ") { describePath(File(it)) } ?: "none"
        "sourceDir=$base | splits=$splits"
    }.getOrElse { "unavailable(${describeError(it)})" }

    private fun describePath(file: File): String = runCatching {
        val kind = when {
            !file.exists() -> "missing"
            file.isDirectory -> "dir"
            file.isFile -> "file(${file.length()}B)"
            else -> "other"
        }
        val perms = "${if (file.canRead()) 'r' else '-'}${if (file.canWrite()) 'w' else '-'}${if (file.canExecute()) 'x' else '-'}"
        val lstat = runCatching {
            val st = Os.lstat(file.path)
            val modeOct = Integer.toOctalString(st.st_mode and 0xFFFF)
            "lstat(uid=${st.st_uid},gid=${st.st_gid},mode=0$modeOct,size=${st.st_size},dev=${st.st_dev},ino=${st.st_ino})"
        }.getOrElse { "lstat(${describeErrorShort(it)})" }
        "${file.absolutePath} [$kind $perms $lstat]"
    }.getOrElse { "${file.path} [inspectErr=${describeError(it)}]" }

    private fun readStatFs(path: File): String = runCatching {
        var target: File? = path
        while (target != null && !target.exists()) {
            target = target.parentFile
        }
        val resolved = target ?: return@runCatching "no existing ancestor for ${path.path}"
        val sf = StatFs(resolved.absolutePath)
        val availMb = sf.availableBytes / (1024.0 * 1024.0)
        val freeMb = sf.freeBytes / (1024.0 * 1024.0)
        val totalMb = sf.totalBytes / (1024.0 * 1024.0)
        String.format(
            Locale.US,
            "at=%s avail=%.1fMB (%dB) free=%.1fMB total=%.1fMB blockSize=%d availBlocks=%d",
            resolved.absolutePath,
            availMb,
            sf.availableBytes,
            freeMb,
            totalMb,
            sf.blockSizeLong,
            sf.availableBlocksLong,
        )
    }.getOrElse { "unavailable(${describeError(it)})" }

    private fun readFdInfo(): String {
        val fdCount = runCatching { File("/proc/self/fd").list()?.size ?: -1 }.getOrDefault(-1)
        val limitLine = runCatching {
            File("/proc/self/limits").readLines()
                .firstOrNull { it.startsWith("Max open files") }
                ?.trim()
                ?.replace(Regex("\\s+"), " ")
        }.getOrNull()
        return "count=$fdCount limit=${limitLine ?: "unknown"}"
    }

    private fun readRelevantMounts(): List<String> = runCatching {
        val file = File("/proc/self/mounts")
        if (!file.canRead()) return@runCatching emptyList()
        file.useLines { seq ->
            seq.filter { MOUNT_FILTER.containsMatchIn(it) }
                .take(MAX_MOUNT_LINES)
                .toList()
        }
    }.getOrElse { listOf("mounts read failed: ${describeError(it)}") }

    private fun probeAssetOpen(assetPath: String): String = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buf = ByteArray(16 * 1024)
        context.assets.open(assetPath).use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                total += n
            }
        }
        val hex = md.digest().take(8).joinToString("") { "%02x".format(it) }
        "OK (bytes=$total, sha256_16=$hex)"
    }.getOrElse { "FAILED: ${describeError(it)}" }

    private fun probeAssetDirList(assetPath: String): String = runCatching {
        val dir = assetPath.substringBeforeLast('/', "")
        val name = assetPath.substringAfterLast('/')
        val entries = context.assets.list(dir)
        val present = entries?.contains(name) == true
        "dir='$dir' count=${entries?.size ?: -1} containsTarget=$present"
    }.getOrElse { "FAILED: ${describeError(it)}" }

    private fun probeWrite(dir: File, fileName: String): String = runCatching {
        val mkdirsOk = if (!dir.exists()) dir.mkdirs() else true
        val probe = File(dir, fileName)
        try {
            FileOutputStream(probe).use { out ->
                out.write("maameow-diag-ok\n".toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val len = probe.length()
            val deleted = probe.delete()
            "OK (mkdirs=$mkdirsOk, written=${len}B, deleted=$deleted)"
        } finally {
            runCatching { if (probe.exists()) probe.delete() }
        }
    }.getOrElse { "FAILED: ${describeError(it)}" }

    private fun probeSyncCopy(assetPath: String, targetDir: File): String = runCatching {
        val mkdirsOk = if (!targetDir.exists()) targetDir.mkdirs() else true
        val probe = File(targetDir, COPY_PROBE_FILE_NAME)
        try {
            var copied = 0L
            context.assets.open(assetPath).use { input ->
                FileOutputStream(probe).use { out ->
                    copied = input.copyTo(out)
                }
            }
            val len = probe.length()
            val deleted = probe.delete()
            "OK (mkdirs=$mkdirsOk, copied=${copied}B, onDisk=${len}B, deleted=$deleted)"
        } finally {
            runCatching { if (probe.exists()) probe.delete() }
        }
    }.getOrElse { "FAILED: ${describeError(it)}" }
}
