package com.aliothmoon.maameow.domain.service

import android.content.Intent
import android.os.SystemClock
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.WakeUnlockResult
import com.aliothmoon.maameow.domain.models.GestureRecordResult
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber

class WakeUnlockEngine {

    enum class WakeResult(val code: Int, val message: UiText) {
        OK(WakeUnlockResult.OK, uiTextOf(R.string.wake_result_ok)),
        WAKE_FAILED(WakeUnlockResult.WAKE_FAILED, uiTextOf(R.string.wake_result_wake_failed)),
        CREDENTIAL_REQUIRED(
            WakeUnlockResult.CREDENTIAL_REQUIRED,
            uiTextOf(R.string.wake_result_credential_required),
        ),
        CREDENTIAL_REJECTED(
            WakeUnlockResult.CREDENTIAL_REJECTED,
            uiTextOf(R.string.wake_result_credential_rejected),
        ),
        NO_KEYGUARD(
            WakeUnlockResult.NO_KEYGUARD,
            uiTextOf(R.string.wake_result_no_keyguard),
        ),
        UNSUPPORTED(WakeUnlockResult.UNSUPPORTED, uiTextOf(R.string.wake_result_unsupported)),
        LOCK_FAILED(WakeUnlockResult.LOCK_FAILED, uiTextOf(R.string.wake_result_lock_failed)),
        GESTURE_EMPTY(
            WakeUnlockResult.GESTURE_EMPTY,
            uiTextOf(R.string.wake_result_gesture_empty),
        ),
        GESTURE_SCREEN_MISMATCH(
            WakeUnlockResult.GESTURE_SCREEN_MISMATCH,
            uiTextOf(R.string.wake_result_gesture_screen_mismatch),
        ),
        RECORD_NO_DEVICE(
            WakeUnlockResult.RECORD_NO_DEVICE,
            uiTextOf(R.string.wake_result_record_no_device),
        ),
        RECORD_TIMEOUT(
            WakeUnlockResult.RECORD_TIMEOUT,
            uiTextOf(R.string.wake_result_record_timeout),
        ),
        RECORD_CANCELLED(
            WakeUnlockResult.RECORD_CANCELLED,
            uiTextOf(R.string.wake_result_record_cancelled),
        ),
        RECORD_NO_TOUCH(
            WakeUnlockResult.RECORD_NO_TOUCH,
            uiTextOf(R.string.wake_result_record_no_touch),
        ),
        RECORD_BUSY(
            WakeUnlockResult.RECORD_BUSY,
            uiTextOf(R.string.wake_result_record_busy),
        ),
        IPC_FAILED(-1, uiTextOf(R.string.wake_result_ipc_failed)),

        /** App 侧拦截：任务运行中锁屏或注入会打断任务 */
        TASK_RUNNING(-2, uiTextOf(R.string.wake_result_task_running)),

        /** 提权侧返回了本端不认识的码，多半是两端版本不一致 */
        UNKNOWN(-3, uiTextOf(R.string.wake_result_unknown));

        val isSuccess: Boolean get() = this == OK

        companion object {
            fun fromCode(code: Int): WakeResult =
                entries.firstOrNull { it.code == code && it.code >= 0 } ?: run {
                    Timber.w("unknown wake result code: %d", code)
                    UNKNOWN
                }
        }
    }

    /** [uiLaunched] 只在传了 launchIntent 时有意义；[elapsedMs] 为整次调用耗时 */
    data class WakeLaunch(val result: WakeResult, val uiLaunched: Boolean, val elapsedMs: Long = 0L)

    /** 最近一次 [wakeAndLaunch] 的发起时刻，[lastTrace] 只认这之后开始的解锁 */
    @Volatile
    private var unlockStartedAtMs = 0L

