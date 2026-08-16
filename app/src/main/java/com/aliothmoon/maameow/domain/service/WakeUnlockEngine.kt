package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.WakeUnlockResult
import com.aliothmoon.maameow.domain.models.GestureRecordResult
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.Dispatchers
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
        IPC_FAILED(-1, uiTextOf(R.string.wake_result_ipc_failed));

        val isSuccess: Boolean get() = this == OK

        companion object {
            fun fromCode(code: Int): WakeResult =
                entries.firstOrNull { it.code == code } ?: IPC_FAILED
        }
    }

    /** 亮屏并解锁 */
    suspend fun unlock(credential: UnlockCredential): WakeResult = when (credential) {
        UnlockCredential.Swipe -> call("unlock") { it.unlock("") }
        is UnlockCredential.Pin -> call("unlock") { it.unlock(credential.digits) }
        is UnlockCredential.Gesture ->
            call("unlockWithGesture") { it.unlockWithGesture(credential.json) }
    }

    /** 设置页自测：先锁屏息屏再解锁 */
    suspend fun testUnlock(credential: UnlockCredential): WakeResult = when (credential) {
        UnlockCredential.Swipe -> call("testUnlock") { it.testUnlock("") }
        is UnlockCredential.Pin -> call("testUnlock") { it.testUnlock(credential.digits) }
        is UnlockCredential.Gesture ->
            call("testUnlockGesture") { it.testUnlockGesture(credential.json) }
    }

    /** 锁屏并息屏（任务结束后自动休眠） */
    suspend fun lockAndSleep(): WakeResult = call("lockAndSleep") { it.lockAndSleep() }

    /** 提权侧返回的都是 [WakeUnlockResult] 码，IPC 失败统一落到 [WakeResult.IPC_FAILED] */
    private suspend fun call(
        name: String,
        block: (RemoteService) -> Int,
    ): WakeResult = withContext(Dispatchers.IO) {
        val result = runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = IPC_TIMEOUT_MS) { svc ->
                WakeResult.fromCode(block(svc))
            }
        }.getOrElse { t ->
            Timber.w(t, "%s: IPC failed", name)
            WakeResult.IPC_FAILED
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
