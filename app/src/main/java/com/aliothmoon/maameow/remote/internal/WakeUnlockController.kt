package com.aliothmoon.maameow.remote.internal

import android.content.Intent
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import com.aliothmoon.maameow.constant.WakeUnlockResult
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.maa.InputControlUtils
import com.aliothmoon.maameow.remote.WakeOutcome
import com.aliothmoon.maameow.remote.internal.ScreenPowerAttempts.SleepAction
import com.aliothmoon.maameow.remote.internal.ScreenPowerAttempts.WakeAction
import com.aliothmoon.maameow.remote.internal.WakeUnlockController.LOCK_SETTLE_MS
import com.aliothmoon.maameow.remote.internal.WakeUnlockController.lockAndSleep
import com.aliothmoon.maameow.third.wrappers.DisplayManager
import com.aliothmoon.maameow.third.wrappers.PowerManager
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.aliothmoon.maameow.third.wrappers.WindowManager

/** 唤醒/解锁/锁屏；提权进程内完成，凭证支持纯数字 PIN 与录制手势 */
object WakeUnlockController {

    private const val SCREEN_ON_TIMEOUT_MS = 5_000L
    private const val DISPLAY_ON_TIMEOUT_MS = 2_000L
    private const val DISPLAY_ON_POLL_MS = 50L
    private const val KEY_WAKE_TIMEOUT_MS = 2_000L
    private const val KEYGUARD_GONE_TIMEOUT_MS = 5_000L

    /** 息屏/AOD 刚切到 Display.STATE_ON 时再让 Keyguard 稳一下，避免 dismissKeyguard 在过渡期被吞 */
    private const val POST_WAKE_DISMISS_DELAY_MS = 300L

    /** bouncer 就绪基线：亮屏热机 1.2s，息屏/Doze 冷唤醒 2.0s */
    private const val BOUNCER_SETTLE_MS = 1_200L
    private const val COLD_BOUNCER_SETTLE_MS = 2_000L

    /** 限制稳定等待，避免拉长定时任务启动耗时 */
    private const val BOUNCER_SETTLE_MAX_MS = 5_000L
    private const val DIGIT_GAP_MS = 50L

    /** PIN 注入补足到固定时长，trace 与触发日志的耗时推不出位数；常见位数都在预算内 */
    private const val PIN_INJECT_BUDGET_MS = 1_000L

    /** 手势回放前等锁屏首屏稳定；不弹 bouncer，比 PIN 那条路要短 */
    private const val GESTURE_SETTLE_MS = 800L

    /** 解锁后等锁屏退场与桌面恢复，避免紧跟的 startActivity 被过渡吞掉 */
    private const val POST_UNLOCK_SETTLE_MS = 500L

    /** 测试：上锁/息屏后等待系统稳定再解锁 */
    private const val LOCK_SETTLE_MS = 500L
    private const val SCREEN_OFF_TIMEOUT_MS = 3_000L

    /** 收尾后不再改动，取时才拼文本 */
    @Volatile
    private var last: WakeTrace? = null

    /**
     * 最近一次解锁（含自测）的步骤与耗时，App 解锁失败时取走写进触发日志
     * @param sinceElapsedMs App 发起解锁时的 elapsedRealtime；更早开始的不算，免得把别次的过程记到这次头上
     */
    fun lastTrace(sinceElapsedMs: Long): String =
        last?.takeIf { it.startMs >= sinceElapsedMs }?.render().orEmpty()

    /**
     * 设置页自测：先 [lockAndSleep]，等待 [LOCK_SETTLE_MS] 后再解锁
     * 整段在提权进程内完成，避免息屏后 App 侧协程被挂起
     * @param type [UnlockCredential.type]
     */
    fun testUnlock(type: String, payload: String): Int = traced("test unlock, $type") {
        lockForTest(it) ?: unlockBy(type, payload, it)
    }

