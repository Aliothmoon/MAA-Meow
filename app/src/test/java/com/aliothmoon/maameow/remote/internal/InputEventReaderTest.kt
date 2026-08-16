package com.aliothmoon.maameow.remote.internal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * struct input_event 的字节解码，是最容易在真机上静默错掉的一层：
 * 偏移写错只会表现为「一条轨迹都没采到」，看不出是解码问题
 */
class InputEventReaderTest {

    @Test
    fun decodesTypeCodeValueAtCorrectOffsets() {
        val events = drain(
            bytes(
                event(TouchStreamParser.EV_ABS, TouchStreamParser.ABS_MT_POSITION_X, 1439),
                event(TouchStreamParser.EV_ABS, TouchStreamParser.ABS_MT_POSITION_Y, 3119),
                event(TouchStreamParser.EV_SYN, TouchStreamParser.SYN_REPORT, 0),
            ),
        )

        assertEquals(
            listOf(
                Triple(TouchStreamParser.EV_ABS, TouchStreamParser.ABS_MT_POSITION_X, 1439),
                Triple(TouchStreamParser.EV_ABS, TouchStreamParser.ABS_MT_POSITION_Y, 3119),
                Triple(TouchStreamParser.EV_SYN, TouchStreamParser.SYN_REPORT, 0),
            ),
            events,
        )
    }

    @Test
    fun negativeValueSurvives() {
        // 抬起靠 ABS_MT_TRACKING_ID = -1 判定，符号位丢了就永远不抬手
        val events = drain(bytes(event(TouchStreamParser.EV_ABS, TouchStreamParser.ABS_MT_TRACKING_ID, -1)))
        assertEquals(-1, events.single().third)
    }

    @Test
    fun codeAboveShortRangeIsUnsigned() {
        // BTN_TOUCH = 0x14a，按有符号读也没错；这里锁死高位 code 不会变负
        val events = drain(bytes(event(TouchStreamParser.EV_KEY, 0xFFFE, 1)))
        assertEquals(0xFFFE, events.single().second)
    }

    @Test
    fun eventSplitAcrossReadsIsReassembled() {
        // evdev 的一次 read 未必落在事件边界上，半个事件必须留到下一轮拼
        val payload = bytes(
            event(TouchStreamParser.EV_ABS, TouchStreamParser.ABS_MT_POSITION_X, 111),
            event(TouchStreamParser.EV_ABS, TouchStreamParser.ABS_MT_POSITION_Y, 222),
        )
        val events = drain(ChunkedStream(payload, chunkSize = 7))

        assertEquals(
            listOf(
                Triple(TouchStreamParser.EV_ABS, TouchStreamParser.ABS_MT_POSITION_X, 111),
                Triple(TouchStreamParser.EV_ABS, TouchStreamParser.ABS_MT_POSITION_Y, 222),
            ),
            events,
        )
    }

    @Test
    fun trailingPartialEventIsDropped() {
        val payload = bytes(event(TouchStreamParser.EV_SYN, TouchStreamParser.SYN_REPORT, 0)) +
            ByteArray(5)
        assertEquals(1, drain(payload).size)
    }

    @Test
    fun readTimeIsStampedPerBatch() {
        val payload = bytes(event(TouchStreamParser.EV_SYN, TouchStreamParser.SYN_REPORT, 0))
        val stamps = mutableListOf<Long>()
        InputEventReader(ByteArrayInputStream(payload), nowMs = { 12_345L }).use { reader ->
            reader.readLoop { _, _, _, atMs -> stamps += atMs }
        }
        assertEquals(listOf(12_345L), stamps)
    }

    /** 单个 24 字节 input_event：timeval(16) + type(2) + code(2) + value(4)，小端 */
    private fun event(type: Int, code: Int, value: Int): ByteArray {
        val buffer = ByteBuffer.allocate(InputEventReader.EVENT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putLong(0, 1_700_000_000L)
        buffer.putLong(8, 654_321L)
        buffer.putShort(InputEventReader.OFFSET_TYPE, type.toShort())
        buffer.putShort(InputEventReader.OFFSET_CODE, code.toShort())
        buffer.putInt(InputEventReader.OFFSET_VALUE, value)
        return buffer.array()
    }

    private fun bytes(vararg events: ByteArray): ByteArray {
        val out = ByteArray(events.sumOf { it.size })
        var offset = 0
        for (e in events) {
            e.copyInto(out, offset)
            offset += e.size
        }
        return out
    }

    private fun drain(payload: ByteArray): List<Triple<Int, Int, Int>> =
        drain(ByteArrayInputStream(payload))

    private fun drain(stream: InputStream): List<Triple<Int, Int, Int>> {
        val out = mutableListOf<Triple<Int, Int, Int>>()
        InputEventReader(stream, nowMs = { 0L }).use { reader ->
            reader.readLoop { type, code, value, _ -> out += Triple(type, code, value) }
        }
        return out
    }

    /** 每次只吐 [chunkSize] 字节，模拟 read 落在事件中间 */
    private class ChunkedStream(private val data: ByteArray, private val chunkSize: Int) :
        InputStream() {
        private var position = 0

        override fun read(): Int =
            if (position >= data.size) -1 else data[position++].toInt() and 0xFF

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(chunkSize, len, data.size - position)
            data.copyInto(b, off, position, position + count)
            position += count
            return count
        }
    }
}
