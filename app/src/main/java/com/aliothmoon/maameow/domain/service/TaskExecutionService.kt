package com.aliothmoon.maameow.domain.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.notification.LiveCategory
import com.aliothmoon.maameow.domain.notification.LiveNotifyIds
import com.aliothmoon.maameow.domain.notification.LiveSession
import com.aliothmoon.maameow.domain.notification.LiveSessionCoordinator
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.callback.TaskChainStatusTracker
import com.aliothmoon.maameow.maa.callback.TaskRunInfo
import com.aliothmoon.maameow.maa.callback.TaskRunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

class TaskExecutionService : Service() {

    companion object {
        private const val MIN_UPDATE_INTERVAL_MS = 1000L

        private val VISIBLE_TASK_TITLE_RES = mapOf(
            "Fight" to R.string.maa_fight,
            "Recruit" to R.string.maa_recruit,
            "Infrast" to R.string.maa_infrast,
            "Mall" to R.string.maa_mall,
            "Award" to R.string.maa_award,
            "Roguelike" to R.string.maa_roguelike,
            "Copilot" to R.string.maa_copilot,
            "SSSCopilot" to R.string.maa_sss_copilot,
            "ParadoxCopilot" to R.string.maa_paradox_copilot,
            "Reclamation" to R.string.maa_reclamation,
            "Custom" to R.string.maa_custom,
            "CloseDown" to R.string.maa_close_down,
            "StartUp" to R.string.maa_start_up,
            "Depot" to R.string.maa_depot,
            "OperBox" to R.string.maa_oper_box,
        )

        // 只提供 start 不提供外部 stop：startForegroundService 后若 stopService
        // 抢在服务创建前到达，系统会因 startForeground 未调用直接杀进程；
        // 终态退出由服务观察状态流自行 stopSelf 完成
        fun start(context: Context) {
            val intent = Intent(context, TaskExecutionService::class.java)
            context.startForegroundService(intent)
        }
    }