    /** @return 非 null 即上锁失败的结果码 */
    private fun lockForTest(trace: WakeTrace): Int? {
        val lockCode = lockAndSleep(trace)
        if (lockCode != WakeUnlockResult.OK) return lockCode
        trace.i("locked for test, settle ${LOCK_SETTLE_MS}ms")
        Thread.sleep(LOCK_SETTLE_MS)
        return null
    }

    /** lockNow 上锁并 goToSleep 息屏 */
    fun lockAndSleep(): Int = lockAndSleep(WakeTrace())

    private fun lockAndSleep(trace: WakeTrace): Int {
        val pm = ServiceManager.getPowerManager()
        val wm = ServiceManager.getWindowManager()

        if (!wm.lockNow()) {
            trace.w("lockNow unavailable")
            return WakeUnlockResult.UNSUPPORTED
        }
        if (!pollUntil(KEYGUARD_GONE_TIMEOUT_MS) { wm.isKeyguardLocked == true }) {
            // 锁屏方式为「无」时 lockNow 后 keyguard 永不出现；滑动/密码锁屏均会出现，
            // 超时且非 secure 即视为未设置锁屏，此时也无需息屏验证
            if (wm.isKeyguardSecure(0) != true) {
                trace.i("keyguard never appeared and not secure — no lock screen configured")
                return WakeUnlockResult.NO_KEYGUARD
            }
            trace.w("keyguard did not lock after lockNow")
            return WakeUnlockResult.LOCK_FAILED
        }

        if (!ensureScreenOff(pm, trace)) {
            trace.w("screen still on after sleep attempts (keyguard already locked)")
        } else {
            trace.i("screen locked and off")
        }
        return WakeUnlockResult.OK
    }

    internal fun bouncerSettleMs(wakeCostMs: Long, coldWake: Boolean = false): Long {
        val base = if (coldWake) COLD_BOUNCER_SETTLE_MS else BOUNCER_SETTLE_MS
        return (base + wakeCostMs * 2).coerceAtMost(BOUNCER_SETTLE_MAX_MS)
    }

    private sealed interface WakeCheck {
        data class Ready(val wasOn: Boolean, val wakeCostMs: Long) : WakeCheck
        data class Done(val resultCode: Int) : WakeCheck
    }

    /**
     * 亮屏（含等待主屏进入 Display.STATE_ON）并确认 keyguard 还在
     */
    private fun wakeAndRequireKeyguard(
        pm: PowerManager,
        dm: DisplayManager,
        wm: WindowManager,
        trace: WakeTrace,
    ): WakeCheck {
        val startMs = SystemClock.elapsedRealtime()
        val screenWasOn = pm.isScreenOn(0)
        val initialDisplay = dm.getDisplayState(0)
        if (!ensureScreenOn(pm, trace)) {
            trace.w("screen did not turn on after wakeUp and key fallback")
            return WakeCheck.Done(WakeUnlockResult.WAKE_FAILED)
        }
        val interactiveMs = SystemClock.elapsedRealtime() - startMs

        // isDisplayInteractive 在 system_server 里几十 ms 就翻 true，但面板与 SystemUI 可能还在 OFF/DOZE 过渡
        val displayWaitStartMs = SystemClock.elapsedRealtime()
        val finalDisplay = waitForDisplayOn(dm, initialDisplay)
        val displayWaitMs = SystemClock.elapsedRealtime() - displayWaitStartMs
        val totalWakeMs = SystemClock.elapsedRealtime() - startMs

        val displayNote = if (initialDisplay != Display.STATE_UNKNOWN) {
            ", display=${displayStateName(initialDisplay)}->${displayStateName(finalDisplay)} (+${displayWaitMs}ms)"
        } else {
            ""
        }
        trace.i("screen on in ${interactiveMs}ms, wasOn=$screenWasOn$displayNote")
        if (!screenWasOn) {
            // 续一下锁屏亮屏计时，避免短超时 ROM 在等 bouncer 期间自己熄回去
            pm.userActivity(0)
        }

        val locked = wm.isKeyguardLocked
        if (locked == null) {
            trace.w("isKeyguardLocked unavailable")
            return WakeCheck.Done(WakeUnlockResult.UNSUPPORTED)
        }
        if (!locked) {
            trace.i("keyguard not showing, nothing to do")
            return WakeCheck.Done(WakeUnlockResult.OK)
        }
        return WakeCheck.Ready(wasOn = screenWasOn, wakeCostMs = totalWakeMs)
    }

