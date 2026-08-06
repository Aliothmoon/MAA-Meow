package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.WakeUnlockResult
import com.aliothmoon.maameow.manager.RemoteServiceManager
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
        BOUNCER_NOT_READY(
            WakeUnlockResult.BOUNCER_NOT_READY,
            uiTextOf(R.string.wake_result_bouncer_not_ready),
        ),
        UNSUPPORTED(WakeUnlockResult.UNSUPPORTED, uiTextOf(R.string.wake_result_unsupported)),
        LOCK_FAILED(WakeUnlockResult.LOCK_FAILED, uiTextOf(R.string.wake_result_lock_failed)),
        IPC_FAILED(-1, uiTextOf(R.string.wake_result_ipc_failed));

        val isSuccess: Boolean get() = this == OK

        companion object {
            fun fromCode(code: Int): WakeResult =
                entries.firstOrNull { it.code == code } ?: IPC_FAILED
        }
    }

    /** 亮屏并解锁 */
    suspend fun unlock(credential: String): WakeResult = withContext(Dispatchers.IO) {
        val result = runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = IPC_TIMEOUT_MS) { svc ->
                WakeResult.fromCode(svc.unlock(credential))
            }
        }.getOrElse { t ->
            Timber.w(t, "unlock: IPC failed")
            WakeResult.IPC_FAILED
        }
        Timber.i("unlock -> %s", result)
        result
    }

    /** 设置页自测：先锁屏息屏再解锁 */
    suspend fun testUnlock(credential: String): WakeResult = withContext(Dispatchers.IO) {
        val result = runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = IPC_TIMEOUT_MS) { svc ->
                WakeResult.fromCode(svc.testUnlock(credential))
            }
        }.getOrElse { t ->
            Timber.w(t, "testUnlock: IPC failed")
            WakeResult.IPC_FAILED
        }
        Timber.i("testUnlock -> %s", result)
        result
    }

    /** 锁屏并息屏（任务结束后自动休眠） */
    suspend fun lockAndSleep(): WakeResult = withContext(Dispatchers.IO) {
        val result = runCatching {
            RemoteServiceManager.useRemoteService(timeoutMs = IPC_TIMEOUT_MS) { svc ->
                WakeResult.fromCode(svc.lockAndSleep())
            }
        }.getOrElse { t ->
            Timber.w(t, "lockAndSleep: IPC failed")
            WakeResult.IPC_FAILED
        }
        Timber.i("lockAndSleep -> %s", result)
        result
    }

    private companion object {
        const val IPC_TIMEOUT_MS = 30_000L
    }
}
