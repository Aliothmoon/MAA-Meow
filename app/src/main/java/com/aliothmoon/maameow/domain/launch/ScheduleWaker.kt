package com.aliothmoon.maameow.domain.launch

import android.content.Intent
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.domain.service.ScreenSaverController
import com.aliothmoon.maameow.domain.service.UnlockAttempt
import com.aliothmoon.maameow.domain.service.WakeCredentialSource
import com.aliothmoon.maameow.domain.service.WakeUnlockEngine
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/** [failed] 为解锁没成时的尝试，交给遥测 */
sealed interface WakeStep {
    val failed: UnlockAttempt?

    data class Ready(override val failed: UnlockAttempt?) : WakeStep

    data class Locked(override val failed: UnlockAttempt, val message: UiText) : WakeStep

    data class UiLaunchFailed(override val failed: UnlockAttempt?) : WakeStep
}

/** 定时启动的唤醒解锁与拉起界面；这里只管凭证、触发日志与取证 */
class ScheduleWaker(
    private val engine: WakeUnlockEngine,
    private val credentials: WakeCredentialSource,
    private val screenSaver: ScreenSaverController,
    private val keyguardLocked: () -> Boolean,
    /** 此刻要密码才能进桌面；不是 isDeviceSecure（只说明设过密码） */
    private val deviceLocked: () -> Boolean,
    private val showIntent: (LaunchRequest) -> Intent,
) {

    internal suspend fun wake(
        request: LaunchRequest,
        launchUi: Boolean,
        before: WakeSnapshot,
        log: ScheduleTriggerLogger.Session,
    ): WakeStep {
        val intent = if (launchUi) showIntent(request) else null

        // 熄屏挂机已盖上：KEEP_SCREEN_ON，多轮定时不解、不收，只拉界面
        if (screenSaver.isShowing()) {
            log.append(uiTextOf(R.string.schedule_log_wake_skipped_screensaver))
            return if (intent == null) WakeStep.Ready(null) else launchOnly(intent, null, log)
        }

        // 交给凭证自己决定注入 PIN、回放手势还是只试 dismissKeyguard，别拿 isDeviceLocked 预跳过
        val (type, credential) = credentials.current()
        log.append(
            uiTextOf(
                if (intent != null) R.string.schedule_log_wake_and_launch_start else R.string.schedule_log_wake_start,
            ),
        )
        val wake = engine.wakeAndLaunch(credential, intent)

        var failed: UnlockAttempt? = null
        if (wake.result.isSuccess) {
            log.append(uiTextOf(R.string.schedule_log_wake_ok))
        } else {
            log.append(uiTextOf(R.string.schedule_log_wake_failed, wake.result.message))
            failed = UnlockAttempt(type = type, ready = credential.isReady, result = wake.result.name.lowercase())
            appendDiagnostics(log, type, credential, before)
            if (keyguardLocked()) {
                val message = if (deviceLocked() && !credential.isReady) {
                    uiTextOf(R.string.notification_schedule_pin_required)
                } else {
                    uiTextOf(R.string.notification_schedule_device_locked)
                }
                return WakeStep.Locked(failed, message)
            }
        }

        if (intent != null && !wake.uiLaunched) {
            if (failed == null) {
                appendTrace(log)
                return WakeStep.UiLaunchFailed(null)
            }
            // 提权侧判不出锁屏或 IPC 断了时没拉，主进程确认锁屏不在后补拉
            return launchOnly(intent, failed, log)
        }
        if (failed == null && wake.elapsedMs >= SLOW_TRACE_MS) appendTrace(log)
        return WakeStep.Ready(failed)
    }

    private suspend fun launchOnly(intent: Intent, failed: UnlockAttempt?, log: ScheduleTriggerLogger.Session): WakeStep {
        log.append(uiTextOf(R.string.schedule_log_launch_ui))
        if (engine.wakeAndLaunch(null, intent).uiLaunched) return WakeStep.Ready(failed)
        appendTrace(log)
        return WakeStep.UiLaunchFailed(failed)
    }

    /** 不带 PIN 与轨迹 */
    private suspend fun appendDiagnostics(
        log: ScheduleTriggerLogger.Session,
        type: String,
        credential: UnlockCredential,
        before: WakeSnapshot,
    ) {
        forensics("wake config") {
            log.append(
                wakeConfigText(
                    unlockType = type,
                    credential = credential,
                    gestureRecordedAtMs = (credential as? UnlockCredential.Gesture)
                        ?.let { credentials.gestureRecordedAtMs() },
                    passwordRequired = deviceLocked(),
                    before = before,
                ),
            )
        }
        appendTrace(log)
    }

    private suspend fun appendTrace(log: ScheduleTriggerLogger.Session) {
        forensics("wake trace") {
            engine.lastTrace()?.let { log.append(uiTextOf(R.string.schedule_log_wake_trace, it)) }
        }
    }

    /** 只是取证，出错也不能改掉这一轮的结局 */
    private inline fun forensics(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "ScheduleWaker: record %s failed", what)
        }
    }

    private companion object {
        const val SLOW_TRACE_MS = 5_000L
    }
}