    private fun waitForDisplayOn(dm: DisplayManager, initialState: Int): Int {
        if (initialState == Display.STATE_UNKNOWN) return Display.STATE_UNKNOWN
        var state = dm.getDisplayState(0)
        if (state == Display.STATE_ON || state == Display.STATE_UNKNOWN) return state
        pollUntil(DISPLAY_ON_TIMEOUT_MS, intervalMs = DISPLAY_ON_POLL_MS) {
            state = dm.getDisplayState(0)
            state == Display.STATE_ON || state == Display.STATE_UNKNOWN
        }
        return state
    }

    /**
     * 解锁后按需拉起并确认界面到主屏前台；解锁没成但锁屏已不在也照拉
     * @param type [UnlockCredential.type]，null 不碰锁屏
     */
    fun wakeAndLaunch(type: String?, payload: String, launchIntent: Intent?): WakeOutcome =
        traced("unlock, ${type ?: "skipped"}") { trace ->
            val code = unlockBy(type, payload, trace)
            WakeOutcome(code, launchIntent != null && launchUi(type, code, launchIntent, trace))
        }

    private fun unlockBy(type: String?, payload: String, trace: WakeTrace): Int = when (type) {
        null -> WakeUnlockResult.OK
        UnlockCredential.TYPE_PIN -> doUnlock(payload, trace)
        UnlockCredential.TYPE_GESTURE -> doUnlockWithGesture(payload, trace)
        else -> doUnlock("", trace)
    }

    private fun launchUi(type: String?, unlockCode: Int, intent: Intent, trace: WakeTrace): Boolean {
        if (unlockCode != WakeUnlockResult.OK && ServiceManager.getWindowManager().isKeyguardLocked != false) {
            trace.w("still locked, ui launch skipped")
            return false
        }
        if (type != null) {
            trace.i("settle ${POST_UNLOCK_SETTLE_MS}ms before ui launch")
            Thread.sleep(POST_UNLOCK_SETTLE_MS)
        }
        return ActivityUtils.launchAndConfirm(intent, onInfo = trace::i, onWarn = trace::w)
    }

