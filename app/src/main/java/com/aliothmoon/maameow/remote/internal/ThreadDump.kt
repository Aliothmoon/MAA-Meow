package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.bridge.NativeBridgeLib

/** 卡死诊断：只留正在处理 binder 调用或在等锁的线程 */
internal object ThreadDump {

    private const val MAX_FRAMES = 16
    private const val MAX_CHARS = 12_000

    fun capture(): String = buildString {
        runCatching { NativeBridgeLib.describeReleaseStage() }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { appendLine("native: $it") }
        val self = Thread.currentThread()
        Thread.getAllStackTraces()
            .filter { (thread, stack) -> thread !== self && (thread.state == Thread.State.BLOCKED || stack.any(::isBinderEntry)) }
            .forEach { (thread, stack) ->
                appendLine("\"${thread.name}\" ${thread.state}")
                stack.take(MAX_FRAMES).forEach { appendLine("    at $it") }
            }
    }.take(MAX_CHARS)

    private fun isBinderEntry(frame: StackTraceElement): Boolean =
        frame.className == "android.os.Binder" && frame.methodName == "execTransact"
}
