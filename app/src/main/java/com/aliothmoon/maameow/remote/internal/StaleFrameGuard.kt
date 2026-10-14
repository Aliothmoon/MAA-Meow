package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.remote.internal.display.VirtualDisplayManager
import android.os.SystemClock
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.remote.internal.ActivityUtils.DisplayOccupancy
import com.aliothmoon.maameow.remote.internal.StaleFrameDetector.Observation
import com.aliothmoon.maameow.third.Ln

/**
 * 虚拟屏上没人画了，就把帧缓冲里那张残影换成黑帧，免得识别对着它空转
 * 只管后台虚拟屏：前台模式截的是物理屏，应用退了桌面自己会出帧
 */
object StaleFrameGuard {

    private const val TICK_MS = 500L

    /** pidof 要 fork 一个进程，而静止界面会一直停在「帧停了、屏上有任务」这一支上 */
    private const val LIVENESS_INTERVAL_MS = 1_000L

    /** 实测退场动画的帧在强杀返回后 250ms 内还会来 */
    private const val SETTLE_QUIET_MS = 500L
    private const val SETTLE_TIMEOUT_MS = 1_500L
    private const val SETTLE_POLL_MS = 50L

    /** 一拍最慢是一次 pidof，等不到就不等了 */
    private const val STOP_JOIN_MS = 1_000L

    private var worker: Thread? = null

    /** [isRunActive] 返回 false 后自己收摊：一轮自然跑完没有人来调 [stop] */
    @Synchronized
    fun start(isRunActive: () -> Boolean) {
        stop()
        worker = Thread(Watch(isRunActive)).apply {
            name = "stale-frame-guard"
            isDaemon = true
            start()
        }
    }

    /** 返回时守卫线程已退出：[start] 传进来的判活要读 core 实例，调用方接着就可能销毁它 */
    @Synchronized
    fun stop() {
        val thread = worker ?: return
        worker = null
        thread.interrupt()
        if (thread === Thread.currentThread()) return
        runCatching { thread.join(STOP_JOIN_MS) }
        if (thread.isAlive) Ln.w("StaleFrameGuard: worker still running after ${STOP_JOIN_MS}ms")
    }

    private class Watch(private val isRunActive: () -> Boolean) : Runnable {
        private val detector = StaleFrameDetector()
        private var livenessCheckedMs = 0L

        override fun run() {
            try {
                while (!Thread.currentThread().isInterrupted && isRunActive()) {
                    Thread.sleep(TICK_MS)
                    tick()
                }
            } catch (_: InterruptedException) {
            } catch (e: Throwable) {
                Ln.w("StaleFrameGuard: worker failed: ${e.message}")
            }
        }

        private fun tick() {
            val displayId = VirtualDisplayManager.getDisplayId()
            if (displayId == DefaultDisplayConfig.DISPLAY_NONE) return
            // 还在出帧就不会去查：游戏运行期间这里不发任何 binder 调用
            val frameCount = NativeBridgeLib.getFrameCount()
            if (detector.onTick(frameCount) { observe(displayId) } && NativeBridgeLib.blankFrame(frameCount)) {
                Ln.w("StaleFrameGuard: nothing draws display $displayId anymore, frame buffer blanked")
            }
        }

        private fun observe(displayId: Int): Observation? =
            when (val occupancy = ActivityUtils.probeDisplay(displayId)) {
                DisplayOccupancy.Empty -> Observation.GONE
                DisplayOccupancy.Unknown -> Observation.STILL_THERE
                is DisplayOccupancy.Occupied -> {
                    // 任务还挂在屏上但进程没了：被 kill 的应用不会来收自己的窗口
                    val topPackage = occupancy.topPackage
                    if (topPackage == null) Observation.STILL_THERE else observeProcess(topPackage)
                }
            }

        private fun observeProcess(packageName: String): Observation? {
            val now = SystemClock.elapsedRealtime()
            if (now - livenessCheckedMs < LIVENESS_INTERVAL_MS) return null
            livenessCheckedMs = now
            return if (ProcessLiveness.probe(packageName) == ProcessLiveness.DEAD) Observation.GONE
            else Observation.STILL_THERE
        }
    }

    /** 一轮开始时屏上已经空着就直接换：上一轮收尾后退出的应用，轮询要 1.5s 才认得出来 */
    fun blankIfVacant() {
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId == DefaultDisplayConfig.DISPLAY_NONE) return
        val frameCount = NativeBridgeLib.getFrameCount()
        if (ActivityUtils.probeDisplay(displayId) != DisplayOccupancy.Empty) return
        if (NativeBridgeLib.blankFrame(frameCount)) {
            Ln.i("StaleFrameGuard: display $displayId is vacant at run start, frame buffer blanked")
        }
    }

    /**
     * 强杀完占着 [displayId] 的 [killedPackage] 后调，会阻塞到画面停稳：返回后的截图不该再是它的画面
     * 屏上换了别人就不动，它会自己出帧
     */
    fun blankAfterKill(displayId: Int, killedPackage: String) {
        if (displayId == DefaultDisplayConfig.DISPLAY_NONE) return
        if (displayId != VirtualDisplayManager.getDisplayId()) return
        val startedMs = SystemClock.elapsedRealtime()
        val settledCount = awaitFramesSettled(startedMs) ?: return
        val occupancy = ActivityUtils.probeDisplay(displayId)
        if (occupancy is DisplayOccupancy.Occupied && occupancy.topPackage != killedPackage) return
        if (NativeBridgeLib.blankFrame(settledCount)) {
            Ln.i(
                "StaleFrameGuard: $killedPackage on display $displayId was force-stopped, " +
                    "frame buffer blanked after ${SystemClock.elapsedRealtime() - startedMs}ms"
            )
        }
    }

    /** 强杀返回后窗口还要播完退场动画，那几帧画的仍是它；一直有帧就是另有东西在画，返回 null 交给轮询 */
    private fun awaitFramesSettled(startedMs: Long): Long? {
        val settle = FrameSettleDetector(
            quietMs = SETTLE_QUIET_MS,
            timeoutMs = SETTLE_TIMEOUT_MS,
            startCount = NativeBridgeLib.getFrameCount(),
            startMs = startedMs,
        )
        while (true) {
            SystemClock.sleep(SETTLE_POLL_MS)
            when (settle.sample(NativeBridgeLib.getFrameCount(), SystemClock.elapsedRealtime())) {
                FrameSettleDetector.State.SETTLED -> return settle.frameCount
                FrameSettleDetector.State.TIMED_OUT -> return null
                FrameSettleDetector.State.WAITING -> Unit
            }
        }
    }
}