    /** @param credential 纯数字 PIN，无凭证锁屏传空串 */
    private fun doUnlock(credential: String, trace: WakeTrace): Int {
        val pm = ServiceManager.getPowerManager()
        val dm = ServiceManager.getDisplayManager()
        val wm = ServiceManager.getWindowManager()

        val wake = when (val check = wakeAndRequireKeyguard(pm, dm, wm, trace)) {
            is WakeCheck.Done -> return check.resultCode
            is WakeCheck.Ready -> check
        }

        val secure = wm.isKeyguardSecure(0) ?: false
        trace.i("keyguard locked, secure=$secure")

        if (!wake.wasOn) {
            Thread.sleep(POST_WAKE_DISMISS_DELAY_MS)
            keyguardGoneBeforeInject(wm, "dismiss", trace)?.let { return it }
        }

        val dismissWatcher = KeyguardDismissWatcher { event ->
            trace.i("dismissKeyguard callback: ${event.label}")
        }
        if (!wm.dismissKeyguard(dismissWatcher.proxy)) {
            trace.w("dismissKeyguard unavailable on this ROM")
            return WakeUnlockResult.UNSUPPORTED
        }
        trace.i("dismissKeyguard requested")

        if (!secure) {
            return if (pollUntil(KEYGUARD_GONE_TIMEOUT_MS) { wm.isKeyguardLocked == false }) {
                trace.i("unlocked (insecure keyguard)")
                WakeUnlockResult.OK
            } else {
                trace.w("insecure keyguard did not dismiss")
                logFailureSnapshot(pm, dm, dismissWatcher, trace)
                WakeUnlockResult.CREDENTIAL_REJECTED
            }
        }

        if (credential.isEmpty()) {
            trace.w("secure keyguard but no credential configured")
            return WakeUnlockResult.CREDENTIAL_REQUIRED
        }
        if (credential.any { !it.isDigit() }) {
            trace.w("only numeric PIN is supported")
            return WakeUnlockResult.CREDENTIAL_REQUIRED
        }

        // bouncer 弹出期间 isKeyguardLocked 仍为 true；轮询等待，中途若被系统取消则补请求一次 dismissKeyguard
        val coldWake = !wake.wasOn
        val settleMs = bouncerSettleMs(wake.wakeCostMs, coldWake = coldWake)
        val settleStartMs = SystemClock.elapsedRealtime()
        when (
            awaitBouncerReady(
                initialSettleMs = settleMs,
                maxTotalMs = BOUNCER_SETTLE_MAX_MS,
                redismissDelayMs = POST_WAKE_DISMISS_DELAY_MS,
                retrySettleMs = BOUNCER_SETTLE_MS,
                isKeyguardLocked = { wm.isKeyguardLocked },
                consumeRetryableDismiss = dismissWatcher::consumeRetryable,
                retryDismiss = { wm.dismissKeyguard(dismissWatcher.proxy) },
                onRetry = { event, ok ->
                    trace.w("dismissKeyguard ${event.label} during settle, retrying once (ok=$ok)")
                },
            )
        ) {
            SettleOutcome.ALREADY_UNLOCKED -> {
                trace.i("keyguard gone before PIN injection, skipped")
                return WakeUnlockResult.OK
            }

            SettleOutcome.UNSUPPORTED -> {
                trace.w("isKeyguardLocked unavailable before PIN injection")
                return WakeUnlockResult.UNSUPPORTED
            }

            SettleOutcome.READY_TO_INJECT -> Unit
        }
        val actualSettleMs = SystemClock.elapsedRealtime() - settleStartMs
        // 位数不进任何日志：logcat 也会在提权进程死亡时被补报
        trace.i("injecting PIN after ${actualSettleMs}ms settle (wake ${wake.wakeCostMs}ms, cold=$coldWake)")

        val injectStartMs = SystemClock.elapsedRealtime()
        var events = 0
        var eventsOk = 0
        for (c in credential) {
            val keyCode = KeyEvent.KEYCODE_0 + (c - '0')
            events += 2
            if (InputControlUtils.keyDown(keyCode, 0)) eventsOk++
            if (InputControlUtils.keyUp(keyCode, 0)) eventsOk++
            Thread.sleep(DIGIT_GAP_MS)
        }
        // 部分 ROM 输完自动解锁，仍锁着才补 ENTER
        if (wm.isKeyguardLocked != false) {
            events += 2
            if (InputControlUtils.keyDown(KeyEvent.KEYCODE_ENTER, 0)) eventsOk++
            if (InputControlUtils.keyUp(KeyEvent.KEYCODE_ENTER, 0)) eventsOk++
        }
        val delivery = Delivery.of(eventsOk, events)
        val padMs = PIN_INJECT_BUDGET_MS - (SystemClock.elapsedRealtime() - injectStartMs)
        if (padMs > 0) Thread.sleep(padMs)
        trace.i("PIN injected, delivery=${delivery.label}")

        val pollStartMs = SystemClock.elapsedRealtime()
        return if (pollUntil(KEYGUARD_GONE_TIMEOUT_MS) { wm.isKeyguardLocked == false }) {
            trace.i("unlocked (PIN accepted) after ${SystemClock.elapsedRealtime() - pollStartMs}ms")
            WakeUnlockResult.OK
        } else {
            // 不重试，避免连续输错触发系统锁定
            val cause = if (delivery == Delivery.ALL) {
                "wrong PIN, or bouncer was not ready and swallowed the keys"
            } else {
                "key delivery=${delivery.label}: events likely blocked by the system"
            }
            trace.w("still locked ${SystemClock.elapsedRealtime() - pollStartMs}ms after PIN injection — $cause")
            logFailureSnapshot(pm, dm, dismissWatcher, trace)
            WakeUnlockResult.CREDENTIAL_REJECTED
        }
    }

