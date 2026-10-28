package com.aliothmoon.maameow.telemetry

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * 给事件配证据，沿用 MaaFwApp `TaskFailureReporter`
 *
 * 取证出任何岔子事件照发，只是不带证据；取证那一会儿进程被杀则事件丢失
 * 各方法由调用方串行调用
 */
internal class IncidentReporter(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val store: () -> EvidenceStore,
    /** 当前保存着的密钥明文，日志离开设备前换成掩码 */
    private val secrets: suspend () -> Collection<String>,
    private val encodeImage: (ByteArray) -> ByteArray?,
    /** 前台模式下 Core 截的是主屏，可能拍到别的应用，不带 */
    private val imagesAllowed: () -> Boolean,
    private val attachmentSampleRate: Double,
    private val send: (Incident, Evidence) -> Unit,
) {

    private var workers: Job = SupervisorJob()
    private var runStart: Deferred<EvidenceStart>? = null
    private var taskStart: Deferred<EvidenceStart>? = null

    /** 会话日志每轮一个新文件，开跑时认一次，这一轮的任务都用它 */
    @Volatile
    private var sessionLog: EvidenceFile? = null

    /** 这一轮里已经配过截图的失败；同一个失败反复出现时画面几乎一样，再带是白占附件额度 */
    private val imagedFailures = HashSet<List<String>>()

    fun onRunStarted() {
        taskStart = null
        sessionLog = null
        imagedFailures.clear()
        runStart = scope.async(workers + io) {
            val evidenceStore = store()
            val session = latestSessionLog(evidenceStore).also { sessionLog = it }
            TaskEvidence.captureStart(evidenceStore, runFiles(session))
        }
    }

    fun onTaskStarted() {
        taskStart = scope.async(workers + io) {
            val evidenceStore = store()
            val session = sessionLog ?: latestSessionLog(evidenceStore)
            TaskEvidence.captureStart(evidenceStore, taskFiles(session))
        }
    }

    fun report(incident: Incident) {
        val failure = incident as? TaskFailure
        val start = when (incident) {
            // 任务失败只看这个任务期间的
            is TaskFailure -> taskStart.also { taskStart = null }
            // 触发日志一次一个文件，整份都是这次的
            is LaunchFailure -> if (incident.withBootLogs) {
                scope.async(workers + io) { withBootLogTails(store(), incident.logFile) }
            } else {
                CompletableDeferred(wholeFile(incident.logFile))
            }
            // 本地日志里没有 Shizuku 的死因
            is ShizukuDown -> null
            // 启动失败与进程死亡看整轮的
            else -> runStart
        }
        // null = 去取截图
        val noImages: AttachmentOutcome? = when {
            failure == null || !imagesAllowed() -> AttachmentOutcome.NotSelected
            !shouldSampleAttachment(failure.runId, failure.taskId.toLong(), attachmentSampleRate) ->
                AttachmentOutcome.NotSelected
            // 排在采样之后：没采到的那次不该挡住后面采到的
            !imagedFailures.add(failure.fingerprint) -> DUPLICATE
            else -> null
        }
        scope.launch(workers + io) {
            val evidence = try {
                collect(start, noImages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Evidence(null, AttachmentOutcome.Omitted("build_failed", e.toString()))
            }
            // 用户在这当口关了遥测就不该再发
            ensureActive()
            // 这里抛出去就是进程崩溃
            try {
                send(incident, evidence)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.w(e, "[telemetry] 事件发送失败")
            }
        }
    }

    /** 等在途的取证与发送，超时不再等 */
    suspend fun awaitIdle(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { workers.children.toList().joinAll() }
    }

    fun cancelAll() {
        workers.cancel()
        workers = SupervisorJob()
        runStart = null
        taskStart = null
        sessionLog = null
        imagedFailures.clear()
    }

    private suspend fun collect(start: Deferred<EvidenceStart>?, noImages: AttachmentOutcome?): Evidence {
        val snapshot = start?.await() ?: return Evidence(null, AttachmentOutcome.NotSelected)
        // 遥测先于各处理器收到回调，等它们把那条出错日志落盘
        delay(LOG_SETTLE_MS)
        val evidenceStore = store()
        val logs = TaskEvidence.collectLogs(evidenceStore, snapshot)
            .redacted(SecretRedaction.redactable(secrets()))
        val attachment = noImages ?: TaskEvidence.collectImages(evidenceStore, snapshot, encodeImage)
        return Evidence(logs, attachment)
    }

    private fun DiagnosticLogs.redacted(secrets: List<String>) = DiagnosticLogs(
        entries = entries.map {
            DiagnosticLog(it.source, it.kind, SecretRedaction.redact(it.content, secrets), it.rawBytes)
        },
        selectedRawBytes = selectedRawBytes,
        truncated = truncated,
        warnings = warnings,
    )

    private fun latestSessionLog(store: EvidenceStore): EvidenceFile? =
        store.list(SESSION_LOG_DIR, core = false).orEmpty()
            .filter { it.startsWith(SESSION_LOG_PREFIX) }
            // 文件名里是 yyyyMMdd_HHmmss，字典序即时间序
            .maxOrNull()
            ?.let { EvidenceFile("$SESSION_LOG_DIR/$it", kind = "session", core = false) }

    private companion object {
        const val SESSION_LOG_DIR = "gui"
        const val SESSION_LOG_PREFIX = "meow_log_"

        /** 会话日志按批落盘，周期不到 100ms */
        const val LOG_SETTLE_MS = 500L

        val DUPLICATE = AttachmentOutcome.Omitted(
            "duplicate", "the same failure already carried a screenshot in this run"
        )

        val CORE_LOG = EvidenceFile("asst.log", kind = "maacore", core = true)
        val APP_LOG = EvidenceFile("error_logs/error.log", kind = "app", core = false)

        fun taskFiles(session: EvidenceFile?): List<EvidenceFile> = listOfNotNull(CORE_LOG, session, APP_LOG)

        /** 基线长度记 0，取证时整份算新写的 */
        fun wholeFile(path: String) = EvidenceStart(
            files = listOf(EvidenceFile(path, kind = "schedule", core = false)),
            lengths = mapOf(path to 0L),
            images = null,
        )

        /** 不分轮次，只取最近一段 */
        val BOOT_LOGS = listOf(
            EvidenceFile("service_bind_debug.log", kind = "boot", core = false),
            EvidenceFile("shizuku_launch_debug.log", kind = "boot", core = true),
            EvidenceFile("root_launch_debug.log", kind = "boot", core = true),
        )
        const val BOOT_LOG_TAIL_BYTES = 8L * 1024

        /** 基线往回退一段，取证时这一段就算新写的 */
        fun withBootLogTails(store: EvidenceStore, triggerLog: String): EvidenceStart {
            val trigger = wholeFile(triggerLog)
            val tails = BOOT_LOGS.mapNotNull { file ->
                store.length(file)?.let { file.path to (it - BOOT_LOG_TAIL_BYTES).coerceAtLeast(0) }
            }
            return EvidenceStart(
                files = trigger.files + BOOT_LOGS,
                lengths = trigger.lengths + tails,
                images = null,
            )
        }

        /** Core 的崩溃现场与启动诊断日志体积小却最要紧，排在前面免得被大日志挤掉 */
        fun runFiles(session: EvidenceFile?): List<EvidenceFile> = listOf(
            EvidenceFile("crash.log", kind = "maacore", core = true),
            EvidenceFile("service_bind_debug.log", kind = "boot", core = false),
            EvidenceFile("service_boot_debug.log", kind = "boot", core = true),
            EvidenceFile("shizuku_launch_debug.log", kind = "boot", core = true),
            EvidenceFile("root_launch_debug.log", kind = "boot", core = true),
        ) + taskFiles(session)
    }
}

/**
 * 失败事件带出错截图的比例；事件本身与日志不受它影响
 *
 * 正式版量大，按附件额度抽样；预发布版与本地构建用户少，问题多数只出现一两次，抽样会让大半问题没图
 */
internal fun failureAttachmentSampleRate(versionName: String, debug: Boolean): Double =
    if (!debug && STABLE_VERSION.matches(versionName)) STABLE_ATTACHMENT_SAMPLE_RATE else 1.0

/** 附件额度每月 10 GB：按 2 万日活、每人每天两轮、每轮 0.35 条失败估，全带一个月要 20 GB 上下 */
private const val STABLE_ATTACHMENT_SAMPLE_RATE = 0.2

/** 正式版的版本名只有三段数字，预发布版带 `-beta.1` 这类后缀 */
private val STABLE_VERSION = Regex("""\d+\.\d+\.\d+""")

/** 按轮次与任务算哈希而不是掷随机数：同一个失败无论重算几次结论都一样 */
internal fun shouldSampleAttachment(runId: String, taskId: Long, sampleRate: Double): Boolean {
    if (sampleRate <= 0.0) return false
    if (sampleRate >= 1.0) return true
    val digest = MessageDigest.getInstance("SHA-256").apply {
        update("maameow-failure-attachment-v1:".toByteArray(Charsets.UTF_8))
        update(runId.toByteArray(Charsets.UTF_8))
        update(ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(taskId).array())
    }.digest()
    val bucket = ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).long
    // 无符号 64 位落到 [0, 1)
    return bucket.toULong().toDouble() / ULong.MAX_VALUE.toDouble() < sampleRate
}
