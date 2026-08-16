package com.aliothmoon.maameow.remote.internal

import android.os.Process
import android.os.SystemClock
import com.aliothmoon.maameow.third.Ln
import java.io.Closeable
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 直读 /dev/input/eventN 的 struct input_event
 * 只允许 arm64-v8a / x86_64，struct 固定 24 字节：timeval(16) + type(2) + code(2) + value(4)
 * 事件自带的时间戳未必与 uptime 同一时钟域，统一用读到的时刻计时
 *
 * 流与时钟都从外部注入，好让字节解码这层能脱离设备测
 */
internal class InputEventReader(
    private val stream: InputStream,
    private val nowMs: () -> Long = { SystemClock.uptimeMillis() },
) : Closeable {

    /** 每个事件都要回调，用 fun interface 走原始类型，免得每次装箱四个对象 */
    fun interface Sink {
        fun onEvent(type: Int, code: Int, value: Int, atMs: Long)
    }

    /** 阻塞读到 [close] 为止；回调在调用线程执行 */
    fun readLoop(sink: Sink) {
        val buffer = ByteArray(EVENT_SIZE * BATCH)
        val view = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        var pending = 0
        while (true) {
            val read = stream.read(buffer, pending, buffer.size - pending)
            if (read <= 0) return
            val total = pending + read
            val now = nowMs()
            var offset = 0
            while (total - offset >= EVENT_SIZE) {
                val type = view.getShort(offset + OFFSET_TYPE).toInt() and 0xFFFF
                val code = view.getShort(offset + OFFSET_CODE).toInt() and 0xFFFF
                val value = view.getInt(offset + OFFSET_VALUE)
                sink.onEvent(type, code, value, now)
                offset += EVENT_SIZE
            }
            // 半个事件留到下一轮拼
            pending = total - offset
            if (pending > 0) System.arraycopy(buffer, offset, buffer, 0, pending)
        }
    }

    /** Android 的 FileInputStream.close 会唤醒阻塞在 read 的线程 */
    override fun close() {
        runCatching { stream.close() }
    }

    companion object {
        const val EVENT_SIZE = 24
        const val OFFSET_TYPE = 16
        const val OFFSET_CODE = 18
        const val OFFSET_VALUE = 20
        private const val BATCH = 64

        fun open(path: String): InputEventReader {
            // APK 只打 arm64-v8a / x86_64，提权进程必然是 64 位；哪天加回 32 位 ABI 要能立刻看出来
            if (!Process.is64Bit()) {
                Ln.e("InputEventReader: 32-bit process, input_event layout mismatch")
            }
            return InputEventReader(FileInputStream(path))
        }
    }
}