    /**
     * 亮屏并回放录制的解锁手势
     * @param gestureJson 空串表示未录制
     */
    private fun doUnlockWithGesture(gestureJson: String, trace: WakeTrace): Int {
        val gesture = UnlockGesture.parseOrNull(gestureJson) { trace.w(it) }
        if (gesture == null || gesture.steps.isEmpty()) {
            trace.w("no usable gesture recorded")
            return WakeUnlockResult.GESTURE_EMPTY
        }

        val pm = ServiceManager.getPowerManager()
        val dm = ServiceManager.getDisplayManager()
        val wm = ServiceManager.getWindowManager()

        when (val check = wakeAndRequireKeyguard(pm, dm, wm, trace)) {
            is WakeCheck.Done -> return check.resultCode
            is WakeCheck.Ready -> Unit
        }

        // 与录制一致，在 keyguard 还在时采样
        val screen = ScreenGeometry.current() ?: run {
            trace.w("screen geometry unavailable")
            return WakeUnlockResult.UNSUPPORTED
        }
        if (screen.rotation != gesture.rotation) {
            trace.w("rotation ${screen.rotation} != recorded ${gesture.rotation}")
            return WakeUnlockResult.GESTURE_SCREEN_MISMATCH
        }
        if (screen.width != gesture.screenWidth || screen.height != gesture.screenHeight) {
            trace.w(
                "screen ${screen.width}x${screen.height} != recorded" +
                        " ${gesture.screenWidth}x${gesture.screenHeight}, scaling"
            )
        }

        // 录制起点就是亮屏后的锁屏首屏，这里不能再 dismissKeyguard 打乱状态
        Thread.sleep(GESTURE_SETTLE_MS)
        keyguardGoneBeforeInject(wm, "gesture", trace)?.let { return it }
        val actions = UnlockGestureReplay.timeline(gesture, screen.width, screen.height)
        if (actions.none { it is InjectAction.Down }) {
            trace.w("gesture has nothing to replay")
            return WakeUnlockResult.GESTURE_EMPTY
        }
        // 不报步数：点按录的手势步数就是 PIN 位数
        trace.i("replaying gesture after ${GESTURE_SETTLE_MS}ms settle")
        val stats = UnlockGestureReplay.execute(actions)
        trace.i("replay done, injected $stats")

        val pollStartMs = SystemClock.elapsedRealtime()
        return if (pollUntil(KEYGUARD_GONE_TIMEOUT_MS) { wm.isKeyguardLocked == false }) {
            trace.i("unlocked (gesture accepted) after ${SystemClock.elapsedRealtime() - pollStartMs}ms")
            WakeUnlockResult.OK
        } else {
            // 与 PIN 一致：不重试，避免连错触发系统锁定
            val cause = if (stats.allAccepted) {
                "all injected: stale trajectory, or keyguard ignores injected events"
            } else {
                "some injections not delivered: events likely blocked by the system"
            }
            trace.w("still locked ${SystemClock.elapsedRealtime() - pollStartMs}ms after gesture replay — $cause")
            logFailureSnapshot(pm, dm, dismissWatcher = null, trace = trace)
            WakeUnlockResult.CREDENTIAL_REJECTED
        }
    }

    /** 解锁失败时补记当前屏幕状态、回调结果与焦点窗口类名，辅助区分防误触/未弹 bouncer/已息屏 */
    private fun logFailureSnapshot(
        pm: PowerManager,
        dm: DisplayManager,
        dismissWatcher: KeyguardDismissWatcher?,
        trace: WakeTrace,
    ) {
        val screenOn = pm.isScreenOnOrNull(0)
        val displayState = displayStateName(dm.getDisplayState(0))
        val dismiss = dismissWatcher?.lastEvent?.label ?: "none"
        val focus = captureWindowFocusSummary()
        val extra = if (focus != null) ", $focus" else ""
        trace.w("snapshot: screenOn=$screenOn, display=$displayState, dismiss=$dismiss$extra")
    }

