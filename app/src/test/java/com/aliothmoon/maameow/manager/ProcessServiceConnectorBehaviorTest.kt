package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.IBinder
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.root.BootstrapRegistry
import io.mockk.CapturingSlot
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * 进程连接器基座行为：IPC 状态机高变更区，失败 / 竞态路径必须有真行为测试兜底
 */
class ProcessServiceConnectorBehaviorTest {

    private val logFile = File.createTempFile("connector", ".log").apply { delete() }

    @After
    fun tearDown() {
        logFile.delete()
    }

    private class FakeRegistry : BootstrapRegistry {
        val pending = ConcurrentHashMap<String, CompletableDeferred<IBinder>>()

        override fun register(token: String): CompletableDeferred<IBinder> =
            CompletableDeferred<IBinder>().also { pending[token] = it }

        override fun unregister(token: String) {
            pending.remove(token)?.cancel()
        }

        /** 模拟 provider 回投；token 已注销返回 false */
        fun attach(token: String, binder: IBinder): Boolean =
            pending.remove(token)?.complete(binder) == true
    }

    private class FakeHandle(private val alive: Boolean, private val code: Int?) : SpawnHandle {
        override fun isAlive(): Boolean = alive
        override fun exitCode(): Int? = code
    }

    private class FakeSpawner : ProcessSpawner {
        var handle: SpawnHandle? = null
        var spawnError: Throwable? = null
        var onSpawn: () -> Unit = {}
        val spawnCalls = AtomicInteger()
        private val kills = LinkedBlockingQueue<String>()

        override fun wrapCommand(launcherPath: String, invocation: String): String = invocation

        override fun spawn(command: String): SpawnHandle? {
            spawnCalls.incrementAndGet()
            onSpawn()
            spawnError?.let { throw it }
            return handle
        }

        override fun killResidual(processName: String) {
            kills.add(processName)
        }

        fun awaitKill(): Boolean = kills.poll(2, TimeUnit.SECONDS) != null
        fun killCount(): Int = kills.size
    }

    private class FakeConnector(
        spawner: ProcessSpawner,
        registry: BootstrapRegistry,
        private val logFile: File,
        timeoutMs: Long,
    ) : ProcessServiceConnectorBackend(spawner, registry) {
        override val backend = RemoteBackend.ROOT
        override val eventPrefix = "FAKE"
        override val processNameSuffix = "fake"
        override val serviceClass: Class<*> = Any::class.java
        override val logFileName = "fake.log"
        override val spawnTimeoutMs = timeoutMs

        private val tokens = LinkedBlockingQueue<String>()

        override fun buildStartCommand(token: String, logFile: File): String {
            tokens.add(token)
            return "fake-cmd"
        }

        override fun debugLogFile(): File = logFile

        override fun destroyRemote(binder: IBinder) = Unit

        fun awaitToken(): String = tokens.poll(5, TimeUnit.SECONDS) ?: error("token never assigned")
    }

    private class RecordingCallbacks : RemoteServiceConnectorBackend.Callbacks {
        val connected = mutableListOf<IBinder>()
        val errors = mutableListOf<Throwable>()
        val disconnected = AtomicInteger()
        private val connectedLatch = CountDownLatch(1)
        private val errorLatch = CountDownLatch(1)

        override fun onConnected(backend: RemoteBackend, binder: IBinder) {
            connected += binder
            connectedLatch.countDown()
        }

        override fun onDisconnected(backend: RemoteBackend) {
            disconnected.incrementAndGet()
        }

        override fun onError(backend: RemoteBackend, throwable: Throwable) {
            errors += throwable
            errorLatch.countDown()
        }

        fun awaitConnected() = connectedLatch.await(5, TimeUnit.SECONDS)
        fun awaitError() = errorLatch.await(5, TimeUnit.SECONDS)
    }

    private inner class Harness(timeoutMs: Long = 300L) {
        val spawner = FakeSpawner()
        val registry = FakeRegistry()
        val callbacks = RecordingCallbacks()
        val connector = FakeConnector(spawner, registry, logFile, timeoutMs).apply {
            initialize(mockk<Context>(relaxed = true))
        }

        /** 连接并回投一个 binder，返回其死亡回调 */
        fun connectAndAttach(): CapturingSlot<IBinder.DeathRecipient> {
            val binder = mockk<IBinder>(relaxed = true)
            val recipient = slot<IBinder.DeathRecipient>()
            justRun { binder.linkToDeath(capture(recipient), any()) }
            connector.connect(callbacks)
            assertTrue(registry.attach(connector.awaitToken(), binder))
            assertTrue(callbacks.awaitConnected())
            return recipient
        }
    }

