package com.aliothmoon.maameow.schedule.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.UnlockGestureStore
import com.aliothmoon.maameow.domain.models.GestureRecordResult
import com.aliothmoon.maameow.domain.models.GestureRecordStatus
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.WakeUnlockEngine
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import com.aliothmoon.maameow.schedule.service.ScheduleWakeLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class ScheduleWakeUnlockViewModel(
    private val application: Application,
    private val appSettingsManager: AppSettingsManager,
    private val wakeUnlockEngine: WakeUnlockEngine,
    private val unlockGestureStore: UnlockGestureStore,
    private val compositionService: MaaCompositionService,
    private val scheduleAlarmManager: ScheduleAlarmManager,
    private val scheduleRepository: ScheduleStrategyRepository,
) : ViewModel() {

    /** 锁屏和注入都会打断正在跑的任务 */
    private val taskActive: Boolean
        get() = compositionService.state.value.let {
            it == MaaExecutionState.STARTING ||
                    it == MaaExecutionState.RUNNING ||
                    it == MaaExecutionState.STOPPING
        }

    val wakeUnlockType: StateFlow<String> = appSettingsManager.wakeUnlockType

    fun setWakeUnlockType(type: String) {
        viewModelScope.launch { appSettingsManager.setWakeUnlockType(type) }
    }

    val wakeCredential: StateFlow<String> = appSettingsManager.wakeCredential

    private val _useAlarmClock = MutableStateFlow(scheduleAlarmManager.useAlarmClock)
    val useAlarmClock: StateFlow<Boolean> = _useAlarmClock.asStateFlow()

    /** 先同步落值，连点不乱序 */
    fun setUseAlarmClock(enabled: Boolean) {
        _useAlarmClock.value = enabled
        scheduleAlarmManager.useAlarmClock = enabled
        viewModelScope.launch(Dispatchers.IO) {
            scheduleAlarmManager.rescheduleAll(scheduleRepository.getAll())
        }
    }

    fun setWakeCredential(credential: String) {
        viewModelScope.launch { appSettingsManager.setWakeCredential(credential) }
    }

    /** null=未测试 */
    sealed interface WakeTestState {
        data object Testing : WakeTestState
        data class Done(val result: WakeUnlockEngine.WakeResult) : WakeTestState
    }

    private val _wakeTestState = MutableStateFlow<WakeTestState?>(null)
    val wakeTestState: StateFlow<WakeTestState?> = _wakeTestState.asStateFlow()

    fun runWakeTest() {
        if (_wakeTestState.value == WakeTestState.Testing) return
        if (taskActive) {
            _wakeTestState.value = WakeTestState.Done(WakeUnlockEngine.WakeResult.TASK_RUNNING)
            return
        }
        viewModelScope.launch {
            _wakeTestState.value = WakeTestState.Testing
            val type = appSettingsManager.wakeUnlockType.value
            val credential = UnlockCredential.of(
                type = type,
                pin = appSettingsManager.wakeCredential.value,
                gestureJson = if (type == UnlockCredential.TYPE_GESTURE) {
                    unlockGestureStore.readJson()
                } else {
                    ""
                },
            )
            // 测试会先息屏，没人持锁的话 CPU 可能在等待期间睡下去，解锁要拖到下次被唤醒
            val wakeLock = ScheduleWakeLock.acquire(application, TEST_WAKE_TIMEOUT_MS)
            val result = try {
                wakeUnlockEngine.testUnlock(credential)
            } finally {
                ScheduleWakeLock.release(wakeLock)
            }
            _wakeTestState.value = WakeTestState.Done(result)
        }
    }

    fun clearWakeTestResult() {
        _wakeTestState.value = null
    }

    // ───────────────── 解锁手势录制 ─────────────────

    val unlockGesture: StateFlow<UnlockGesture?> = unlockGestureStore.gesture

    /** null=空闲；录制期间 App 在锁屏后面，只能靠轮询回收结果 */
    sealed interface GestureRecordState {
        /** 已发起，等提权进程锁屏 */
        data object Preparing : GestureRecordState
        data object Recording : GestureRecordState
        data class Done(val steps: Int) : GestureRecordState
        data class Failed(val result: WakeUnlockEngine.WakeResult) : GestureRecordState
    }

    private val _gestureRecordState = MutableStateFlow<GestureRecordState?>(null)
    val gestureRecordState: StateFlow<GestureRecordState?> = _gestureRecordState.asStateFlow()

    private var recordJob: Job? = null

    fun startGestureRecord() {
        if (recordJob?.isActive == true) return
        if (taskActive) {
            _gestureRecordState.value =
                GestureRecordState.Failed(WakeUnlockEngine.WakeResult.TASK_RUNNING)
            return
        }
        recordJob = viewModelScope.launch {
            _gestureRecordState.value = GestureRecordState.Preparing
            // 同测试：锁屏到亮屏之间要持锁，录制结束或取消即释放
            val wakeLock = ScheduleWakeLock.acquire(
                application,
                (RECORD_TIMEOUT_MS + RECORD_GRACE_MS).toLong(),
            )
            try {
                if (!wakeUnlockEngine.startGestureRecord(RECORD_TIMEOUT_MS)) {
                    _gestureRecordState.value =
                        GestureRecordState.Failed(WakeUnlockEngine.WakeResult.IPC_FAILED)
                    return@launch
                }
                _gestureRecordState.value = GestureRecordState.Recording
                awaitRecordResult()
            } finally {
                ScheduleWakeLock.release(wakeLock)
            }
        }
    }

    /** 进页面时补一次：VM 若在锁屏期间被重建，轮询协程会一起没掉 */
    fun refreshGestureRecord() {
        if (recordJob?.isActive == true) return
        recordJob = viewModelScope.launch {
            val result = wakeUnlockEngine.pollGestureRecord() ?: return@launch
            if (result.status.isTerminal) {
                consume(result)
            } else if (result.status == GestureRecordStatus.RECORDING) {
                _gestureRecordState.value = GestureRecordState.Recording
                awaitRecordResult()
            }
        }
    }

    fun cancelGestureRecord() {
        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            wakeUnlockEngine.cancelGestureRecord()
            // 远端取消不留终态，本地直接收尾
            _gestureRecordState.value =
                GestureRecordState.Failed(WakeUnlockEngine.WakeResult.RECORD_CANCELLED)
        }
    }

    fun clearGestureRecordState() {
        _gestureRecordState.value = null
    }

    fun clearGesture() {
        viewModelScope.launch { unlockGestureStore.clear() }
    }

    private suspend fun awaitRecordResult() {
        val deadline = SystemClock.elapsedRealtime() + RECORD_TIMEOUT_MS + RECORD_GRACE_MS
        // 先轮询再判超时：锁屏期间进程可能被冻结，解冻后这一轮仍要能把结果取回来
        while (true) {
            delay(RECORD_POLL_INTERVAL_MS.milliseconds)
            val result = wakeUnlockEngine.pollGestureRecord()
            // IDLE：oneway 的 start 还没落地，或远端重启过，继续等而不是当成已结束
            if (result != null && result.status.isTerminal) {
                consume(result)
                return
            }
            if (SystemClock.elapsedRealtime() >= deadline) break
        }
        _gestureRecordState.value =
            GestureRecordState.Failed(WakeUnlockEngine.WakeResult.RECORD_TIMEOUT)
    }

    private suspend fun consume(result: GestureRecordResult) {
        val gesture = result.gesture
        _gestureRecordState.value = if (
            result.status == GestureRecordStatus.DONE && gesture != null
        ) {
            unlockGestureStore.save(gesture)
            GestureRecordState.Done(gesture.steps.size)
        } else {
            GestureRecordState.Failed(WakeUnlockEngine.WakeResult.fromCode(result.errorCode))
        }
    }

    private companion object {
        /** 与提权进程的录制超时对齐，留一段宽限防止两边同时判超时 */
        const val RECORD_TIMEOUT_MS = 90_000
        const val RECORD_GRACE_MS = 15_000
        const val RECORD_POLL_INTERVAL_MS = 1_000L

        /** 覆盖锁屏、息屏、亮屏与 PIN/手势注入的最长耗时 */
        const val TEST_WAKE_TIMEOUT_MS = 30_000L
    }
}
