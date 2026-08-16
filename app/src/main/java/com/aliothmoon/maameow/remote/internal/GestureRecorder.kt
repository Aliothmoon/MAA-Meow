package com.aliothmoon.maameow.remote.internal

import android.os.SystemClock
import com.aliothmoon.maameow.constant.WakeUnlockResult
import com.aliothmoon.maameow.domain.models.GestureRecordResult
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import com.aliothmoon.maameow.utils.JsonUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 解锁手势录制：锁屏 → 自动亮屏 → 采集触摸事件 → keyguard 消失即收尾
 * 录制期间 App 是看不见的，所以做成异步状态机，由 App 侧回到前台后轮询 [poll]
 */
internal object GestureRecorder {

    private const val TAG = "GestureRecorder"

    const val DEFAULT_TIMEOUT_MS = 90_000

    /** 息屏兜底等待；lockAndSleep 内部已经等过一轮 */
    private const val SCREEN_OFF_WAIT_MS = 2_000L

    /** keyguard 消失后再多收一会儿，保证最后一个抬起落袋 */
    private const val TAIL_AFTER_UNLOCK_MS = 200L

    private const val KEYGUARD_SETTLE_MS = 600L

    /** 每次探测都是 binder 往返，息屏期间别一直薅醒 CPU；尾巴有 200ms，这个粒度够用 */
    private const val KEYGUARD_POLL_MS = 250L

    private const val READER_JOIN_MS = 1_000L

    /** 常量结果没必要每次轮询都重新编码 */
    private val IDLE_JSON = encode(GestureRecordResult.IDLE)
    private val RECORDING_JSON = encode(GestureRecordResult.RECORDING)

    @Volatile
    private var worker: Thread? = null

    /** 兼作取消信号与可打断的睡眠闸门 */
    @Volatile
    private var cancelSignal = CountDownLatch(1)

    @Volatile
    private var resultJson: String = IDLE_JSON

    private val cancelled: Boolean get() = cancelSignal.count == 0L

    @Synchronized
    fun start(timeoutMs: Int) {
        if (worker?.isAlive == true) {
            Ln.w("$TAG: already recording")
            return
        }
        cancelSignal = CountDownLatch(1)
        resultJson = RECORDING_JSON
        val budget = if (timeoutMs > 0) timeoutMs.toLong() else DEFAULT_TIMEOUT_MS.toLong()
        worker = Thread({
            val result = runCatching { record(budget) }.getOrElse {
                Ln.e("$TAG: record failed", it)
                GestureRecordResult.failed(WakeUnlockResult.UNSUPPORTED)
            }
            // 取消是 App 发起的，它自己已经收了界面，别再留一份终态等人来取
            resultJson = if (cancelled) IDLE_JSON else encode(result)
            Ln.i("$TAG: finished ${result.status} steps=${result.gesture?.steps?.size ?: 0}")
        }, "gesture-record").apply {
            isDaemon = true
            start()
        }
    }

    /** 终态取走即清：App 侧每次进设置页都会问一次，不能重复消费同一份结果 */
    @Synchronized
    fun poll(): String {
        val snapshot = resultJson
        if (snapshot != IDLE_JSON && snapshot != RECORDING_JSON) {
            resultJson = IDLE_JSON
        }
        return snapshot
    }

    fun cancel() {
        Ln.i("$TAG: cancel requested")
        cancelSignal.countDown()
    }

    private fun encode(result: GestureRecordResult): String =
        JsonUtils.common.encodeToString(GestureRecordResult.serializer(), result)

    private fun record(budgetMs: Long): GestureRecordResult {
        val device = InputDeviceProbe.findTouchDevice()
            ?: return GestureRecordResult.failed(WakeUnlockResult.RECORD_NO_DEVICE)

        // 先把设备读起来再锁屏，免得锁完才发现打不开
        val reader = runCatching { InputEventReader.open(device.path) }.getOrElse {
            Ln.e("$TAG: cannot open ${device.path}", it)
            return GestureRecordResult.failed(WakeUnlockResult.RECORD_NO_DEVICE)
        }

        val deadline = SystemClock.elapsedRealtime() + budgetMs
        val parser = TouchStreamParser()
        val collecting = AtomicBoolean(false)
        val startedAt = AtomicLong(0)

        // 选错节点 / 驱动协议不认时都是零轨迹，靠这两个计数区分是没事件还是没解析出来
        val rawEvents = AtomicLong(0)
        val collectedEvents = AtomicLong(0)

        val readerThread = Thread({
            runCatching {
                reader.readLoop { type, code, value, atMs ->
                    rawEvents.incrementAndGet()
                    if (collecting.get()) {
                        collectedEvents.incrementAndGet()
                        val tMs = (atMs - startedAt.get()).toInt()
                        synchronized(parser) { parser.onEvent(type, code, value, tMs) }
                    }
                }
            }.onFailure { Ln.i("$TAG: reader stopped: ${it.message}") }
        }, "gesture-reader").apply {
            isDaemon = true
            start()
        }

        val outcome = try {
            lockThenAwaitUnlock(deadline, device.path, collecting, startedAt)
        } finally {
            collecting.set(false)
            reader.close()
            readerThread.join(READER_JOIN_MS)
        }

        val screen = outcome.screen
        if (outcome.failure != null || screen == null) {
            return GestureRecordResult.failed(outcome.failure ?: WakeUnlockResult.UNSUPPORTED)
        }

        val elapsed = (SystemClock.uptimeMillis() - startedAt.get()).toInt()
        val strokes = synchronized(parser) {
            parser.finish(elapsed)
            parser.strokes.toList()
        }

        Ln.i(
            "$TAG: captured ${strokes.size} strokes on ${screen.width}x${screen.height}" +
                " rotation=${screen.rotation}," +
                " events raw=${rawEvents.get()} collected=${collectedEvents.get()}"
        )

        val mapped = TouchCoordMapper.mapStrokes(strokes, device, screen)
        val gesture = UnlockGestureCodec.build(mapped, screen)
        if (gesture.steps.isEmpty()) {
            Ln.w("$TAG: no steps parsed from ${device.path} \"${device.name}\"")
            return GestureRecordResult.failed(WakeUnlockResult.RECORD_NO_TOUCH)
        }
        return GestureRecordResult.done(gesture)
    }

