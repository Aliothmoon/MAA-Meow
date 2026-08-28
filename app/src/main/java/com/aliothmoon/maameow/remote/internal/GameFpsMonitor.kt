package com.aliothmoon.maameow.remote.internal

import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import java.lang.reflect.Proxy
import kotlin.concurrent.thread

/**
 * 后台模式游戏实时帧率
 *
 * 主路径：Android 13+ `IWindowManager.registerTaskFpsCallback`，SurfaceFlinger 按游戏 task 的图层
 * 每 500ms 推一次 present-to-present 均值；Shell 自 13 起持有 ACCESS_FPS_COUNTER，Root 天然通过；
 * 回退：按 libbridge 收到的合成帧计数做差，虚拟屏上只有游戏，两者口径一致
 */
object GameFpsMonitor {

    private const val TAG = "GameFpsMonitor"
    const val UNKNOWN = -1f

    // SF 只在合成时派发，画面静止就不再上报；超过这个时长视为 0
    private const val TASK_FPS_STALE_MS = 2_000L
    private const val FRAME_COUNT_INTERVAL_MS = 1_000L

    @Volatile
    private var source: Source? = null

    @JvmStatic
    @Synchronized
    fun start(packageName: String) {
        stop()
        val next = createTaskSource(packageName)?.takeIf { it.start() }
            ?: FrameCountSource().also { it.start() }
        source = next
        Ln.i("$TAG: start ${next.name} for $packageName")
    }

    /** 游戏不是由 MaaMeow 拉起时（未启用自动启动），由看门狗探测到在虚拟屏上后补开 */
    @JvmStatic
    @Synchronized
    fun ensureStarted(packageName: String) {
        if (source == null) start(packageName)
    }

    @JvmStatic
    @Synchronized
    fun stop() {
        source?.let {
            it.stop()
            Ln.i("$TAG: stop ${it.name}")
        }
        source = null
    }

    /** 未监控返回 [UNKNOWN] */
    @JvmStatic
    fun currentFps(): Float = source?.currentFps() ?: UNKNOWN

    private fun createTaskSource(packageName: String): TaskFpsSource? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val taskId = ActivityUtils.findTaskId(packageName) ?: run {
            Ln.w("$TAG: no task for $packageName, fall back to frame count")
            return null
        }
        return TaskFpsSource(taskId)
    }

    private interface Source {
        val name: String

        /** 失败返回 false，调用方换下一种来源 */
        fun start(): Boolean
        fun stop()
        fun currentFps(): Float
    }

    /** ITaskFpsCallback 的手工 Binder：AIDL 只有一个 oneway onFpsReported(float)，不必引入隐藏类副本 */
    private class TaskFpsSource(private val taskId: Int) : Source {

        override val name = "TaskFps(task=$taskId)"

        @Volatile
        private var fps = 0f

        @Volatile
        private var lastReportMs = 0L

        private val binder = object : Binder() {
            init {
                attachInterface(null, DESCRIPTOR)
            }

            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != TRANSACTION_ON_FPS_REPORTED) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(DESCRIPTOR)
                fps = data.readFloat()
                lastReportMs = SystemClock.elapsedRealtime()
                return true
            }
        }

        // 反射调用 registerTaskFpsCallback 需要一个实现框架侧 ITaskFpsCallback 接口的对象；
        // system_server 拿到的是 Proxy，回调只会经 asBinder → onTransact 进来
        private val callback: Any? = runCatching {
            val iface = Class.forName("android.window.ITaskFpsCallback")
            Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { proxy, method, args ->
                when (method.name) {
                    "asBinder" -> binder
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args[0]
                    "toString" -> name
                    else -> null
                }
            }
        }.getOrNull()

        override fun start(): Boolean {
            val cb = callback ?: return false
            val ok = ServiceManager.getWindowManager().registerTaskFpsCallback(taskId, cb)
            if (ok) lastReportMs = SystemClock.elapsedRealtime()
            else Ln.w("$TAG: registerTaskFpsCallback failed, fall back to frame count")
            return ok
        }

        override fun stop() {
            callback?.let { ServiceManager.getWindowManager().unregisterTaskFpsCallback(it) }
        }

        override fun currentFps(): Float =
            if (SystemClock.elapsedRealtime() - lastReportMs > TASK_FPS_STALE_MS) 0f else fps

        private companion object {
            const val DESCRIPTOR = "android.window.ITaskFpsCallback"
            const val TRANSACTION_ON_FPS_REPORTED = IBinder.FIRST_CALL_TRANSACTION
        }
    }

    /** 按 libbridge 合成帧计数每秒做差 */
    private class FrameCountSource : Source {

        override val name = "FrameCount"

        @Volatile
        private var fps = 0f

        @Volatile
        private var running = false
        private var worker: Thread? = null

        override fun start(): Boolean {
            running = true
            worker = thread(name = "game-fps-sampler", isDaemon = true) {
                val estimator = FrameCountFpsEstimator()
                estimator.sample(NativeBridgeLib.getFrameCount(), System.nanoTime())
                while (running) {
                    try {
                        Thread.sleep(FRAME_COUNT_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                    estimator.sample(NativeBridgeLib.getFrameCount(), System.nanoTime())?.let { fps = it }
                }
            }
            return true
        }

        override fun stop() {
            running = false
            worker?.interrupt()
            worker = null
        }

        override fun currentFps(): Float = fps
    }
}