    private val compositionService: MaaCompositionService by inject()
    private val sessionLogger: MaaSessionLogger by inject()
    private val taskChainStatusTracker: TaskChainStatusTracker by inject()
    private val liveCoordinator: LiveSessionCoordinator by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null
    private var boundToken: Long = 0L
    private var observeToken: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        bindToken()
        // 必须先 startForeground，再做任何可能读到 IDLE/ERROR 并 stop 的逻辑，
        // 避免与 Composition 快速失败/stopService 竞态触发
        // ForegroundServiceDidNotStartInTimeException。
        // postForegroundNotification 只负责构建+notify，耗时的断网闸门已在 STARTING 时拿过
        val initial = currentSnapshot()
        startAsForeground(postForegroundNotification(initial, firstFloat = true))
        if (isTerminal(initial.state)) {
            handleTerminalState(boundToken, initial)
            return
        }
        ensureObserveProgress()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        bindToken()
        // 系统可能只走 onStartCommand；再保证一次 FGS 提升
        // onCreate 若因终态提前 return 未启动观察，随后竞态进入 STARTING 时须在此补上
        val snapshot = currentSnapshot()
        startAsForeground(postForegroundNotification(snapshot, firstFloat = false))
        if (isTerminal(snapshot.state)) {
            handleTerminalState(boundToken, snapshot)
        } else {
            ensureObserveProgress()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // 系统侧终止与 StateFlow 收集存在竞态；此处兜底确保 Live Update 通知被清除。
        // observeProgress 的 collector 由 serviceScope.cancel() 结构化取消
        progressJob = null
        removeActiveNotification(boundToken)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun bindToken() {
        boundToken = liveCoordinator.currentToken()
    }

    private fun currentSnapshot(): TaskNotificationSnapshot = TaskNotificationSnapshot(
        state = compositionService.state.value,
        statusText = sessionLogger.logs.value.lastOrNull()?.content,
        tasks = taskChainStatusTracker.tasks.value,
    )

    private fun ensureObserveProgress() {
        if (progressJob?.isActive == true && observeToken == boundToken) return
        progressJob?.cancel()
        observeToken = boundToken
        val token = observeToken
        progressJob = serviceScope.launch {
            var lastUpdateTime = 0L
            var pending: TaskNotificationSnapshot? = null
            var scheduledJob: Job? = null

            fun resetSchedule() {
                scheduledJob?.cancel()
                scheduledJob = null
                pending = null
            }

            fun forceUpdate(snapshot: TaskNotificationSnapshot) {
                resetSchedule()
                lastUpdateTime = SystemClock.elapsedRealtime()
                updateNotification(token, snapshot)
            }

            fun throttledUpdate(snapshot: TaskNotificationSnapshot) {
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastUpdateTime
                if (elapsed >= MIN_UPDATE_INTERVAL_MS) {
                    lastUpdateTime = now
                    updateNotification(token, snapshot)
                    return
                }
                pending = snapshot
                if (scheduledJob?.isActive == true) return
                scheduledJob = launch {
                    delay((MIN_UPDATE_INTERVAL_MS - elapsed).coerceAtLeast(0L))
                    pending?.let {
                        lastUpdateTime = SystemClock.elapsedRealtime()
                        updateNotification(token, it)
                    }
                    pending = null
                    scheduledJob = null
                }
            }

            // 三个数据源合并为单一 Snapshot 流，distinctUntilChanged 避免无意义刷新
            combine(
                compositionService.state,
                taskChainStatusTracker.tasks,
                sessionLogger.logs
                    .map { it.lastOrNull()?.content }
                    .distinctUntilChanged(),
            ) { state, tasks, lastLog ->
                TaskNotificationSnapshot(state, lastLog, tasks)
            }
                .distinctUntilChanged()
                .collect { snapshot ->
                    when (snapshot.state) {
                        MaaExecutionState.IDLE,
                        MaaExecutionState.ERROR -> {
                            resetSchedule()
                            handleTerminalState(token, snapshot)
                        }

                        MaaExecutionState.STARTING -> forceUpdate(
                            snapshot.copy(
                                statusText = getString(R.string.notification_task_starting)
                            )
                        )

                        MaaExecutionState.STOPPING -> forceUpdate(
                            snapshot.copy(
                                statusText = getString(R.string.notification_task_stopping)
                            )
                        )

                        MaaExecutionState.RUNNING -> throttledUpdate(
                            snapshot.copy(
                                statusText = snapshot.statusText
                                    ?: getString(R.string.notification_task_running)
                            )
                        )
                    }
                }
        }
    }

    private fun handleTerminalState(token: Long, snapshot: TaskNotificationSnapshot) {
        if (!liveCoordinator.isCurrent(token)) return
        if (!isTerminal(compositionService.state.value)) return
        Timber.i("TaskExecutionService: state=%s token=%s, stopping", snapshot.state, token)
        liveCoordinator.cancelProgress(token)
        clearProgressNotification()
        stopSelf()
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                LiveNotifyIds.PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(LiveNotifyIds.PROGRESS, notification)
        }
    }

    private fun postForegroundNotification(
        snapshot: TaskNotificationSnapshot,
        firstFloat: Boolean,
    ): Notification {
        val session = toSession(snapshot, firstFloat)
        return liveCoordinator.postProgressForeground(boundToken, session)
            ?: liveCoordinator.fallbackBuild(session)
    }

    private fun toSession(
        snapshot: TaskNotificationSnapshot,
        firstFloat: Boolean,
    ): LiveSession {
        val statusText = snapshot.statusText ?: defaultStatusText(snapshot.state)
        val progress = progressOf(snapshot)
        val activeName = activeTaskName(snapshot)
        val title = activeName ?: getString(R.string.notification_task_running_title)
        val label = progress.label
        val contentText = if (label != null) "$label · $statusText" else statusText
        // AOSP 胶囊只有一个字符串：任务名在前，系统从尾部截断时先保住它
        // 不预截断（8 字上限只是超级岛摘要态的要求）；任务链未登记时留空，由下游决定不设胶囊
        val capsule = listOfNotNull(activeName, label).joinToString(" ")
        return LiveSession(
            sessionId = LiveNotifyIds.PROGRESS_SESSION,
            category = LiveCategory.PROGRESS,
            title = title,
            text = contentText,
            capsuleText = capsule,
            progressCurrent = progress.current,
            progressMax = progress.max,
            progressLabel = progress.label,
            ongoing = true,
            firstFloat = firstFloat,
            timeoutSec = 86_400,
            isError = snapshot.state == MaaExecutionState.ERROR || progress.hasTaskError,
        )
    }

