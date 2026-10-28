package com.aliothmoon.maameow.domain.service

import android.app.ActivityManager
import android.content.Context
import com.aliothmoon.maameow.domain.launch.LaunchMutex
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.schedule.service.ScheduleWakeLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.system.exitProcess

/** 退出应用：关 RemoteService 再关 App */
class AppTerminator(
    private val context: Context,
    private val mutex: LaunchMutex,
    private val compositionService: MaaCompositionService,
    private val externalNotification: ExternalNotificationService,
    private val telemetry: RunTelemetry,
    private val wakeUnlockEngine: WakeUnlockEngine,
    private val screenPower: ScreenPowerController,
) {

    /** [sleep] 为真时关服务前锁屏休眠；期间开了新一轮就放弃退出 */
    suspend fun exit(sleep: Boolean) {
        // 熄屏后 CPU 会挂起，等待与收尾都会停住；进程结束即释放
        val wakeLock = ScheduleWakeLock.acquire(context, WAKE_TIMEOUT_MS)
        // IDLE 先于完成通知，留点时间让推送排上队；熄屏前等完
        delay(NOTIFY_ENQUEUE_GRACE_MS)
        externalNotification.awaitPendingSends(NOTIFY_FLUSH_TIMEOUT_MS)
        // 占住启动锁，后到的定时按忙碌跳过
        if (!isIdle() || !mutex.tryAcquire(EXIT_HOLDER_ID, "exit")) {
            Timber.i("AppTerminator: another run started, exit aborted")
            ScheduleWakeLock.release(wakeLock)
            return
        }
        Timber.i("AppTerminator: exiting, sleep=%s", sleep)
        try {
            // 失败截图要从提权进程取，须在断开前
            telemetry.onAppExit()
            // 面板是我们关的就一并休眠，否则关服务时会被点亮
            if (sleep || screenPower.poweredOff != null) {
                wakeUnlockEngine.lockAndSleep()
            }
            withContext(Dispatchers.IO) { RemoteServiceManager.unbind() }
            withContext(Dispatchers.Main) {
                context.getSystemService(ActivityManager::class.java)
                    .appTasks.forEach { it.finishAndRemoveTask() }
            }
        } finally {
            // EXIT_SELF/0 才算正常退出，killProcess 会记成 SIGNALED
            exitProcess(0)
        }
    }

    private fun isIdle(): Boolean = compositionService.state.value.let {
        it == MaaExecutionState.IDLE || it == MaaExecutionState.ERROR
    }

    private companion object {
        const val EXIT_HOLDER_ID = "app-exit"
        const val WAKE_TIMEOUT_MS = 90_000L
        const val NOTIFY_ENQUEUE_GRACE_MS = 3_000L
        const val NOTIFY_FLUSH_TIMEOUT_MS = 30_000L
    }
}
