package com.aliothmoon.maameow.web.api

import android.os.Build
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.launch.LaunchPipeline
import com.aliothmoon.maameow.domain.launch.LaunchSession
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.maa.callback.TaskChainStatusTracker
import com.aliothmoon.maameow.maa.callback.TaskRunInfo
import com.aliothmoon.maameow.maa.callback.TaskRunStatus
import com.aliothmoon.maameow.manager.RemoteServiceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** 网页端能看到的全部状态 */
interface WebUiBackend {
    val app: WebAppInfo
    val status: Flow<WebStatus>
    val chain: Flow<WebChain>
    val logs: StateFlow<List<LogItem>>
}

class WebStateSource(
    composition: MaaCompositionService,
    launchPipeline: LaunchPipeline,
    appSettings: AppSettingsManager,
    chainState: TaskChainState,
    statusTracker: TaskChainStatusTracker,
    sessionLogger: MaaSessionLogger,
) : WebUiBackend {

    override val app = WebAppInfo(
        version = BuildConfig.VERSION_NAME,
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
    )

    override val status: Flow<WebStatus> = combine(
        composition.state,
        RemoteServiceManager.state,
        appSettings.runMode,
        launchPipeline.session,
        composition.runDeadline,
    ) { execution, backend, runMode, session, deadline ->
        WebStatus(
            execution = execution.name,
            backend = backend.toWeb(),
            runMode = runMode.name,
            launch = session.toWeb(),
            runDeadline = deadline,
        )
    }.distinctUntilChanged()

    override val chain: Flow<WebChain> = combine(
        chainState.chain,
        chainState.profiles,
        chainState.profileId,
        statusTracker.tasks,
    ) { nodes, profiles, profileId, runs ->
        val runByNode = runs.groupBy { it.slot?.nodeId }
        WebChain(
            profileId = profileId,
            profileName = profiles.firstOrNull { it.id == profileId }?.name.orEmpty(),
            nodes = nodes.sortedBy { it.order }.map { it.toWeb(runByNode[it.id]) },
        )
    }.distinctUntilChanged()

    override val logs: StateFlow<List<LogItem>> = sessionLogger.logs

    private companion object {
        val typeByConfig by lazy { TaskTypeInfo.entries.associateBy { it.defaultConfig()::class } }

        fun TaskChainNode.toWeb(runs: List<TaskRunInfo>?) = WebChainNode(
            id = id,
            name = name,
            enabled = enabled,
            type = typeByConfig[config::class]?.name,
            run = runs?.let(::aggregate)?.name,
        )

        // 一个节点可能拆成多个 Core 任务
        fun aggregate(runs: List<TaskRunInfo>): TaskRunStatus = when {
            runs.any { it.status == TaskRunStatus.IN_PROGRESS } -> TaskRunStatus.IN_PROGRESS
            runs.any { it.status == TaskRunStatus.ERROR } -> TaskRunStatus.ERROR
            runs.all { it.status == TaskRunStatus.COMPLETED } -> TaskRunStatus.COMPLETED
            runs.any { it.status == TaskRunStatus.COMPLETED } -> TaskRunStatus.IN_PROGRESS
            else -> TaskRunStatus.PENDING
        }

        // 不用 simpleName：release 混淆后类名会变
        fun RemoteServiceManager.ServiceState.toWeb(): String = when (this) {
            RemoteServiceManager.ServiceState.Disconnected -> "Disconnected"
            RemoteServiceManager.ServiceState.Connecting -> "Connecting"
            RemoteServiceManager.ServiceState.Died -> "Died"
            is RemoteServiceManager.ServiceState.Connected -> "Connected"
            is RemoteServiceManager.ServiceState.Error -> "Error"
        }

        fun LaunchSession.Phase.toWeb(): String = when (this) {
            LaunchSession.Phase.DevicePrep -> "DevicePrep"
            is LaunchSession.Phase.Counting -> "Counting"
            LaunchSession.Phase.Preparing -> "Preparing"
            LaunchSession.Phase.Starting -> "Starting"
        }

        fun LaunchSession.toWeb(): WebLaunch? = when (this) {
            LaunchSession.Idle -> null
            is LaunchSession.InFlight -> WebLaunch(
                name = request.displayName,
                phase = phase.toWeb(),
                remainingSeconds = (phase as? LaunchSession.Phase.Counting)?.remainingSeconds,
            )
        }
    }
}

fun LogItem.toWeb() = WebLogItem(
    id = id,
    time = timestampMillis,
    content = content,
    level = level.name,
    color = level.colorRole.name,
    tooltip = tooltip,
)
