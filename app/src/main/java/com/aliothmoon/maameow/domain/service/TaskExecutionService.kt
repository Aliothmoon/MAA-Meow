package com.aliothmoon.maameow.domain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.aliothmoon.maameow.MainActivity
import com.aliothmoon.maameow.R
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

class TaskExecutionService : Service() {

    companion object {
        private const val TASK_CHANNEL_ID = "task_execution_live"
        private const val RESULT_CHANNEL_ID = "task_execution_result"
        private const val NOTIFICATION_ID = 9003
        private const val RESULT_NOTIFICATION_ID = 9004
        private const val MIN_UPDATE_INTERVAL_MS = 1000L
        private const val PROGRESS_STYLE_MAX = 1000
        private const val PERMISSION_POST_PROMOTED_NOTIFICATIONS =
            "android.permission.POST_PROMOTED_NOTIFICATIONS"

        private const val PROGRESS_COLOR_COMPLETED = 0xFF4CAF50.toInt()
        private const val PROGRESS_COLOR_ACTIVE = 0xFF2196F3.toInt()
        private const val PROGRESS_COLOR_PENDING = 0xFF9E9E9E.toInt()
        private const val PROGRESS_COLOR_ERROR = 0xFFD32F2F.toInt()
                private val VISIBLE_TASK_TITLES = mapOf(
            "Fight" to "理智作战",
            "Recruit" to "自动公招",
            "Infrast" to "基建换班",
            "Mall" to "信用收支",
            "Award" to "领取奖励",
        )


        private val VISIBLE_TASK_TITLES = mapOf(
            "Fight" to "理智作战",
            "Recruit" to "自动公招",
            "Infrast" to "基建换班",
            "Mall" to "信用收支",
            "Award" to "领取奖励",
        )

        fun start(context: Context) {
            val intent = Intent(context, TaskExecutionService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TaskExecutionService::class.java))
        }
    }

    private val compositionService: MaaCompositionService by inject()
    private val sessionLogger: MaaSessionLogger by inject()
    private val taskChainStatusTracker: TaskChainStatusTracker by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startAsForeground(
            buildNotification(
                TaskNotificationSnapshot(
                    state = MaaExecutionState.STARTING,
                    statusText = getString(R.string.notification_task_starting),
                    tasks = emptyList(),
                )
            )
        )
        observeProgress()
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeProgress() {
        observeJob = serviceScope.launch {
            var lastUpdateTime = 0L
            var pendingRunningSnapshot: TaskNotificationSnapshot? = null
            var scheduledRunningUpdateJob: Job? = null

            fun cancelScheduledRunningUpdate() {
                scheduledRunningUpdateJob?.cancel()
                scheduledRunningUpdateJob = null
                pendingRunningSnapshot = null
            }

            fun scheduleRunningUpdate(snapshot: TaskNotificationSnapshot) {
                pendingRunningSnapshot = snapshot

                val now = System.currentTimeMillis()
                val elapsed = now - lastUpdateTime
                if (elapsed >= MIN_UPDATE_INTERVAL_MS) {
                    lastUpdateTime = now
                    val latestSnapshot = pendingRunningSnapshot ?: snapshot
                    pendingRunningSnapshot = null
                    scheduledRunningUpdateJob?.cancel()
                    scheduledRunningUpdateJob = null
                    updateNotification(latestSnapshot)
                    return
                }

                if (scheduledRunningUpdateJob?.isActive == true) {
                    return
                }

                scheduledRunningUpdateJob = serviceScope.launch {
                    delay((MIN_UPDATE_INTERVAL_MS - elapsed).coerceAtLeast(0L))
                    pendingRunningSnapshot?.let { latestSnapshot ->
                        lastUpdateTime = System.currentTimeMillis()
                        pendingRunningSnapshot = null
                        updateNotification(latestSnapshot)
                    }
                    scheduledRunningUpdateJob = null
                }
            }

            combine(
                compositionService.state,
                sessionLogger.logs,
                taskChainStatusTracker.tasks,
            ) { state, logs, tasks ->
                TaskNotificationSnapshot(
                    state = state,
                    statusText = logs.lastOrNull()?.content,
                    tasks = tasks,
                )
            }.collect { snapshot ->
                when (snapshot.state) {
                    MaaExecutionState.IDLE,
                    MaaExecutionState.ERROR,
                    -> handleTerminalState(snapshot, ::cancelScheduledRunningUpdate)

                    MaaExecutionState.STARTING -> {
                        cancelScheduledRunningUpdate()
                        updateNotification(
                            snapshot.copy(statusText = getString(R.string.notification_task_starting))
                        )
                    }

                    MaaExecutionState.STOPPING -> {
                        cancelScheduledRunningUpdate()
                        updateNotification(
                            snapshot.copy(statusText = getString(R.string.notification_task_stopping))
                        )
                    }

                    MaaExecutionState.RUNNING -> {
                        val runningSnapshot = snapshot.copy(
                            statusText = snapshot.statusText
                                ?: getString(R.string.notification_task_running)
                        )
                        scheduleRunningUpdate(runningSnapshot)
                    }
                }
            }
        }
    }

    private fun handleTerminalState(
        snapshot: TaskNotificationSnapshot,
        cancelScheduledRunningUpdate: () -> Unit,
    ) {
        cancelScheduledRunningUpdate()

        val isCompleted = snapshot.state == MaaExecutionState.IDLE
        val title = getString(
            if (isCompleted) {
                R.string.notification_task_completed
            } else {
                R.string.notification_task_error
            }
        )

        Timber.i("TaskExecutionService: state=%s, stopping", snapshot.state)
        showResultNotification(title, snapshot.statusText.orEmpty())
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelName = getString(R.string.notification_channel_task_execution)
        val channelDescription = getString(R.string.notification_channel_task_execution_desc)

        // Live updates must use DEFAULT+ importance to remain eligible for promoted ongoing
        // notifications / Live Updates on supported devices. Result notifications stay LOW
        // to reduce noise after the foreground task has ended.
        val taskChannel = NotificationChannel(
            TASK_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = channelDescription
            setShowBadge(false)
        }

        val resultChannel = NotificationChannel(
            RESULT_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = channelDescription
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(taskChannel, resultChannel))
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(snapshot: TaskNotificationSnapshot): Notification {
        val statusText = snapshot.statusText ?: defaultStatusText(snapshot.state)
        val progressInfo = buildProgressInfo(snapshot)
        val contentText = buildContentText(statusText, progressInfo)
        val title = buildNotificationTitle(snapshot)

        return buildCompatProgressNotification(
            title = title,
            statusText = statusText,
            contentText = contentText,
            progressInfo = progressInfo,
        )
    }

    private fun defaultStatusText(state: MaaExecutionState): String = when (state) {
        MaaExecutionState.STARTING -> getString(R.string.notification_task_starting)
        MaaExecutionState.STOPPING -> getString(R.string.notification_task_stopping)
        MaaExecutionState.RUNNING -> getString(R.string.notification_task_running)
        MaaExecutionState.IDLE -> getString(R.string.notification_task_completed)
        MaaExecutionState.ERROR -> getString(R.string.notification_task_error)
    }

    private fun buildCompatProgressNotification(
        title: String,
        statusText: String,
        contentText: String,
        progressInfo: TaskProgressInfo,
    ): Notification {
        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressIndeterminate(progressInfo.totalCount == 0)
            .setProgressTrackerIcon(
                IconCompat.createWithResource(this, R.drawable.ic_progress_tracker)
            )

        if (progressInfo.totalCount > 0) {
            style.setProgress(progressInfo.progress)
        }

        progressInfo.segments.forEach { segment ->
            style.addProgressSegment(
                NotificationCompat.ProgressStyle.Segment(segment.length)
                    .setColor(segment.color)
            )
        }
        progressInfo.points.forEach { point ->
            style.addProgressPoint(
                NotificationCompat.ProgressStyle.Point(point.position)
                    .setColor(point.color)
            )
        }

        return NotificationCompat.Builder(this, TASK_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(style)
            .setContentIntent(buildContentIntent())
            .setOngoing(true)
            .setRequestPromotedOngoing(canRequestPromotedOngoing())
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setSubText(statusText)
            .build()
    }

    private fun buildProgressInfo(snapshot: TaskNotificationSnapshot): TaskProgressInfo {
        val tasks = snapshot.tasks
        val total = tasks.size
        if (total == 0) {
            return TaskProgressInfo(
                max = PROGRESS_STYLE_MAX,
                progress = 0,
                completedCount = 0,
                totalCount = 0,
                segments = listOf(
                    ProgressSegmentInfo(PROGRESS_STYLE_MAX, PROGRESS_COLOR_PENDING)
                ),
                points = emptyList(),
            )
        }

        val unit = PROGRESS_STYLE_MAX / total
        val remainder = PROGRESS_STYLE_MAX % total
        val segments = tasks.mapIndexed { index, task ->
            val length = unit + if (index < remainder) 1 else 0
            ProgressSegmentInfo(length, colorForStatus(task.status))
        }

        val completedCount = tasks.count { it.status == TaskRunStatus.COMPLETED }
        val activeIndex = tasks.indexOfFirst { it.status == TaskRunStatus.IN_PROGRESS }
            .takeIf { it >= 0 }
        val taskErrorIndex = tasks.indexOfFirst { it.status == TaskRunStatus.ERROR }
            .takeIf { it >= 0 }
        val completedProgress = segments
            .take(completedCount.coerceIn(0, total))
            .sumOf { it.length }

        val progress = when {
            // Fill through the first failed task so the errored segment is always visible.
            taskErrorIndex != null -> {
                segments.take((taskErrorIndex + 1).coerceIn(0, total)).sumOf { it.length }
            }

            // If the service enters ERROR without a task-level error marker, preserve only
            // completed progress instead of implying the whole chain failed at once.
            snapshot.state == MaaExecutionState.ERROR -> completedProgress
            snapshot.state == MaaExecutionState.IDLE -> PROGRESS_STYLE_MAX
            snapshot.state == MaaExecutionState.STOPPING -> {
                segments.take(
                    completedCount.coerceAtLeast(activeIndex ?: completedCount)
                        .coerceIn(0, total)
                ).sumOf { it.length }
            }

            activeIndex != null -> {
                segments.take(activeIndex).sumOf { it.length } +
                    segments[activeIndex].length / 2
            }

            completedCount > 0 -> completedProgress
            else -> 0
        }.coerceIn(0, PROGRESS_STYLE_MAX)

        return TaskProgressInfo(
            max = PROGRESS_STYLE_MAX,
            progress = progress,
            completedCount = completedCount,
            totalCount = total,
            segments = segments,
            points = buildProgressPoints(segments),
        )
    }

    private fun buildProgressPoints(segments: List<ProgressSegmentInfo>): List<ProgressPointInfo> {
        if (segments.size <= 1) return emptyList()

        var position = 0
        return segments.dropLast(1).map { segment ->
            position += segment.length
            ProgressPointInfo(position, PROGRESS_COLOR_PENDING)
        }
    }

    private fun buildContentText(statusText: String, progressInfo: TaskProgressInfo): String {
        val progressText = if (progressInfo.totalCount > 0) {
            "${progressInfo.completedCount}/${progressInfo.totalCount}"
        } else {
            getString(R.string.notification_task_running)
        }
        return "$progressText · $statusText"
    }

    private fun buildNotificationTitle(snapshot: TaskNotificationSnapshot): String {
        val currentTaskTitle = snapshot.tasks
            .firstOrNull { it.status == TaskRunStatus.IN_PROGRESS }
            ?.taskChain
            ?.trim()
            ?.let { taskChain ->
                VISIBLE_TASK_TITLES[taskChain]
                    ?: taskChain.takeIf { it in VISIBLE_TASK_TITLES.values }
            }

        return currentTaskTitle ?: getString(R.string.notification_task_running_title)
    }

    private fun colorForStatus(status: TaskRunStatus): Int = when (status) {
        TaskRunStatus.PENDING -> PROGRESS_COLOR_PENDING
        TaskRunStatus.IN_PROGRESS -> PROGRESS_COLOR_ACTIVE
        TaskRunStatus.COMPLETED -> PROGRESS_COLOR_COMPLETED
        TaskRunStatus.ERROR -> PROGRESS_COLOR_ERROR
    }

    private fun canRequestPromotedOngoing(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            ContextCompat.checkSelfPermission(
                this,
                PERMISSION_POST_PROMOTED_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun updateNotification(snapshot: TaskNotificationSnapshot) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun showResultNotification(title: String, text: String) {
        stopForeground(STOP_FOREGROUND_REMOVE)

        val notification = NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(buildContentIntent())
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(RESULT_NOTIFICATION_ID, notification)
    }

    private data class TaskNotificationSnapshot(
        val state: MaaExecutionState,
        val statusText: String?,
        val tasks: List<TaskRunInfo>,
    )

    private data class TaskProgressInfo(
        val max: Int,
        val progress: Int,
        val completedCount: Int,
        val totalCount: Int,
        val segments: List<ProgressSegmentInfo>,
        val points: List<ProgressPointInfo>,
    )

    private data class ProgressSegmentInfo(
        val length: Int,
        val color: Int,
    )

    private data class ProgressPointInfo(
        val position: Int,
        val color: Int,
    )
}

