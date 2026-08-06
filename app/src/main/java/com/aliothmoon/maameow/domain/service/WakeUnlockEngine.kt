package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** 唤醒 + 解锁的 App 侧入口；实际序列在提权进程 `WakeUnlockController` */
class WakeUnlockEngine {

    /** 与 `WakeUnlockController.Result` 一一对应 */
    enum class WakeResult(val code: Int, val message: UiText) {
        OK(0, uiTextOf(R.string.wake_result_ok)),
        WAKE_FAILED(1, uiTextOf(R.string.wake_result_wake_failed)),
        CREDENTIAL_REQUIRED(2, uiTextOf(R.string.wake_result_credential_required)),
        CREDENTIAL_REJECTED(3, uiTextOf(R.string.wake_result_credential_rejected)),
        BOUNCER_NOT_READY(4, uiTextOf(R.string.wake_result_bouncer_not_ready)),
        UNSUPPORTED(5, uiTextOf(R.string.wake_result_unsupported)),
        LOCK_FAILED(6, uiTextOf(R.string.wake_result_lock_failed)),
        IPC_FAILED(-1, uiTextOf(R.string.wake_result_ipc_failed));

        val isSuccess: Boolean get() = this == OK

        companion object {
            fun fromCode(code: Int): WakeResult =
                entries.firstOrNull { it.code == code } ?: IPC_FAILED
        }
    }

    /** 点亮屏幕并解除锁屏；失败不重试 */
    suspend fun wakeAndUnlock(credential: String): WakeResult = withContext(Dispatchers.IO) {
        val result = runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = IPC_TIMEOUT_MS) { svc ->
                WakeResult.fromCode(svc.wakeAndUnlock(credential))
            }
        }.getOrElse { t ->
            Timber.w(t, "wakeAndUnlock: IPC failed")
            WakeResult.IPC_FAILED
        }
        Timber.i("wakeAndUnlock -> %s", result)
        result
    }

    /** 设置页测试：锁屏 → 延时 → 解锁（提权进程内串行） */
    suspend fun testWakeAndUnlock(credential: String): WakeResult = withContext(Dispatchers.IO) {
        val result = runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = IPC_TIMEOUT_MS) { svc ->
                WakeResult.fromCode(svc.testWakeAndUnlock(credential))
            }
        }.getOrElse { t ->
            Timber.w(t, "testWakeAndUnlock: IPC failed")
            WakeResult.IPC_FAILED
        }
        Timber.i("testWakeAndUnlock -> %s", result)
        result
    }

    /** 仅点亮屏幕，不解锁 */
    suspend fun wakeScreen(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = IPC_TIMEOUT_MS) { it.wakeScreen() }
        }.getOrDefault(false)
    }

    /** 熄屏；保持解锁状态，避免下次定时再解 */
    suspend fun turnScreenOff(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = IPC_TIMEOUT_MS) { svc ->
                svc.setDisplayPower(false)
                true
            }
        }.getOrDefault(false)
    }

    private companion object {
        const val IPC_TIMEOUT_MS = 30_000L
    }
}