    /** [credential] 为 null 时不碰锁屏，只拉界面 */
    suspend fun wakeAndLaunch(credential: UnlockCredential?, launchIntent: Intent?): WakeLaunch {
        val startMs = SystemClock.elapsedRealtime()
        unlockStartedAtMs = startMs
        return call("wakeAndLaunch", WakeLaunch(WakeResult.IPC_FAILED, uiLaunched = false)) {
            val outcome = it.wakeAndLaunch(credential?.type, credential?.payload.orEmpty(), launchIntent)
            WakeLaunch(
                result = WakeResult.fromCode(outcome.unlockCode),
                uiLaunched = outcome.uiLaunched,
                elapsedMs = SystemClock.elapsedRealtime() - startMs,
            )
        }
    }

    /** 设置页自测：先锁屏息屏再解锁 */
    suspend fun testUnlock(credential: UnlockCredential): WakeResult {
        val startMs = SystemClock.elapsedRealtime()
        return call("testUnlock", WakeResult.IPC_FAILED) { svc ->
            val result = WakeResult.fromCode(svc.testUnlock(credential.type, credential.payload))
            runCatching { svc.getLastWakeTrace(startMs) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { Timber.i("testUnlock trace:\n%s", it) }
            result
        }
    }

    /** 锁屏并息屏（任务结束后自动休眠） */
    suspend fun lockAndSleep(): WakeResult =
        call("lockAndSleep", WakeResult.IPC_FAILED) { WakeResult.fromCode(it.lockAndSleep()) }

    /**
     * 提权侧本次 [wakeAndLaunch] 的步骤与耗时
     * 只问已有连接；没连上、调用没到提权侧或提权进程是旧版本返回 null
     */
    suspend fun lastTrace(): String? = withContext(Dispatchers.IO) {
        val service = RemoteServiceManager.getInstanceOrNull() ?: return@withContext null
        runCatching { service.getLastWakeTrace(unlockStartedAtMs) }
            .onFailure { Timber.w(it, "lastWakeTrace failed") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun <T> call(
        name: String,
        ipcFailed: T,
        block: (RemoteService) -> T,
    ): T = withContext(Dispatchers.IO) {
        val result = try {
            RemoteServiceManager.useRemoteService(timeoutMs = IPC_TIMEOUT_MS) { svc -> block(svc) }
        } catch (e: Exception) {
            // 等连接超时不算取消
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            Timber.w(e, "%s: IPC failed", name)
            ipcFailed
        }
        Timber.i("%s -> %s", name, result)
        result
    }

    // ───────────────── 手势录制 ─────────────────

    /** 立即返回；提权进程会先锁屏，随后等用户手动解锁 */
    suspend fun startGestureRecord(timeoutMs: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = IPC_TIMEOUT_MS) { svc ->
                svc.startGestureRecord(timeoutMs)
            }
            true
        }.getOrElse { t ->
            Timber.w(t, "startGestureRecord: IPC failed")
            false
        }
    }

    /**
     * 取一次录制结果，终态取走即清，重复调用不会重复消费
     * 只看已有连接，不主动拉起提权服务——设置页每次进入都会问一次
     * @return null 表示没连上或调用失败，调用方按「还在录」处理
     */
    suspend fun pollGestureRecord(): GestureRecordResult? = withContext(Dispatchers.IO) {
        val service = RemoteServiceManager.getInstanceOrNull() ?: return@withContext null
        runCatching {
            JsonUtils.common.decodeFromString(
                GestureRecordResult.serializer(),
                service.pollGestureRecord(),
            )
        }.getOrElse { t ->
            Timber.w(t, "pollGestureRecord failed")
            null
        }
    }

    suspend fun cancelGestureRecord() {
        withContext(Dispatchers.IO) {
            runCatching { RemoteServiceManager.getInstanceOrNull()?.cancelGestureRecord() }
                .onFailure { Timber.w(it, "cancelGestureRecord: IPC failed") }
        }
    }

    private companion object {
        const val IPC_TIMEOUT_MS = 30_000L
    }
}
