package com.aliothmoon.maameow.remote.internal

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.view.Display
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal enum class DismissEvent(val label: String) {
    ERROR("error"),
    SUCCEEDED("succeeded"),
    CANCELLED("cancelled"),
}

/**
 * 框架侧 IKeyguardDismissCallback 的手工 Binder 代理
 * 避免引入隐藏类副本；仅记录事件，供冷唤醒期间识别被系统取消/报错的 dismissKeyguard
 */
internal class KeyguardDismissWatcher(
    private val onEvent: (DismissEvent) -> Unit,
) {
    private val last = AtomicReference<DismissEvent?>(null)
    private val retryable = AtomicReference<DismissEvent?>(null)

    val lastEvent: DismissEvent? get() = last.get()

    /** 取走并清空一次可重试的失败回调（error / cancelled），避免重复触发 */
    fun consumeRetryable(): DismissEvent? = retryable.getAndSet(null)

    private val binder = object : Binder() {
        init {
            attachInterface(null, DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val event = when (code) {
                txError -> DismissEvent.ERROR
                txSucceeded -> DismissEvent.SUCCEEDED
                txCancelled -> DismissEvent.CANCELLED
                else -> return super.onTransact(code, data, reply, flags)
            }
            data.enforceInterface(DESCRIPTOR)
            record(event)
            return true
        }
    }

    val proxy: Any? = runCatching {
        val iface = Class.forName(DESCRIPTOR)
        Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { proxy, method, args ->
            when (method.name) {
                "asBinder" -> binder
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "KeyguardDismissWatcher"
                else -> null
            }
        }
    }.getOrNull()

    private fun record(event: DismissEvent) {
        last.set(event)
        if (event == DismissEvent.ERROR || event == DismissEvent.CANCELLED) {
            retryable.set(event)
        }
        onEvent(event)
    }

    private companion object {
        const val DESCRIPTOR = "com.android.internal.policy.IKeyguardDismissCallback"

        private val stubClass: Class<*>? = runCatching { Class.forName("$DESCRIPTOR\$Stub") }.getOrNull()

        private fun resolveTx(fieldName: String, fallback: Int): Int = runCatching {
            val field = stubClass?.getDeclaredField(fieldName) ?: return fallback
            field.isAccessible = true
            field.getInt(null)
        }.getOrDefault(fallback)

        val txError: Int = resolveTx("TRANSACTION_onDismissError", IBinder.FIRST_CALL_TRANSACTION)
        val txSucceeded: Int = resolveTx("TRANSACTION_onDismissSucceeded", IBinder.FIRST_CALL_TRANSACTION + 1)
        val txCancelled: Int = resolveTx("TRANSACTION_onDismissCancelled", IBinder.FIRST_CALL_TRANSACTION + 2)
    }
}

internal enum class SettleOutcome {
    READY_TO_INJECT,
    ALREADY_UNLOCKED,
    UNSUPPORTED,
}

/**
 * 等待 bouncer 就绪：期间提前解锁立刻放行，收到 dismissKeyguard 取消/报错时最多补请求一次
 * 不重试 PIN 注入，避免连续输错触发系统锁定
 */
internal fun awaitBouncerReady(
    initialSettleMs: Long,
    maxTotalMs: Long,
    pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    redismissDelayMs: Long = 300L,
    retrySettleMs: Long = 1_200L,
    clockMs: () -> Long = SystemClock::elapsedRealtime,
    sleep: (Long) -> Unit = Thread::sleep,
    isKeyguardLocked: () -> Boolean?,
    consumeRetryableDismiss: () -> DismissEvent?,
    retryDismiss: () -> Boolean,
    onRetry: (DismissEvent, Boolean) -> Unit,
): SettleOutcome {
    val startMs = clockMs()
    val hardDeadline = startMs + maxTotalMs.coerceAtLeast(0L)
    var targetMs = (startMs + initialSettleMs.coerceAtLeast(0L)).coerceAtMost(hardDeadline)
    var retried = false

    while (true) {
        when (isKeyguardLocked()) {
            false -> return SettleOutcome.ALREADY_UNLOCKED
            null -> return SettleOutcome.UNSUPPORTED
            true -> Unit
        }

        val failedEvent = consumeRetryableDismiss()
        if (failedEvent != null && !retried) {
            retried = true
            val pause = redismissDelayMs.coerceAtMost((hardDeadline - clockMs()).coerceAtLeast(0L))
            if (pause > 0L) sleep(pause)
            when (isKeyguardLocked()) {
                false -> return SettleOutcome.ALREADY_UNLOCKED
                null -> return SettleOutcome.UNSUPPORTED
                true -> Unit
            }
            val ok = retryDismiss()
            onRetry(failedEvent, ok)
            if (ok) {
                targetMs = (clockMs() + retrySettleMs).coerceAtMost(hardDeadline)
            }
        }

        val remaining = targetMs - clockMs()
        if (remaining <= 0L) break
        sleep(minOf(pollIntervalMs, remaining))
    }

    return when (isKeyguardLocked()) {
        false -> SettleOutcome.ALREADY_UNLOCKED
        null -> SettleOutcome.UNSUPPORTED
        true -> SettleOutcome.READY_TO_INJECT
    }
}

internal fun displayStateName(state: Int): String = when (state) {
    Display.STATE_UNKNOWN -> "UNKNOWN"
    Display.STATE_OFF -> "OFF"
    Display.STATE_ON -> "ON"
    Display.STATE_DOZE -> "DOZE"
    Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
    Display.STATE_VR -> "VR"
    Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
    else -> "STATE_$state"
}

private val CURRENT_FOCUS_REGEX = Regex("""mCurrentFocus=(null|Window\{[^}]*\})""")
private val WINDOW_TOKEN_REGEX = Regex("""Window\{[0-9a-fA-F]+\s+u\d+\s+([^\s}]+)""")
private val SAFE_TOKEN_REGEX = Regex("""[^A-Za-z0-9._/$:-]""")
private val POLICY_KEYS = listOf(
    "screenState",
    "mScreenOnFully",
    "mAwake",
    "mKeyguardOccluded",
    "mShowingDream",
    "isStatusBarKeyguard",
)
private val POLICY_VALUE_REGEX = Regex("""\b(${POLICY_KEYS.joinToString("|")})=([A-Za-z0-9_]+)""")

/**
 * 从 dumpsys window policy / displays 摘出焦点窗口与屏幕策略状态，仅在解锁失败时调用
 * 焦点只保留不含空格的组件/类名片段，避免把通知或窗口标题带进遥测日志
 */
internal fun parseWindowFocusSummary(dumpsysPolicy: String, dumpsysDisplays: String): String? {
    val parts = mutableListOf<String>()

    val focusMatch = CURRENT_FOCUS_REGEX.find(dumpsysDisplays)?.groupValues?.getOrNull(1)
    if (focusMatch != null) {
        val focus = if (focusMatch == "null") {
            "null"
        } else {
            val raw = WINDOW_TOKEN_REGEX.find(focusMatch)?.groupValues?.getOrNull(1) ?: "unknown"
            SAFE_TOKEN_REGEX.replace(raw, "").take(80).ifEmpty { "unknown" }
        }
        parts += "focus=$focus"
    }

    val seen = LinkedHashMap<String, String>()
    for (m in POLICY_VALUE_REGEX.findAll(dumpsysPolicy)) {
        val key = m.groupValues[1]
        if (key !in seen) seen[key] = m.groupValues[2]
    }
    for ((k, v) in seen) {
        parts += "$k=$v"
    }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/** 带严格超时的窗口状态采样；仅在解锁失败后执行，超时强杀子进程 */
internal fun captureWindowFocusSummary(timeoutMs: Long = 1_500L): String? {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    val policy = runDumpsysBounded(arrayOf("dumpsys", "window", "policy"), deadline)
    val displays = runDumpsysBounded(arrayOf("dumpsys", "window", "displays"), deadline)
    if (policy.isEmpty() && displays.isEmpty()) return null
    return parseWindowFocusSummary(policy, displays)
}

private const val MAX_DUMPSYS_BYTES = 128 * 1024

private fun runDumpsysBounded(cmd: Array<String>, deadlineMs: Long): String {
    val budget = deadlineMs - SystemClock.elapsedRealtime()
    if (budget <= 50L) return ""
    var process: Process? = null
    return try {
        process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val buf = ByteArrayOutputStream()
        val reader = Thread {
            runCatching {
                val chunk = ByteArray(4096)
                val stream = process.inputStream
                while (true) {
                    val n = stream.read(chunk)
                    if (n <= 0) break
                    val left = MAX_DUMPSYS_BYTES - buf.size()
                    if (left > 0) buf.write(chunk, 0, minOf(n, left))
                }
            }
        }.apply {
            name = "wake-dumpsys"
            isDaemon = true
            start()
        }
        val remaining = (deadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(50L)
        if (!process.waitFor(remaining, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
        }
        reader.join(100L)
        buf.toString(Charsets.UTF_8.name())
    } catch (_: Exception) {
        process?.destroyForcibly()
        ""
    }
}