    @Test
    fun spawnFailure_reportsOnceWithoutRetry() {
        val h = Harness()
        h.spawner.spawnError = IllegalStateException("launcher missing")

        h.connector.connect(h.callbacks)
        assertTrue(h.callbacks.awaitError())

        assertEquals(1, h.spawner.spawnCalls.get())
        assertEquals("launcher missing", h.callbacks.errors.single().message)
        assertTrue("失败后 token 必须注销", h.registry.pending.isEmpty())
        assertEquals("没拉起来就没有残留可清", 0, h.spawner.killCount())
    }

    @Test
    fun binderNeverArrives_timesOutAsTimeoutException() {
        val h = Harness(timeoutMs = 300L)

        h.connector.connect(h.callbacks)
        assertTrue(h.callbacks.awaitError())

        val error = h.callbacks.errors.single()
        assertTrue("超时必须转成 TimeoutException 而非泄漏 CancellationException", error is TimeoutException)
        assertTrue(error.message!!.contains("300ms"))
        assertTrue("超时必须清残留进程", h.spawner.awaitKill())
    }

    @Test
    fun launcherLogTail_isAppendedToFailure() {
        val h = Harness(timeoutMs = 300L)
        h.spawner.onSpawn = { logFile.writeText("[I] launcher start\n[E] execv failed: EACCES\n") }

        h.connector.connect(h.callbacks)
        assertTrue(h.callbacks.awaitError())

        val message = h.callbacks.errors.single().message!!
        assertTrue(message, message.contains("launcher log tail: [I] launcher start | [E] execv failed: EACCES"))
    }

    @Test
    fun processExitsBeforeBinder_failsFastWithExitCode() {
        val h = Harness(timeoutMs = 10_000L)
        h.spawner.handle = FakeHandle(alive = false, code = 126)

        val start = System.currentTimeMillis()
        h.connector.connect(h.callbacks)
        assertTrue(h.callbacks.awaitError())
        val elapsed = System.currentTimeMillis() - start

        val error = h.callbacks.errors.single()
        assertTrue("进程先退出必须秒级失败而非等满超时 (elapsed=${elapsed}ms)", elapsed < 3_000)
        assertTrue(error is ProcessExitedException)
        assertEquals(126, (error as ProcessExitedException).exitCode)
        assertTrue(h.spawner.awaitKill())
    }

    @Test
    fun processAliveUntilBinder_connects() {
        val h = Harness(timeoutMs = 5_000L)
        h.spawner.handle = FakeHandle(alive = true, code = null)

        h.connectAndAttach()

        assertEquals(1, h.callbacks.connected.size)
        assertTrue(h.callbacks.errors.isEmpty())
        assertFalse(h.connector.isConnecting)
        assertEquals("成功路径不得清进程", 0, h.spawner.killCount())
    }

    @Test
    fun disconnectThenLateBinder_isDropped() {
        val h = Harness(timeoutMs = 5_000L)
        h.connector.connect(h.callbacks)
        val token = h.connector.awaitToken()

        h.connector.disconnect(null)

        assertFalse("disconnect 后 token 应已注销", h.registry.attach(token, mockk(relaxed = true)))
        assertTrue("迟到的 binder 不得触发 onConnected", h.callbacks.connected.isEmpty())
        assertTrue("未连上就断开必须清残留进程", h.spawner.awaitKill())
    }

    @Test
    fun deathOfOldToken_isIgnored() {
        val h = Harness(timeoutMs = 5_000L)
        val recipient = h.connectAndAttach()

        // 第二次连接发起后，旧 binder 的死讯必须被丢弃（回调同步，无需等待）
        h.connector.connect(h.callbacks)
        h.connector.awaitToken()
        recipient.captured.binderDied()

        assertEquals("旧 token 死讯不得触发 onDisconnected", 0, h.callbacks.disconnected.get())
        h.connector.disconnect(null)
    }

    @Test
    fun deathOfCurrentToken_reportsDisconnected() {
        val h = Harness(timeoutMs = 5_000L)
        val recipient = h.connectAndAttach()

        recipient.captured.binderDied()

        assertEquals("当前连接死讯必须上抛", 1, h.callbacks.disconnected.get())
    }

    @Test
    fun binderFromSupersededConnect_isDropped() {
        val h = Harness(timeoutMs = 5_000L)
        h.connector.connect(h.callbacks)
        val token1 = h.connector.awaitToken()

        h.connector.connect(h.callbacks)
        val token2 = h.connector.awaitToken()

        assertFalse("被取代的 token 应已注销", h.registry.attach(token1, mockk(relaxed = true)))
        assertNotNull("新 token 仍在等待", h.registry.pending[token2])
        assertTrue(h.callbacks.connected.isEmpty())
        h.connector.disconnect(null)
    }
}