    /** [failure] 为 null 表示走完了，此时 [screen] 必定已采样 */
    private class Outcome(val failure: Int?, val screen: ScreenGeometry? = null)

    private fun lockThenAwaitUnlock(
        deadline: Long,
        devicePath: String,
        collecting: AtomicBoolean,
        startedAt: AtomicLong,
    ): Outcome {
        if (cancelled) return Outcome(WakeUnlockResult.RECORD_CANCELLED)

        val locked = WakeUnlockController.lockAndSleep()
        if (locked != WakeUnlockResult.OK) {
            Ln.w("$TAG: cannot lock for recording, code=$locked")
            return Outcome(locked)
        }
        if (cancelled) return Outcome(WakeUnlockResult.RECORD_CANCELLED)

        val pm = ServiceManager.getPowerManager()
        val wm = ServiceManager.getWindowManager()

        // 息屏失败也继续：此时屏还亮着，下面的唤醒是空操作
        if (!pollUntil(SCREEN_OFF_WAIT_MS) { !pm.isScreenOn(0) }) {
            Ln.w("$TAG: screen still on after lock, recording anyway")
        }
        if (cancelled) return Outcome(WakeUnlockResult.RECORD_CANCELLED)

        // 自己亮屏：用户只需做解锁动作本身，双击/抬手唤醒不会被录进来
        // 亮不了就没必要录了，回放走的是同一条唤醒路径
        if (!WakeUnlockController.wakeScreen()) {
            Ln.w("$TAG: screen did not turn on, gesture would be useless")
            return Outcome(WakeUnlockResult.WAKE_FAILED)
        }

        // 必须在录制当时采样：解锁后可能已经转到别的方向，拿桌面的方向映射会整体偏掉
        val screen = ScreenGeometry.current()
        // 紧接着开采，中间不留缓冲，免得漏掉用户的第一下
        startedAt.set(SystemClock.uptimeMillis())
        collecting.set(true)
        Ln.i("$TAG: screen on $screen, collecting from $devicePath")

        // 唤醒瞬间 keyguard 状态会抖，先让它稳下来再等它消失，
        // 否则会把这一瞬的 false 当成「用户已解锁」当场收工
        if (sleepUnlessCancelled(KEYGUARD_SETTLE_MS)) {
            return Outcome(WakeUnlockResult.RECORD_CANCELLED)
        }
        if (wm.isKeyguardLocked != true) {
            Ln.w("$TAG: keyguard already gone right after wake")
            return Outcome(WakeUnlockResult.NO_KEYGUARD)
        }

        val unlocked = awaitCancellable(deadline) { wm.isKeyguardLocked == false }
        if (unlocked != WakeUnlockResult.OK) {
            Ln.w("$TAG: keyguard still locked, code=$unlocked")
            return Outcome(unlocked)
        }

        // keyguard 判定可能早于最后一个抬起，多收一会儿；此处已到手，取消也不再回退
        Thread.sleep(TAIL_AFTER_UNLOCK_MS)
        return Outcome(failure = null, screen = screen)
    }

    /** 等条件成立；返回 OK / RECORD_CANCELLED / RECORD_TIMEOUT */
    private inline fun awaitCancellable(deadline: Long, cond: () -> Boolean): Int {
        while (true) {
            if (cancelled) return WakeUnlockResult.RECORD_CANCELLED
            if (cond()) return WakeUnlockResult.OK
            if (SystemClock.elapsedRealtime() >= deadline) return WakeUnlockResult.RECORD_TIMEOUT
            if (sleepUnlessCancelled(KEYGUARD_POLL_MS)) return WakeUnlockResult.RECORD_CANCELLED
        }
    }

    /** 阻塞在取消闸门上而不是空转轮询；@return true 表示期间被取消 */
    private fun sleepUnlessCancelled(durationMs: Long): Boolean =
        cancelSignal.await(durationMs, TimeUnit.MILLISECONDS)
}
