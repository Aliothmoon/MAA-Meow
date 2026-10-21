package com.aliothmoon.maameow.bridge

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.view.isInvisible
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.random.Random

/**
 * 远端用单线程执行器模拟 oneway：按序处理，但可能晚于 App 拆掉 SurfaceView
 * Surface 发出时先过一遍 Parcel，和 binder 送过去的副本一样不受 App 端 release 影响
 */
@RunWith(AndroidJUnit4::class)
class PreviewSurfaceRaceTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(120)

    private lateinit var remote: ExecutorService
    private lateinit var source: Surface
    private lateinit var producer: Thread

    @Volatile
    private var producing = true
    private val sinks = CopyOnWriteArrayList<Sink>()

    @Before
    fun setUp() {
        assumeTrue(NativeBridgeLib.LOADED)
        remote = Executors.newSingleThreadExecutor { Thread(it, "fake-binder") }
        source = checkNotNull(NativeBridgeLib.setupNativeCapturer(W, H))
        producing = true
        producer = thread(name = "frame-source") {
            while (producing) {
                runCatching {
                    val canvas = source.lockCanvas(null)
                    canvas.drawColor(SOURCE_COLOR)
                    source.unlockCanvasAndPost(canvas)
                }
                Thread.sleep(8)
            }
        }
    }

    @After
    fun tearDown() {
        if (!::remote.isInitialized) return
        producing = false
        producer.join(2_000)
        send(null)
        drainRemote()
        remote.shutdown()
        sinks.forEach { it.close() }
        NativeBridgeLib.releaseNativeCapturer()
    }

    @Test
    fun attachArrivingAfterConsumerIsGone_nextSurfaceStillRenders() {
        val gone = Sink()
        send(gone.surface, remoteDelayMs = 50)
        gone.close()
        Thread.sleep(300)
        send(null)

        val next = Sink()
        send(next.surface)
        assertTrue(next.awaitFrames(5))
    }

    @Test
    fun consumerGoneWhileRendering_lateDetach_nextSurfaceStillRenders() {
        val first = Sink()
        send(first.surface)
        assertTrue(first.awaitFrames(5))
        first.close()
        Thread.sleep(300)
        send(null)

        val next = Sink()
        send(next.surface)
        assertTrue(next.awaitFrames(5))
    }

    @Test
    fun sameQueueReattachedRightAfterDetach_reconnects() {
        val sink = Sink()
        send(sink.surface)
        assertTrue(sink.awaitFrames(5))
        repeat(20) { round ->
            send(null)
            send(sink.surface)
            assertTrue("round $round", sink.awaitFrames(3))
        }
    }

    @Test
    fun rapidSwitchingWithRandomTiming_staysResponsiveAndDoesNotLeak() {
        val seed = System.nanoTime()
        val random = Random(seed)
        val fdBefore = openFds()
        val blanking = AtomicBoolean(true)
        val blanker = thread(name = "stale-guard") {
            while (blanking.get()) {
                NativeBridgeLib.blankFrame(NativeBridgeLib.getFrameCount())
                Thread.sleep(15)
            }
        }
        val lateClose = ArrayDeque<Sink>()
        val start = SystemClock.uptimeMillis()

        repeat(ROUNDS) {
            val sink = Sink()
            send(sink.surface, remoteDelayMs = random.nextLong(0, 3))
            Thread.sleep(random.nextLong(0, 6))
            when (random.nextInt(4)) {
                3 -> lateClose += sink
                0 -> {
                    sink.close()
                    send(null, remoteDelayMs = random.nextLong(0, 3))
                }
                1 -> {
                    send(null, remoteDelayMs = random.nextLong(0, 3))
                    sink.close()
                }
                else -> {
                    send(null, remoteDelayMs = random.nextLong(0, 3))
                    lateClose += sink
                }
            }
            while (lateClose.size > 5) lateClose.removeFirst().close()
        }
        lateClose.forEach { it.close() }
        drainRemote(10_000)
        blanking.set(false)
        blanker.join(1_000)
        Log.i(TAG, "rapid switching: $ROUNDS rounds in ${SystemClock.uptimeMillis() - start}ms, seed=$seed")

        val last = Sink()
        send(last.surface)
        assertTrue("seed=$seed", last.awaitFrames(5))

        // 渲染线程漏了 ANativeWindow 会攥着缓冲不放，fd 回不到原位
        last.close()
        send(null)
        drainRemote()
        Thread.sleep(500)
        repeat(2) {
            Runtime.getRuntime().gc()
            System.runFinalization()
        }
        val fdAfter = openFds()
        Log.i(TAG, "fd $fdBefore -> $fdAfter")
        assertTrue("fd $fdBefore -> $fdAfter, seed=$seed", fdAfter - fdBefore < FD_SLACK)
    }

    @Test
    fun shutdownOverlappingSurfaceSwitching_doesNotDeadlock_thenRestarts() {
        val seed = System.nanoTime()
        val random = Random(seed)
        val first = Sink()
        send(first.surface)
        assertTrue(first.awaitFrames(3))

        val switching = thread(name = "app-main") {
            repeat(100) {
                val sink = Sink()
                send(sink.surface, remoteDelayMs = random.nextLong(0, 3))
                Thread.sleep(random.nextLong(0, 4))
                send(null)
                if (random.nextBoolean()) sink.close()
            }
        }
        Thread.sleep(random.nextLong(0, 60))
        val shutdown = thread(name = "preview-shutdown") { NativeBridgeLib.shutdownPreview() }
        shutdown.join(5_000)
        assertFalse("shutdown hung, seed=$seed", shutdown.isAlive)
        switching.join(10_000)
        assertFalse("switching hung, seed=$seed", switching.isAlive)
        drainRemote()

        val next = Sink()
        send(next.surface)
        assertTrue("seed=$seed", next.awaitFrames(5))
    }

    @Test
    fun surfaceViewVisibilityToggles_previewEndsUpOnScreen() {
        val seed = System.nanoTime()
        val random = Random(seed)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var view: SurfaceView
            scenario.onActivity { activity ->
                view = SurfaceView(activity).apply {
                    holder.setFormat(PixelFormat.RGBA_8888)
                    holder.setFixedSize(W, H)
                    holder.addCallback(AppLikeCallback())
                }
                activity.setContentView(FrameLayout(activity).apply {
                    addView(view, FrameLayout.LayoutParams(W, H))
                })
            }
            assertTrue("seed=$seed", awaitColorOnScreen(view))

            repeat(TOGGLES) { i ->
                scenario.onActivity { view.isInvisible = i % 2 == 0 }
                Thread.sleep(random.nextLong(0, 25))
            }
            scenario.onActivity { view.isInvisible = false }
            assertTrue("seed=$seed", awaitColorOnScreen(view))
            drainRemote()
        }
    }

    /** 同 BackgroundTaskView：尺寸对上才发，销毁时发 null 再 release */
    private inner class AppLikeCallback : SurfaceHolder.Callback {
        private var lastSent: Surface? = null

        override fun surfaceCreated(holder: SurfaceHolder) = Unit

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (width == W && height == H && lastSent != holder.surface) {
                lastSent = holder.surface
                send(holder.surface)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            lastSent = null
            send(null)
            holder.surface.release()
        }
    }

    private fun send(surface: Surface?, remoteDelayMs: Long = 0) {
        val copy = surface?.parcelCopy()
        remote.execute {
            if (remoteDelayMs > 0) Thread.sleep(remoteDelayMs)
            NativeBridgeLib.setPreviewSurface(copy)
        }
    }

    private fun drainRemote(timeoutMs: Long = 5_000) {
        remote.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private fun Surface.parcelCopy(): Surface {
        val parcel = Parcel.obtain()
        try {
            writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return Surface.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private fun openFds(): Int = File("/proc/self/fd").list()?.size ?: -1

    private fun awaitColorOnScreen(view: SurfaceView, timeoutMs: Long = 5_000): Boolean {
        val handler = Handler(Looper.getMainLooper())
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val latch = CountDownLatch(1)
            var result = -1
            // Surface 还没建好时直接抛异常，不走回调
            val requested = runCatching {
                PixelCopy.request(view, bitmap, { result = it; latch.countDown() }, handler)
            }.isSuccess
            if (requested) latch.await(1, TimeUnit.SECONDS)
            if (result == PixelCopy.SUCCESS && bitmap.getPixel(W / 2, H / 2).isCloseTo(SOURCE_COLOR)) {
                return true
            }
            Thread.sleep(50)
        }
        return false
    }

    private fun Int.isCloseTo(other: Int): Boolean =
        abs(Color.red(this) - Color.red(other)) <= 12 &&
            abs(Color.green(this) - Color.green(other)) <= 12 &&
            abs(Color.blue(this) - Color.blue(other)) <= 12

    /** close 即 App 拆掉 SurfaceView */
    private inner class Sink : AutoCloseable {
        private val looper = HandlerThread("preview-sink").apply { start() }
        private val reader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 2)
        val frames = AtomicInteger()
        private var closed = false
        val surface: Surface get() = reader.surface

        init {
            reader.setOnImageAvailableListener({ r ->
                frames.incrementAndGet()
                // EGL 按 config 定缓冲格式，和 reader 对不上时拒收，但缓冲照样还回队列
                runCatching { r.acquireLatestImage()?.close() }
            }, Handler(looper.looper))
            sinks += this
        }

        fun awaitFrames(count: Int, timeoutMs: Long = 3_000): Boolean {
            val target = frames.get() + count
            val deadline = SystemClock.uptimeMillis() + timeoutMs
            while (SystemClock.uptimeMillis() < deadline) {
                if (frames.get() >= target) return true
                Thread.sleep(10)
            }
            return false
        }

        override fun close() {
            if (closed) return
            closed = true
            reader.close()
            looper.quitSafely()
        }
    }

    private companion object {
        const val TAG = "PreviewSurfaceRaceTest"
        const val W = 320
        const val H = 180
        const val ROUNDS = 300
        const val TOGGLES = 120
        const val FD_SLACK = 64
        val SOURCE_COLOR = Color.rgb(255, 0, 255)
    }
}
