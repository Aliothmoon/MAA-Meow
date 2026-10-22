package com.aliothmoon.maameow.telemetry

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.MaaCoreVersion
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.LaunchOutcome
import com.aliothmoon.maameow.domain.service.RunKind
import com.aliothmoon.maameow.domain.service.RunTelemetry
import com.aliothmoon.maameow.domain.service.ScreenPowerController
import com.aliothmoon.maameow.domain.service.UnlockAttempt
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.AsstMsg
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.manager.RemoteAccessCoordinator
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.utils.device.RomDetector
import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.KeyValueCollectionBehavior
import io.sentry.Sentry
import io.sentry.SentryAttributes
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.android.core.SentryAndroid
import io.sentry.logger.SentryLogParameters
import io.sentry.protocol.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sentry 遥测，沿用 MaaFwApp `TelemetryController`，事件模型按 MaaCore 的回调重写
 *
 * 构建没带 DSN 或用户开关关着就不初始化
 * 上报面见 docs/zh-cn/develop/TELEMETRY.md，改动时两边一起改
 */
class TelemetryController(
    private val context: Context,
    private val settings: AppSettingsManager,
    private val notificationSettings: NotificationSettingsManager,
    private val pathConfig: MaaPathConfig,
    private val taskChainState: TaskChainState,
    private val screenPower: ScreenPowerController,
) : RunTelemetry {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    /** 只在 [lock] 里改；回调热路径上先不加锁看一眼，没开就什么都不做 */
    @Volatile
    private var active = false

    /** 一轮只报一次整轮级别的故障：连接层报错与随后的启动失败往往是同一件事 */
    private var runIncidentReported = false

    /** 写到 Scope 上的环境 tag 留一份，任务 Span 开跑时抄过去 */
    private val envTags = ConcurrentHashMap<String, String>()

    private val tracer = RunTracer(
        startTransaction = { name, op -> Sentry.startTransaction(name, op) },
        environment = { envTags.toMap() },
        onTaskStarted = { reporter.onTaskStarted() },
        onTaskFailure = { reporter.report(it) },
    )

    private val reporter = IncidentReporter(
        scope = scope,
        io = Dispatchers.IO,
        store = { evidenceStore },
        secrets = ::secrets,
        encodeImage = ::encodeJpeg,
        imagesAllowed = { settings.runMode.value == RunMode.BACKGROUND },
        attachmentSampleRate = failureAttachmentSampleRate(BuildConfig.VERSION_NAME, BuildConfig.DEBUG),
        send = ::send,
    )

    private val evidenceStore: EvidenceStore by lazy {
        RoutingEvidenceStore(
            local = LocalEvidenceStore(File(pathConfig.debugDir), File(pathConfig.coreDebugDir)),
            remote = RemoteEvidenceStore { RemoteServiceManager.getInstanceOrNull() },
            coreSeparated = pathConfig.isCoreSeparated,
        )
    }

    private val forensics by lazy { ServiceDeathForensics(ServiceDeathForensics.PrefsStore(context)) }

    /** 取值不随开关变，重新初始化不必再查一遍 ActivityManager */
    private val hardware by lazy { TelemetryHardware.collect(context) }

    /** 须在设置读完盘之后调：读盘前开关还是默认值，照着初始化会绕过用户的关闭 */
    fun setup() {
        if (BuildConfig.SENTRY_DSN.isBlank()) {
            Timber.i("[telemetry] 构建未带 DSN，遥测不启用")
            return
        }
        scope.launch {
            // 开关关着也记：用户反馈问题时可凭这行在后台按 user.id 定位
            Timber.i("[telemetry] 匿名设备 ID (Sentry user.id) = %s", TelemetryUserId.get(context))
            settings.telemetryEnabled.collect(::reconfigure)
        }
        scope.launch {
            RemoteServiceManager.state
                .filterIsInstance<RemoteServiceManager.ServiceState.Connected>()
                .collect { collectDeathForensics() }
        }
    }

    override fun onRunStarted(kind: RunKind, tasks: List<MaaTaskParams>): String? {
        if (!active) return null
        val runId = UUID.randomUUID().toString()
        val begun = guarded {
            if (!active) return@guarded false
            runIncidentReported = false
            tracer.begin(runId, kind.value, tasks.map { it.type.value })
            reporter.onRunStarted()
            true
        }
        if (begun != true) return null
        // 前台模式不建屏，别带上一轮的档位
        clearEnvTag(VD_TIER_TAG)
        // 调用方可能在主线程
        scope.launch { runCatching { refreshRunTags() } }
        return runId
    }

    override fun onTaskRegistered(taskId: Int, params: String) {
        if (!active) return
        val summary = TaskParamSummary.summarize(params)
        guarded { if (active) tracer.register(taskId, summary) }
    }

    override fun onVirtualDisplayStarted(vdm: Boolean, flagTier: Int) {
        if (!active) return
        setEnvTag(VD_BACKEND_TAG, if (vdm) "vdm" else "legacy")
        setEnvTag(VD_TIER_TAG, flagTier.toString())
    }

    override fun onCallback(message: AsstMsg, details: JSONObject?) {
        // SubTaskStart / SubTaskExtraInfo 是高频回调，先比消息类型再谈别的
        if (!active || message !in TRACKED_MESSAGES) return
        guarded { if (active) tracer.onCallback(message, details) }
    }

    override fun onStartFailed(code: String, cause: Throwable?) {
        if (!active || code !in REPORTED_START_FAILURES) return
        reportRunIncident(SpanStatus.INTERNAL_ERROR) {
            StartFailure(
                runId = tracer.runId,
                code = code,
                what = cause?.javaClass?.simpleName,
                cause = cause?.toString(),
                tags = tracer.tags,
            )
        }
    }

    override fun onServiceDied(state: MaaExecutionState) {
        // 空闲期死掉多半是用户停了 Shizuku，不算故障
        if (!active || state !in RUNNING_STATES) return
        val serviceState = when (RemoteServiceManager.state.value) {
            is RemoteServiceManager.ServiceState.Died -> "died"
            is RemoteServiceManager.ServiceState.Error -> "error"
            else -> "unknown"
        }
        reportRunIncident(SpanStatus.ABORTED) {
            ServiceDeath(
                runId = tracer.runId,
                state = state.name.lowercase(),
                serviceState = serviceState,
                backend = RemoteAccessCoordinator.configuredBackend().name.lowercase(),
                taskChain = tracer.currentTaskChain,
                tags = tracer.tags,
                // 进程被替换也会走到这，那时它没死
                servicePid = RemoteServiceManager.lastServicePid.takeIf { serviceState == "died" },
                diedAtMs = System.currentTimeMillis(),
            )
        }
    }

    override fun onLaunchFinished(outcome: LaunchOutcome) {
        if (!active || outcome.result !in REPORTED_LAUNCH_RESULTS) return
        // 没走到开跑，tag 还是上次刷的；取证要等半秒，赶得上
        scope.launch { runCatching { refreshRunTags() } }
        // 不动追踪状态：这时任务还没开跑，没有哪一轮可收
        // 后端多半是根因
        val shizuku = RemoteAccessCoordinator.configuredBackend() == RemoteBackend.SHIZUKU
        guarded {
            if (!active) return@guarded
            reporter.report(
                LaunchFailure(
                    result = outcome.result.name.lowercase(),
                    launchReason = outcome.reason,
                    message = outcome.message,
                    delayMs = outcome.delayMs,
                    logFile = outcome.logFile,
                    tags = mapOf("run_mode" to outcome.runMode.lowercase()) +
                            (if (shizuku) TelemetryShizuku.tags(context) else emptyMap()) +
                            outcome.unlock?.let(::unlockTags).orEmpty(),
                    extras = if (shizuku) TelemetryShizuku.extras() else emptyMap(),
                    withBootLogs = outcome.result == ExecutionResult.FAILED_START,
                )
            )
        }
    }

    /** 唤醒解锁没成才有；SKIPPED_LOCKED 必然带着它 */
    private fun unlockTags(attempt: UnlockAttempt): Map<String, String> = buildMap {
        put("unlock.type", attempt.type)
        put("unlock.ready", attempt.ready.toString())
        put("unlock.result", attempt.result)
        runCatching { context.getSystemService(KeyguardManager::class.java).isDeviceSecure }
            .onSuccess { put("keyguard.secure", it.toString()) }
    }

    override fun onShizukuDown(afterBoot: Boolean, enabledSchedules: Int) {
        if (!active) return
        scope.launch { runCatching { refreshRunTags() } }
        guarded {
            if (!active) return@guarded
            reporter.report(
                ShizukuDown(
                    afterBoot = afterBoot,
                    enabledSchedules = enabledSchedules,
                    tags = TelemetryShizuku.tags(context),
                    extras = TelemetryShizuku.extras(),
                )
            )
        }
    }

    override suspend fun onAppExit() {
        if (!active) return
        reporter.awaitIdle(INCIDENT_FLUSH_TIMEOUT_MS)
        val wasActive = synchronized(lock) { active.also { active = false } }
        if (wasActive) withContext(Dispatchers.IO) { closeSentry() }
    }

    private fun closeSentry() {
        // 先正常结束 Session，否则它会被判为 abnormal，拉低 crash-free 率
        Sentry.endSession()
        Sentry.close()
    }

    /** [incident] 要在收掉这一轮之前构造，它取的是当前这轮的 ID 与 tag */
    private fun reportRunIncident(status: SpanStatus, incident: () -> Incident) {
        guarded {
            if (!active || runIncidentReported) return@guarded
            runIncidentReported = true
            val built = incident()
            tracer.abort(status)
            reporter.report(built)
        }
    }

    /** 在锁里改追踪状态；遥测自己出错不能连累启动流程和回调分发 */
    private fun <T> guarded(block: () -> T): T? = try {
        synchronized(lock, block)
    } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        Timber.w(e, "[telemetry] 处理失败")
        null
    }

    /** 由 [reporter] 在后台取完证之后调用，不在锁里 */
    private fun send(incident: Incident, evidence: Evidence) {
        val event = incident.toSentryEvent().apply { setEvidence(evidence) }
        val logContext = event.diagnosticLogContext(incident)
        evidence.logs?.let { emitLogs(it.entries, logContext) }
        val attachments = (evidence.attachment as? AttachmentOutcome.Attached)?.images.orEmpty()
            .map { Attachment(it.bytes, it.filename, JPEG_CONTENT_TYPE) }
        val eventId = if (attachments.isEmpty()) {
            Sentry.captureEvent(event)
        } else {
            Sentry.captureEvent(event, Hint.withAttachments(attachments))
        }
        Timber.i("[telemetry] %s event_id=%s run_id=%s", incident.reason, eventId, incident.runId)
        if (incident is ServiceDeath && incident.servicePid != null) {
            forensics.remember(
                ServiceDeathForensics.Pending(
                    eventId = logContext.eventId,
                    reason = logContext.reason,
                    runId = logContext.runId,
                    attributes = logContext.attributes,
                    traceId = logContext.traceId,
                    spanId = logContext.spanId,
                    diedAtMs = incident.diedAtMs,
                    pid = incident.servicePid,
                )
            )
            // 这时多半已重连上，不必等下次连接
            collectDeathForensics()
        }
    }

    private fun emitLogs(entries: List<DiagnosticLog>, logContext: DiagnosticLogContext) {
        entries.asSequence().flatMap { it.toRecords(logContext) }.forEach { record ->
            // 不传 args：日志正文里的 % 不能被当成格式串
            Sentry.logger().log(
                record.level,
                SentryLogParameters.create(SentryAttributes.fromMap(record.attributes)),
                record.body,
            )
        }
    }

    private fun collectDeathForensics() {
        val service = RemoteServiceManager.getInstanceOrNull() ?: return
        scope.launch {
            if (!active) return@launch
            val pending = forensics.take(runCatching { service.pid() }.getOrNull()) ?: return@launch
            val content = runCatching {
                service.dumpSystemLog(pending.sinceMs, pending.untilMs, pending.pid, "${context.packageName}:")
            }.getOrElse { "# dump failed: $it" }
            val redacted = SecretRedaction.redact(content, SecretRedaction.redactable(secrets()))
            emitLogs(
                listOf(DiagnosticLog(SYSTEM_LOG_SOURCE, kind = "system", redacted, content.encodeToByteArray().size.toLong())),
                pending.logContext(delayMs = System.currentTimeMillis() - pending.diedAtMs),
            )
            Timber.i("[telemetry] service death forensics sent, event_id=%s", pending.eventId)
        }
    }

    /**
     * 锁只护 [active] 与追踪状态的切换，Sentry 的收尾与初始化放在锁外，不让回调线程跟着等
     *
     * 只由 [setup] 里那条 collect 串行调用，不会并发
     */
    private fun reconfigure(enabled: Boolean) {
        val wasActive = synchronized(lock) {
            tracer.reset()
            reporter.cancelAll()
            active.also { active = false }
        }
        if (wasActive) closeSentry()
        if (!enabled) return
        runCatching { init() }
            .onFailure { Timber.w(it, "[telemetry] 初始化失败") }
            .onSuccess {
                synchronized(lock) { active = true }
                // 冷启动时提权进程常先于遥测连上
                collectDeathForensics()
            }
    }

    private fun init() {
        SentryAndroid.init(context) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = ENVIRONMENT
            options.tracesSampleRate = TRACES_SAMPLE_RATE
            // 替代已弃用的 sendDefaultPii = false；配了任一项，没配的就回落到 Sentry 默认（多为开），所以逐项关掉
            options.dataCollection.apply {
                userInfo = false
                filePaths = false
                databaseQueryData = false
                cookies = KeyValueCollectionBehavior.off()
                urlQueryParams = KeyValueCollectionBehavior.off()
                httpBodies = emptySet()
                httpHeaders.request = KeyValueCollectionBehavior.off()
                httpHeaders.response = KeyValueCollectionBehavior.off()
                graphql.document = false
                graphql.variables = false
            }
            // 只有出事时那份日志尾巴走 Sentry Logs，App 平时的日志不往这里写
            options.logs.isEnabled = true
            options.logs.beforeSend = SentryOptions.Logs.BeforeSendLogCallback { it.apply { bindDiagnosticTrace() } }
            // Session（Release Health）开着，日活与 crash-free 率靠它
            options.isEnableAutoSessionTracking = true
            // 自动采集只留未捕获异常与 ANR，其余全部关掉
            options.isAnrEnabled = true
            options.beforeSend = SentryOptions.BeforeSendCallback { event, hint ->
                runCatching { TelemetryAnr.enrich(context, event, hint) }
                    .onFailure { Timber.w(it, "[telemetry] ANR 原因补充失败") }
                event
            }
            // 不关的话每条消息事件都会附上发送线程的调用栈
            options.isAttachStacktrace = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableUserInteractionTracing = false
            options.isEnableActivityLifecycleBreadcrumbs = false
            options.isEnableAutoActivityLifecycleTracing = false
            options.isEnableAppLifecycleBreadcrumbs = false
            options.isEnableAppComponentBreadcrumbs = false
            options.isEnableSystemEventBreadcrumbs = false
            options.isEnableNetworkEventBreadcrumbs = false
        }
        Sentry.setUser(User().apply { id = TelemetryUserId.get(context) })
        setEnvTag("build_type", BuildConfig.BUILD_TYPE)
        setEnvTag("maacore.version", MaaCoreVersion.current.ifBlank { "unknown" })
        val rom = RomDetector.current
        setEnvTag("rom", rom.name)
        if (rom.version.isNotBlank()) setEnvTag("rom.version", rom.version)
        Sentry.configureScope { it.setContexts("hardware", hardware) }
        refreshRunTags()
    }

    private fun refreshRunTags() {
        TelemetryRunTags.collect(context, settings, taskChainState, pathConfig, screenPower.poweredOff)
            .forEach { (key, value) -> setEnvTag(key, value) }
    }

    private fun setEnvTag(key: String, value: String) {
        Sentry.setTag(key, value)
        envTags[key] = value
    }

    private fun clearEnvTag(key: String) {
        Sentry.removeTag(key)
        envTags.remove(key)
    }

    private suspend fun secrets(): Collection<String> = TelemetrySecrets.collect(
        app = settings.settings.first(),
        notification = notificationSettings.settings.first(),
        // 当前配置档的链以内存里的为准，存盘的那份可能还没跟上
        chains = taskChainState.profiles.value.map { it.chain } + listOf(taskChainState.chain.value),
    )

    /** Core 存的是 PNG，一张一兆上下，缩小并压成 JPEG 再带走 */
    private fun encodeJpeg(bytes: ByteArray): ByteArray? = runCatching {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        try {
            val (width, height) = TaskEvidence.scaledImageSize(source.width, source.height)
            // 尺寸没变时返回的就是 source 本身
            val scaled = Bitmap.createScaledBitmap(source, width, height, true)
            try {
                ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                    .toByteArray()
            } finally {
                if (scaled !== source) scaled.recycle()
            }
        } finally {
            source.recycle()
        }
    }.getOrNull()

    private companion object {
        /** 只按构建类型分；alpha / beta / 正式版靠 release 筛，不再跟用户的更新渠道设置走 */
        val ENVIRONMENT = if (BuildConfig.DEBUG) "dev" else "stable"
        const val JPEG_CONTENT_TYPE = "image/jpeg"

        const val SYSTEM_LOG_SOURCE = "logcat/system"
        const val JPEG_QUALITY = 60

        const val VD_TIER_TAG = "vd.tier"
        const val VD_BACKEND_TAG = "vd.backend"

        /** 一轮一条事务、每条任务链一条 Span，量不大，全采 */
        const val TRACES_SAMPLE_RATE = 1.0

        const val INCIDENT_FLUSH_TIMEOUT_MS = 10_000L

        val TRACKED_MESSAGES = setOf(
            AsstMsg.TaskChainStart,
            AsstMsg.TaskChainError,
            AsstMsg.TaskChainCompleted,
            AsstMsg.TaskChainStopped,
            AsstMsg.SubTaskError,
            AsstMsg.AllTasksCompleted,
            AsstMsg.Destroyed,
        )

        val RUNNING_STATES = setOf(
            MaaExecutionState.STARTING,
            MaaExecutionState.RUNNING,
            MaaExecutionState.STOPPING,
        )

        /** 正忙跳过与用户取消是正常结果，不报 */
        val REPORTED_LAUNCH_RESULTS = setOf(
            ExecutionResult.FAILED_VALIDATION,
            ExecutionResult.FAILED_START,
            ExecutionResult.FAILED_UI_LAUNCH,
            ExecutionResult.SKIPPED_LOCKED,
        )

        /**
         * 算故障的启动失败，取值同 MaaCompositionService 里的会话状态码
         *
         * 横屏、分辨率、后端没授权、服务还在连这几种是用户侧条件，不报
         */
        val REPORTED_START_FAILURES = setOf(
            "RESOURCE_ERROR",
            "CORE_DATA_PUSH_ERROR",
            "REMOTE_ACCESS_UNAVAILABLE",
            "CREATE_INSTANCE_ERROR",
            "SET_TOUCH_MODE_ERROR",
            "DISPLAY_MODE_ERROR",
            "VIRTUAL_DISPLAY_ERROR",
            "VDM_DISPLAY_ERROR",
            "MAA_CONNECT_ERROR",
            "START_ERROR",
        )
    }
}
