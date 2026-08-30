package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.third.Ln
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 用 getevent -p 找触摸屏并拿到 ABS 量程
 * 不走 ioctl 是为了免掉一层 JNI
 *
 * 按结构特征打分而不是名字：同一块屏可能挂着好几个节点，
 * 例如 vivo 的 vivo_ts（真触摸屏）与 vivo_ts_fp（屏下指纹区域），
 * 两者量程完全相同，只有 SLOT / TRACKING_ID / BTN_TOUCH / INPUT_PROP_DIRECT 能把它们区分开
 */
internal object InputDeviceProbe {

    private const val TAG = "InputDeviceProbe"
    private const val GETEVENT_TIMEOUT_S = 5L

    private val DEVICE_LINE = Regex("""^add device \d+:\s*(\S+)""")
    private val NAME_LINE = Regex("""^\s*name:\s*"(.*)"\s*$""")
    private val SECTION_LINE = Regex("""^\s*([A-Z]{2,3})\s*\(\d{4}\):(.*)$""")
    private val ABS_LINE =
        Regex("""([0-9a-fA-F]{4})\s*:\s*value\s+-?\d+,\s*min\s+(-?\d+),\s*max\s+(-?\d+)""")
    private val HEX_TOKEN = Regex("""\b[0-9a-fA-F]{4}\b""")

    private const val ABS_X = TouchStreamParser.ABS_X
    private const val ABS_Y = TouchStreamParser.ABS_Y
    private const val ABS_MT_SLOT = TouchStreamParser.ABS_MT_SLOT
    private const val ABS_MT_POSITION_X = TouchStreamParser.ABS_MT_POSITION_X
    private const val ABS_MT_POSITION_Y = TouchStreamParser.ABS_MT_POSITION_Y
    private const val ABS_MT_TRACKING_ID = TouchStreamParser.ABS_MT_TRACKING_ID
    private const val BTN_TOUCH = TouchStreamParser.BTN_TOUCH
    private const val BTN_TOOL_PEN = 0x140

    fun findTouchDevice(): TouchDeviceInfo? {
        val output = runGetevent()
        if (output == null) {
            Ln.w("$TAG: getevent -p produced nothing")
            logProcInputDevices()
            return null
        }
        val candidates = parse(output)
        val info = candidates.firstOrNull { File(it.path).canRead() }
        if (info == null) {
            Ln.w("$TAG: no readable touchscreen in getevent output")
            logProcInputDevices()
            return null
        }
        // 选错节点会静默录不到东西，把落选的也记下来便于排查
        candidates.drop(1).forEach { Ln.i("$TAG: candidate ${it.path} \"${it.name}\"") }
        Ln.i(
            "$TAG: using ${info.path} \"${info.name}\"" +
                    " x=[${info.absXMin},${info.absXMax}] y=[${info.absYMin},${info.absYMax}]"
        )
        return info
    }

    private class Candidate(val info: TouchDeviceInfo, val score: Long)

    private class Device {
        var name = ""
        var direct = false
        val abs = mutableMapOf<Int, IntArray>()
        val keys = mutableSetOf<Int>()
    }

    /** getevent -p 的完整输出 → 候选触摸屏，最像的排在最前 */
    fun parse(output: String): List<TouchDeviceInfo> {
        val candidates = mutableListOf<Candidate>()
        var path: String? = null
        var device = Device()
        var section: String? = null

        fun flush() {
            val devPath = path ?: return
            candidates += buildCandidate(devPath, device) ?: return
        }

        for (line in output.lineSequence()) {
            val trimmed = line.trim()

            val header = DEVICE_LINE.find(trimmed)
            if (header != null) {
                flush()
                path = header.groupValues[1]
                device = Device()
                section = null
                continue
            }
            if (path == null) continue

            val nameMatch = NAME_LINE.find(line)
            if (nameMatch != null) {
                device.name = nameMatch.groupValues[1]
                section = null
                continue
            }
            if (trimmed.startsWith("events:")) {
                section = null
                continue
            }
            if (trimmed.startsWith("input props:")) {
                section = SECTION_PROPS
                continue
            }

            // 段标题自带首个条目，后续行是缩进的续行
            val sectionMatch = SECTION_LINE.find(line)
            val content = if (sectionMatch != null) {
                section = sectionMatch.groupValues[1]
                sectionMatch.groupValues[2]
            } else {
                line
            }

            when (section) {
                SECTION_ABS -> ABS_LINE.find(content)?.let {
                    device.abs[it.groupValues[1].toInt(16)] = intArrayOf(
                        it.groupValues[2].toInt(),
                        it.groupValues[3].toInt(),
                    )
                }

                SECTION_KEY -> HEX_TOKEN.findAll(content)
                    .forEach { device.keys += it.value.toInt(16) }

                SECTION_PROPS -> if (content.contains("INPUT_PROP_DIRECT")) device.direct = true
            }
        }
        flush()
        return candidates.sortedByDescending { it.score }.map { it.info }
    }