    /**
     * 等待期间可能已被用户或其他途径解开，此时再注入 PIN 会打进前台应用、手势会点到桌面
     * @return 非 null 即不该注入，调用方直接返回
     */
    private fun keyguardGoneBeforeInject(wm: WindowManager, what: String, trace: WakeTrace): Int? =
        when (wm.isKeyguardLocked) {
            true -> null
            false -> {
                trace.i("keyguard gone before $what injection, skipped")
                WakeUnlockResult.OK
            }

            null -> {
                trace.w("isKeyguardLocked unavailable before $what injection")
                WakeUnlockResult.UNSUPPORTED
            }
        }

    /** 只亮屏，不碰 keyguard；录制手势时用，和回放走同一条唤醒路径 */
    fun wakeScreen(): Boolean = ensureScreenOn(ServiceManager.getPowerManager(), WakeTrace())

    /** 每次解锁换一份 trace，异常时也留下已走到的步骤 */
    private inline fun <T> traced(what: String, block: (WakeTrace) -> T): T {
        val trace = WakeTrace()
        trace.note(what)
        try {
            return block(trace).also { trace.note("result=$it") }
        } finally {
            last = trace
        }
    }

    private fun ensureScreenOn(pm: PowerManager, trace: WakeTrace): Boolean =
        ScreenPowerAttempts.run(
            actions = ScreenPowerAttempts.wakeActions,
            alreadyDone = { pm.isScreenOn(0) },
            perform = { action ->
                when (action) {
                    WakeAction.BINDER -> {
                        if (!pm.wakeUp()) {
                            trace.w("wakeUp() invoke failed, polling then falling back to keys")
                        }
                    }

                    WakeAction.KEY_WAKEUP -> {
                        trace.w("injecting KEYCODE_WAKEUP")
                        injectKey(KeyEvent.KEYCODE_WAKEUP)
                    }

                    WakeAction.KEY_POWER -> if (!pm.isScreenOn(0)) {
                        trace.w("injecting KEYCODE_POWER")
                        injectKey(KeyEvent.KEYCODE_POWER)
                    }
                }
            },
            pollAfter = { action ->
                val timeout = if (action == WakeAction.BINDER) {
                    SCREEN_ON_TIMEOUT_MS
                } else {
                    KEY_WAKE_TIMEOUT_MS
                }
                val on = pollUntil(timeout) { pm.isScreenOn(0) }
                if (!on) trace.w("screen still off after $action")
                on
            },
        )

    private fun ensureScreenOff(pm: PowerManager, trace: WakeTrace): Boolean =
        ScreenPowerAttempts.run(
            actions = ScreenPowerAttempts.sleepActions,
            alreadyDone = { !pm.isScreenOn(0) },
            perform = { action ->
                when (action) {
                    SleepAction.BINDER -> {
                        if (!pm.goToSleep()) {
                            trace.w("goToSleep() invoke failed, polling then falling back to keys")
                        }
                    }

                    SleepAction.KEY_SLEEP -> {
                        trace.w("injecting KEYCODE_SLEEP")
                        injectKey(KeyEvent.KEYCODE_SLEEP)
                    }

                    SleepAction.KEY_POWER -> if (pm.isScreenOn(0)) {
                        trace.w("injecting KEYCODE_POWER")
                        injectKey(KeyEvent.KEYCODE_POWER)
                    }
                }
            },
            pollAfter = { action ->
                val timeout = if (action == SleepAction.BINDER) {
                    SCREEN_OFF_TIMEOUT_MS
                } else {
                    KEY_WAKE_TIMEOUT_MS
                }
                val off = pollUntil(timeout) { !pm.isScreenOn(0) }
                if (!off) trace.w("screen still on after $action")
                off
            },
        )

    private fun injectKey(keyCode: Int) {
        InputControlUtils.keyDown(keyCode, 0)
        InputControlUtils.keyUp(keyCode, 0)
    }
}