    private fun defaultStatusText(state: MaaExecutionState): String = when (state) {
        MaaExecutionState.STARTING -> getString(R.string.notification_task_starting)
        MaaExecutionState.STOPPING -> getString(R.string.notification_task_stopping)
        MaaExecutionState.RUNNING -> getString(R.string.notification_task_running)
        MaaExecutionState.IDLE -> getString(R.string.notification_task_completed)
        MaaExecutionState.ERROR -> getString(R.string.notification_task_error)
    }

    private fun progressOf(snapshot: TaskNotificationSnapshot): ProgressNumbers {
        val tasks = snapshot.tasks
        val total = tasks.size
        // 任务链还没登记时 max=0，交给下游渲染成不确定态而不是停在 0%
        if (total == 0) {
            return ProgressNumbers(0, 0, null, hasTaskError = false)
        }
        val completedCount = tasks.count { it.status == TaskRunStatus.COMPLETED }
        val activeIndex = tasks.indexOfFirst { it.status == TaskRunStatus.IN_PROGRESS }
            .takeIf { it >= 0 }
        val taskErrorIndex = tasks.indexOfFirst { it.status == TaskRunStatus.ERROR }
            .takeIf { it >= 0 }

        fun progressFor(finishedCount: Int): Int =
            (finishedCount.toLong() * LiveNotifyIds.PROGRESS_STYLE_MAX / total).toInt()

        val current = when {
            taskErrorIndex != null -> progressFor(taskErrorIndex + 1)
            snapshot.state == MaaExecutionState.ERROR -> progressFor(completedCount)
            snapshot.state == MaaExecutionState.IDLE -> LiveNotifyIds.PROGRESS_STYLE_MAX
            snapshot.state == MaaExecutionState.STOPPING -> {
                val idx = completedCount.coerceAtLeast(activeIndex ?: completedCount)
                    .coerceIn(0, total)
                progressFor(idx)
            }

            activeIndex != null -> {
                progressFor(activeIndex) + (LiveNotifyIds.PROGRESS_STYLE_MAX / total) / 2
            }

            completedCount > 0 -> progressFor(completedCount)
            else -> 0
        }.coerceIn(0, LiveNotifyIds.PROGRESS_STYLE_MAX)

        return ProgressNumbers(
            current = current,
            max = LiveNotifyIds.PROGRESS_STYLE_MAX,
            label = "$completedCount/$total",
            hasTaskError = taskErrorIndex != null,
        )
    }

    private fun activeTaskName(snapshot: TaskNotificationSnapshot): String? =
        snapshot.tasks
            .firstOrNull { it.status == TaskRunStatus.IN_PROGRESS }
            ?.taskChain
            ?.trim()
            ?.let { taskChain ->
                // 未映射的任务类型回退原始名，避免进度数字失去任务名参照
                VISIBLE_TASK_TITLE_RES[taskChain]?.let(::getString) ?: taskChain
            }

    private fun updateNotification(token: Long, snapshot: TaskNotificationSnapshot) {
        if (!liveCoordinator.isCurrent(token)) return
        liveCoordinator.publishProgress(token, toSession(snapshot, firstFloat = false))
    }

    private fun removeActiveNotification(token: Long) {
        if (liveCoordinator.isCurrent(token)) {
            liveCoordinator.cancelProgress(token)
        }
        clearProgressNotification()
    }

    private fun clearProgressNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.cancel(LiveNotifyIds.PROGRESS)
        } catch (e: SecurityException) {
            Timber.w(e, "cancel blocked by POST_NOTIFICATIONS denial")
        }
    }

    private fun isTerminal(state: MaaExecutionState): Boolean =
        state == MaaExecutionState.IDLE || state == MaaExecutionState.ERROR

    private data class TaskNotificationSnapshot(
        val state: MaaExecutionState,
        val statusText: String?,
        val tasks: List<TaskRunInfo>,
    )

    private data class ProgressNumbers(
        val current: Int,
        val max: Int,
        val label: String?,
        /** 任务链里有子任务失败，进度条与强调色转红，即使整条链还在继续 */
        val hasTaskError: Boolean,
    )
}
