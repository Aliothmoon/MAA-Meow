package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.permission.PermissionState
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AchievementReporter(
    private val repository: AchievementRepository,
    private val appSettingsManager: AppSettingsManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskStoppedBeforeNextStart = AtomicBoolean(false)

    fun reportTaskStarted(taskCount: Int) {
        report {
            event = AchievementEvents.MISSION_STARTED
            "taskCount" to taskCount
            "runMode" to appSettingsManager.runMode.value.name
        }
    }

    fun reportTaskStarted(
        taskCount: Int,
        launchesGame: Boolean,
        gameAliveBeforeStart: Boolean? = null,
    ) {
        val startedAfterStop = taskStoppedBeforeNextStart.getAndSet(false)
        report {
            event = AchievementEvents.MISSION_STARTED
            "taskCount" to taskCount
            "runMode" to appSettingsManager.runMode.value.name
            "launchesGame" to launchesGame
            "startedAfterStop" to startedAfterStop
            if (gameAliveBeforeStart != null) {
                "gameAliveBeforeStart" to gameAliveBeforeStart
            }
        }
    }

    fun reportTaskStopped() {
        taskStoppedBeforeNextStart.set(true)
        report { event = AchievementEvents.TASK_STOPPED_BEFORE_NEXT_START }
    }

    fun reportTaskStartBlocked(reason: String) {
        report {
            event = AchievementEvents.TASK_START_BLOCKED
            "reason" to reason
        }
    }

    fun reportAllTasksCompleted(elapsedMillis: Long? = null) {
        report {
            event = AchievementEvents.ALL_TASKS_COMPLETED
            if (elapsedMillis != null) {
                "elapsedMillis" to elapsedMillis
            }
        }
    }

    fun reportScheduleResult(result: ExecutionResult) {
        report {
            event = AchievementEvents.SCHEDULE_RESULT
            "result" to result.name
        }
    }

    fun reportRemoteConnected(backend: RemoteBackend) {
        report {
            event = AchievementEvents.REMOTE_CONNECTED
            "backend" to backend.name
        }
    }

    fun reportRemoteConnectFailed(backend: RemoteBackend) {
        report {
            event = AchievementEvents.REMOTE_CONNECT_FAILED
            "backend" to backend.name
        }
    }

    fun reportPermissionState(state: PermissionState) {
        report {
            event = AchievementEvents.PERMISSION_STATE
            "allGranted" to state.allRequiredGranted
        }
    }

    fun reportNotificationProviders(enabledProviderIds: Set<String>, allProviderIds: Set<String>) {
        if (allProviderIds.isEmpty()) return
        report {
            event = AchievementEvents.NOTIFICATION_PROVIDER_STATE
            "allEnabled" to enabledProviderIds.containsAll(allProviderIds)
            "enabledCount" to enabledProviderIds.size
            "totalCount" to allProviderIds.size
        }
    }

    fun reportFeedbackGroupOpened() {
        report { event = AchievementEvents.FEEDBACK_GROUP_OPENED }
    }

    fun reportNotificationPermissionResult(granted: Boolean) {
        report {
            event = AchievementEvents.NOTIFICATION_PERMISSION_RESULT
            "granted" to granted
        }
    }

    fun reportDebugModeChanged(enabled: Boolean) {
        report {
            event = AchievementEvents.DEBUG_MODE_CHANGED
            "enabled" to enabled
        }
    }

    private fun report(block: AchievementRepository.ReportBuilder.() -> Unit) {
        scope.launch {
            repository.report(block)
        }
    }
}
