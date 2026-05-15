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
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

class TaskExecutionService : Service() {

    companion object {
        private const val CHANNEL_ID = "task_execution"
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

    private fun observeProgress() {
        observeJob = serviceScope.launch {
            var lastUpdateTime = 0L
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
            }.collectLatest { snapshot ->
                val state = snapshot.state
                val latestLog = snapshot.statusText
                when (state) {
                    MaaExecutionState.IDLE, MaaExecutionState.ERROR -> {
                        Timber.i("TaskExecutionService: state=$state, stopping")
                        val title = getString(
                            if (state == MaaExecutionState.IDLE) R.string.notification_task_completed
                            else R.string.notification_task_error
                        )
                        showResultNotification(title, latestLog ?: "")
                        stopSelf()
                    }

                    MaaExecutionState.STARTING -> {
                        updateNotification(
                            snapshot.copy(statusText = getString(R.string.notification_task_starting))
                        )
                    }

                    MaaExecutionState.STOPPING -> {
                        updateNotification(
                            snapshot.copy(statusText = getString(R.string.notification_task_stopping))
                        )
                    }

                    MaaExecutionState.RUNNING -> {
                        val runningSnapshot = snapshot.copy(
                            statusText = latestLog ?: getString(R.string.notification_task_running)
                        )
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= MIN_UPDATE_INTERVAL_MS) {
                            lastUpdateTime = now
                            updateNotification(runningSnapshot)
                        } else {
                            delay(MIN_UPDATE_INTERVAL_MS - (now - lastUpdateTime))
                            lastUpdateTime = System.currentTimeMillis()
                            updateNotification(runningSnapshot)
                        }
                    }
                }
            }
        }
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_task_execution),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_task_execution_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
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

    private fun buildNotification(snapshot: TaskNotificationSnapshot): Notification {
        val statusText = snapshot.statusText ?: when (snapshot.state) {
            MaaExecutionState.STARTING -> getString(R.string.notification_task_starting)
            MaaExecutionState.STOPPING -> getString(R.string.notification_task_stopping)
            MaaExecutionState.RUNNING -> getString(R.string.notification_task_running)
            MaaExecutionState.IDLE -> getString(R.string.notification_task_completed)
            MaaExecutionState.ERROR -> getString(R.string.notification_task_error)
        }
        val progressInfo = buildProgressInfo(snapshot)
        val contentText = buildContentText(statusText, progressInfo)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            buildProgressStyleNotification(statusText, contentText, progressInfo)
        } else {
            buildCompatProgressNotification(statusText, contentText, progressInfo)
        }
    }

    private fun buildProgressStyleNotification(
        statusText: String,
        contentText: String,
        progressInfo: TaskProgressInfo,
    ): Notification {
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressIndeterminate(progressInfo.totalCount == 0)
            .setProgressTrackerIcon(Icon.createWithResource(this, R.drawable.point_yu))

        if (progressInfo.totalCount > 0) {
            style.setProgress(progressInfo.progress)
        }

        progressInfo.segments.forEach { segment ->
            style.addProgressSegment(
                Notification.ProgressStyle.Segment(segment.length).setColor(segment.color)
            )
        }
        progressInfo.points.forEach { point ->
            style.addProgressPoint(
                Notification.ProgressStyle.Point(point.position).setColor(point.color)
            )
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_task_running_title))
            .setContentText(contentText)
            .setStyle(style)
            .setContentIntent(buildContentIntent())
            .setOngoing(true)
            .apply { requestPromotedOngoingCompat(this) }
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setSubText(statusText)
            .build()
    }

    private fun buildCompatProgressNotification(
        statusText: String,
        contentText: String,
        progressInfo: TaskProgressInfo,
    ): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_task_running_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\n$statusText"))
            .setContentIntent(buildContentIntent())
            .setOngoing(true)
            .setRequestPromotedOngoing(canRequestPromotedOngoing())
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(progressInfo.max, progressInfo.progress, progressInfo.totalCount == 0)
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
                segments = listOf(ProgressSegmentInfo(PROGRESS_STYLE_MAX, PROGRESS_COLOR_PENDING)),
                points = emptyList(),
            )
        }

        val unit = PROGRESS_STYLE_MAX / total
        val remainder = PROGRESS_STYLE_MAX % total
        val segments = tasks.mapIndexed { index, task ->
            val length = unit + if (index < remainder) 1 else 0
            ProgressSegmentInfo(length, colorForStatus(task.status))
        }
        val completed = tasks.count { it.status == TaskRunStatus.COMPLETED }
        val activeIndex = tasks.indexOfFirst { it.status == TaskRunStatus.IN_PROGRESS }
            .takeIf { it >= 0 }
        val progress = when {
            snapshot.state == MaaExecutionState.ERROR || tasks.any { it.status == TaskRunStatus.ERROR } -> {
                val errorIndex = tasks.indexOfFirst { it.status == TaskRunStatus.ERROR }
                    .takeIf { it >= 0 }
                    ?: activeIndex
                    ?: completed
                segments.take(errorIndex.coerceIn(0, total)).sumOf { it.length }
            }

            snapshot.state == MaaExecutionState.IDLE -> PROGRESS_STYLE_MAX
            snapshot.state == MaaExecutionState.STOPPING -> segments.take(
                completed.coerceAtLeast(activeIndex ?: completed).coerceIn(0, total)
            ).sumOf { it.length }

            activeIndex != null -> segments.take(activeIndex).sumOf { it.length } + segments[activeIndex].length / 2
            completed > 0 -> segments.take(completed).sumOf { it.length }
            else -> 0
        }.coerceIn(0, PROGRESS_STYLE_MAX)

        return TaskProgressInfo(
            max = PROGRESS_STYLE_MAX,
            progress = progress,
            completedCount = completed,
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
            ProgressPointInfo(position, Color.WHITE)
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

    private fun requestPromotedOngoingCompat(builder: Notification.Builder) {
        if (!canRequestPromotedOngoing()) return
        runCatching {
            Notification.Builder::class.java
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
        }.onFailure {
            Timber.d(it, "Promoted ongoing notifications are not available on this SDK")
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
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun showResultNotification(title: String, text: String) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(buildContentIntent())
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(RESULT_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