    private fun buildCandidate(path: String, device: Device): Candidate? {
        // 触控笔量程往往比触摸屏还大，先排掉
        if (BTN_TOOL_PEN in device.keys) return null

        val mtX = device.abs[ABS_MT_POSITION_X]
        val mtY = device.abs[ABS_MT_POSITION_Y]
        val multiTouch = mtX != null && mtY != null
        val x = mtX ?: device.abs[ABS_X] ?: return null
        val y = mtY ?: device.abs[ABS_Y] ?: return null

        val info = TouchDeviceInfo(
            path = path,
            name = device.name,
            absXMin = x[0],
            absXMax = x[1],
            absYMin = y[0],
            absYMax = y[1],
        )
        if (!info.usable) return null

        var score = info.rangeX.toLong() * info.rangeY
        // 主屏几乎必然是 MT 协议 B：位置 + tracking id 三件套齐全就是它，
        // 附属节点（屏下指纹上报区等）恰恰缺 tracking id
        if (multiTouch && ABS_MT_TRACKING_ID in device.abs) score += PROTOCOL_B_BONUS
        if (device.direct) score += DIRECT_BONUS
        if (BTN_TOUCH in device.keys) score += TOUCH_KEY_BONUS
        if (ABS_MT_SLOT in device.abs) score += SLOT_BONUS
        if (multiTouch) score += MULTI_TOUCH_BONUS
        if (device.name.contains("touch", ignoreCase = true)) score += NAME_BONUS
        return Candidate(info, score)
    }

    private fun runGetevent(): String? = runCatching {
        val process = ProcessBuilder("getevent", "-p").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(GETEVENT_TIMEOUT_S, TimeUnit.SECONDS)) {
            process.destroy()
        }
        text.ifBlank { null }
    }.onFailure {
        Ln.w("$TAG: getevent failed: ${it.message}")
    }.getOrNull()

    /** 探测失败时留点排查线索：至少能看出设备存不存在、是不是权限问题 */
    private fun logProcInputDevices() {
        runCatching {
            val devices = File("/proc/bus/input/devices")
            if (!devices.canRead()) {
                Ln.w("$TAG: /proc/bus/input/devices unreadable")
                return
            }
            devices.readLines()
                .filter { it.startsWith("N: ") || it.startsWith("H: ") }
                .take(PROC_LOG_LIMIT)
                .forEach { Ln.i("$TAG: $it") }
            File("/dev/input").listFiles()
                ?.filter { it.name.startsWith("event") }
                ?.forEach { Ln.i("$TAG: ${it.path} readable=${it.canRead()}") }
        }.onFailure {
            Ln.w("$TAG: cannot dump /proc/bus/input/devices: ${it.message}")
        }
    }

    private const val SECTION_ABS = "ABS"
    private const val SECTION_KEY = "KEY"
    private const val SECTION_PROPS = "PROPS"

    // 结构特征远比量程与名字可信，权重拉开档位
    private const val PROTOCOL_B_BONUS = 100_000_000_000L
    private const val DIRECT_BONUS = 8_000_000_000L
    private const val TOUCH_KEY_BONUS = 4_000_000_000L
    private const val SLOT_BONUS = 1_000_000_000L
    private const val MULTI_TOUCH_BONUS = 500_000_000L
    private const val NAME_BONUS = 100_000_000L
    private const val PROC_LOG_LIMIT = 40
}
