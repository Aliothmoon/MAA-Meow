package com.aliothmoon.maameow.remote.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDeviceProbeTest {

    @Test
    fun picksMultiTouchScreenOverButtonDevices() {
        val devices = InputDeviceProbe.parse(GETEVENT_OUTPUT)

        val best = devices.first()
        assertEquals("/dev/input/event3", best.path)
        assertEquals("sec_touchscreen", best.name)
        assertEquals(0, best.absXMin)
        assertEquals(1439, best.absXMax)
        assertEquals(0, best.absYMin)
        assertEquals(2959, best.absYMax)
        assertEquals(1440, best.rangeX)
        assertEquals(2960, best.rangeY)
    }

    @Test
    fun devicesWithoutPositionAxesAreDropped() {
        // gpio-keys 只有 KEY，不该混进候选
        val paths = InputDeviceProbe.parse(GETEVENT_OUTPUT).map { it.path }
        assertTrue(paths.none { it == "/dev/input/event0" })
    }

    @Test
    fun legacySingleTouchDeviceIsStillACandidate() {
        val single = InputDeviceProbe.parse(GETEVENT_OUTPUT).firstOrNull {
            it.path == "/dev/input/event5"
        }
        assertEquals(479, single?.absXMax)
        assertEquals(799, single?.absYMax)
        // 多点设备优先，老式单点排在后面
        assertTrue(InputDeviceProbe.parse(GETEVENT_OUTPUT).indexOfFirst {
            it.path == "/dev/input/event5"
        } > 0)
    }

    /**
     * vivo V2046A 实测输出：vivo_ts_fp（屏下指纹上报节点）与 vivo_ts（真触摸屏）量程完全相同，
     * 只能靠 SLOT / TRACKING_ID / BTN_TOUCH / INPUT_PROP_DIRECT 区分；
     * vivo_ts_pen 的量程比触摸屏还大，必须排掉
     */
    @Test
    fun realTouchscreenWinsOverFingerprintNodeWithIdenticalRange() {
        val devices = InputDeviceProbe.parse(VIVO_OUTPUT)

        assertEquals("/dev/input/event5", devices.first().path)
        assertEquals("vivo_ts", devices.first().name)
        assertTrue(devices.none { it.name == "vivo_ts_pen" })
        assertTrue(devices.none { it.name == "algo-prox" })
        assertTrue(devices.none { it.name == "goodixfp" })
    }

    @Test
    fun trackingIdOutweighsDirectAndBtnTouch() {
        // 位置 + tracking id 三件套是主屏的判据，压过 INPUT_PROP_DIRECT 与 BTN_TOUCH
        val output = """
            add device 1: /dev/input/event1
              name:     "touch_no_tracking"
              events:
                KEY (0001): 014a
                ABS (0003): 0035  : value 0, min 0, max 1439, fuzz 0, flat 0, resolution 0
                            0036  : value 0, min 0, max 2959, fuzz 0, flat 0, resolution 0
              input props:
                INPUT_PROP_DIRECT
            add device 2: /dev/input/event2
              name:     "panel"
              events:
                ABS (0003): 0035  : value 0, min 0, max 1439, fuzz 0, flat 0, resolution 0
                            0036  : value 0, min 0, max 2959, fuzz 0, flat 0, resolution 0
                            0039  : value 0, min 0, max 65535, fuzz 0, flat 0, resolution 0
              input props:
                <none>
        """.trimIndent()
        assertEquals("/dev/input/event2", InputDeviceProbe.parse(output).first().path)
    }

    @Test
    fun zeroRangeAxesAreRejected() {
        val output = """
            add device 1: /dev/input/event9
              name:     "broken_touch"
              events:
                ABS (0003): 0035  : value 0, min 0, max 0, fuzz 0, flat 0, resolution 0
                            0036  : value 0, min 0, max 0, fuzz 0, flat 0, resolution 0
        """.trimIndent()
        assertTrue(InputDeviceProbe.parse(output).isEmpty())
    }

    private companion object {
        val VIVO_OUTPUT = """
            add device 1: /dev/input/event6
              name:     "algo-prox"
              events:
                REL (0002): 0002
                ABS (0003): 0002  : value 0, min -2147483648, max 2147483647, fuzz 0, flat 0, resolution 0
              input props:
                <none>
            add device 2: /dev/input/event3
              name:     "vivo_ts_fp"
              events:
                KEY (0001): 00fe
                ABS (0003): 0030  : value 0, min 0, max 31, fuzz 0, flat 0, resolution 0
                            0031  : value 0, min 0, max 31, fuzz 0, flat 0, resolution 0
                            0035  : value 0, min 0, max 1439, fuzz 0, flat 0, resolution 0
                            0036  : value 0, min 0, max 3119, fuzz 0, flat 0, resolution 0
              input props:
                <none>
            add device 3: /dev/input/event5
              name:     "vivo_ts"
              events:
                KEY (0001): 0011  0012  0018  001e  0021  0023  002e  002f
                            0032  0067  0069  006a  008b  008f  009e  00ac
                            00d4  00f9  00fa  00fe  0145  014a  025d  0279
                            0280  02f2  02f3  02f4  02f5  02f6  02f7  02f8
                ABS (0003): 002f  : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                            0030  : value 0, min 0, max 31, fuzz 0, flat 0, resolution 0
                            0031  : value 0, min 0, max 31, fuzz 0, flat 0, resolution 0
                            0035  : value 0, min 0, max 1439, fuzz 0, flat 0, resolution 0
                            0036  : value 0, min 0, max 3119, fuzz 0, flat 0, resolution 0
                            0039  : value 0, min 0, max 65535, fuzz 0, flat 0, resolution 0
              input props:
                INPUT_PROP_DIRECT
            add device 4: /dev/input/event0
              name:     "gpio_keys"
              events:
                KEY (0001): 0072  0073
              input props:
                <none>
            add device 5: /dev/input/event2
              name:     "goodixfp"
              events:
                KEY (0001): 0066  0074  008b  009e  00fe
              input props:
                <none>
            add device 6: /dev/input/event4
              name:     "vivo_ts_pen"
              events:
                KEY (0001): 0140  014a  014b  014c  02f9
                ABS (0003): 0000  : value 0, min 0, max 2880, fuzz 0, flat 0, resolution 0
                            0001  : value 0, min 0, max 6240, fuzz 0, flat 0, resolution 0
                            0018  : value 0, min 0, max 4095, fuzz 0, flat 0, resolution 0
              input props:
                INPUT_PROP_DIRECT
        """.trimIndent()

        val GETEVENT_OUTPUT = """
            add device 1: /dev/input/event0
              name:     "gpio-keys"
              events:
                KEY (0001): 0072  0073  0074
              input props:
                <none>

            add device 2: /dev/input/event3
              name:     "sec_touchscreen"
              events:
                KEY (0001): 0066  008b  009e  00d9  014a
                ABS (0003): 0030  : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                            0032  : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                            0035  : value 0, min 0, max 1439, fuzz 0, flat 0, resolution 0
                            0036  : value 0, min 0, max 2959, fuzz 0, flat 0, resolution 0
                            0039  : value 0, min 0, max 65535, fuzz 0, flat 0, resolution 0
              input props:
                INPUT_PROP_DIRECT

            add device 3: /dev/input/event5
              name:     "old-touchpanel"
              events:
                KEY (0001): 014a
                ABS (0003): 0000  : value 0, min 0, max 479, fuzz 0, flat 0, resolution 0
                            0001  : value 0, min 0, max 799, fuzz 0, flat 0, resolution 0
              input props:
                INPUT_PROP_DIRECT
        """.trimIndent()
    }
}
