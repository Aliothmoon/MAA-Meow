package com.aliothmoon.maameow.manager

import android.content.Context
import android.os.IBinder
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.root.BootstrapRegistry
import com.aliothmoon.maameow.root.RootUserService.RemoteProcessServiceEntry
import io.mockk.CapturingSlot
import io.mockk.every
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * 进程连接器基座行为：IPC 状态机高变更区，失败 / 竞态路径必须有真行为测试兜底
 */
class ProcessServiceConnectorBehaviorTest {

    private class FakeServiceEntry : RemoteProcessServiceEntry

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
        enableSidecar: Boolean = false,
    ) : ProcessServiceConnectorBackend(spawner, registry) {
        override val backend = RemoteBackend.ROOT
        override val eventPrefix = "FAKE"
        override val processNameSuffix = "fake"
        override val serviceClass: Class<out RemoteProcessServiceEntry> =
            FakeServiceEntry::class.java
        override val logFileName = "fake.log"
        override val spawnTimeoutMs = timeoutMs
        override val sidecarServiceClass: Class<out RemoteProcessServiceEntry>? =
            FakeServiceEntry::class.java.takeIf { enableSidecar }

        private val tokens = LinkedBlockingQueue<String>()
        val attachedSidecars = LinkedBlockingQueue<Pair<IBinder, IBinder>>()
        val destroyedSidecars = AtomicInteger()
        val destroyedSidecarBinders = LinkedBlockingQueue<IBinder>()
        val destroyedRemotes = LinkedBlockingQueue<IBinder>()
        var onAttachSidecar: () -> Unit = {}
        var onDestroySidecar: () -> Unit = {}

        override fun buildStartCommand(token: String, logFile: File): String {
            tokens.add(token)
            return "fake-cmd"
        }

        override fun buildSidecarStartCommand(
            token: String,
            logFile: File,
            targetClass: Class<*>,
        ): String {
            tokens.add(token)
            return "fake-sidecar-cmd"
        }

        override fun attachSidecar(primaryBinder: IBinder, sidecarBinder: IBinder) {
            attachedSidecars.add(primaryBinder to sidecarBinder)
            onAttachSidecar()
        }

        override fun destroySidecar(sidecarBinder: IBinder) {
            destroyedSidecars.incrementAndGet()
            destroyedSidecarBinders.add(sidecarBinder)
            onDestroySidecar()
        }

        override fun debugLogFile(): File = logFile

        override fun destroyRemote(binder: IBinder) {
            destroyedRemotes.add(binder)
        }

        fun awaitToken(): String = tokens.poll(5, TimeUnit.SECONDS) ?: error("token never assigned")
    }

    private class RecordingCallbacks : RemoteServiceConnectorBackend.Callbacks {
        val connected = CopyOnWriteArrayList<IBinder>()
        val errors = CopyOnWriteArrayList<Throwable>()
        val disconnected = AtomicInteger()
        private val connectedLatch = CountDownLatch(1)
        private val errorLatch = CountDownLatch(1)
        var onConnectedHook: () -> Unit = {}

        override fun onConnected(backend: RemoteBackend, binder: IBinder) {
            connected += binder
            onConnectedHook()
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

    private inner class Harness(timeoutMs: Long = 300L, enableSidecar: Boolean = false) {
        val spawner = FakeSpawner()
        val registry = FakeRegistry()
        val callbacks = RecordingCallbacks()
        val connector = FakeConnector(spawner, registry, logFile, timeoutMs, enableSidecar).apply {
            initialize(mockk<Context>(relaxed = true), logFile.parentFile!!)
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
        assertTrue("成功回调后连接协程应及时结束", waitUntil { !h.connector.isConnecting })
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

    @Test
    fun binderSupersededAfterInitialTokenCheck_isDestroyedBeforePublish() {
        val h = Harness(timeoutMs = 5_000L)
        val linkEntered = CountDownLatch(1)
        val allowLinkToReturn = CountDownLatch(1)
        val oldBinder = mockk<IBinder>(relaxed = true)
        every { oldBinder.linkToDeath(any(), any()) } answers {
            linkEntered.countDown()
            allowLinkToReturn.await(5, TimeUnit.SECONDS)
        }

        h.connector.connect(h.callbacks)
        val oldToken = h.connector.awaitToken()
        assertTrue(h.registry.attach(oldToken, oldBinder))
        assertTrue("旧 binder 应进入 linkToDeath", linkEntered.await(5, TimeUnit.SECONDS))

        h.connector.connect(h.callbacks)
        val replacementToken = h.registry.pending.keys.single()
        allowLinkToReturn.countDown()

        assertEquals(oldBinder, h.connector.destroyedRemotes.poll(5, TimeUnit.SECONDS))
        assertTrue("被取代的 binder 不得发布", h.callbacks.connected.isEmpty())
        assertNotNull(h.registry.pending[replacementToken])
        h.connector.disconnect(null)
    }

    @Test
    fun binderSupersededDuringConnectedCallback_isDestroyedAfterCallback() {
        val h = Harness(timeoutMs = 5_000L)
        val callbackEntered = CountDownLatch(1)
        val allowCallbackToReturn = CountDownLatch(1)
        val oldBinder = mockk<IBinder>(relaxed = true)
        h.callbacks.onConnectedHook = {
            callbackEntered.countDown()
            allowCallbackToReturn.await(5, TimeUnit.SECONDS)
        }

        h.connector.connect(h.callbacks)
        assertTrue(h.registry.attach(h.connector.awaitToken(), oldBinder))
        assertTrue(callbackEntered.await(5, TimeUnit.SECONDS))

        h.connector.connect(h.callbacks)
        allowCallbackToReturn.countDown()

        assertEquals(oldBinder, h.connector.destroyedRemotes.poll(5, TimeUnit.SECONDS))
        assertNotNull(h.registry.pending[h.connector.awaitToken()])
        h.connector.disconnect(null)
    }

    @Test
    fun sidecar_isAttachedBeforePrimaryIsPublished() {
        val h = Harness(timeoutMs = 5_000L, enableSidecar = true)
        val primary = mockk<IBinder>(relaxed = true)
        val sidecar = mockk<IBinder>(relaxed = true)

        h.connector.connect(h.callbacks)
        assertTrue(h.registry.attach(h.connector.awaitToken(), primary))
        val sidecarToken = h.connector.awaitToken()

        assertTrue("主服务必须等 sidecar attach 后才能发布", h.callbacks.connected.isEmpty())
        assertTrue(h.registry.attach(sidecarToken, sidecar))
        assertTrue(h.callbacks.awaitConnected())

        assertEquals(primary to sidecar, h.connector.attachedSidecars.poll(1, TimeUnit.SECONDS))
        assertEquals(2, h.spawner.spawnCalls.get())
    }

    @Test
    fun sidecarTimeout_keepsPrimaryAndFallsBack() {
        val h = Harness(timeoutMs = 300L, enableSidecar = true)
        val primary = mockk<IBinder>(relaxed = true)

        h.connector.connect(h.callbacks)
        assertTrue(h.registry.attach(h.connector.awaitToken(), primary))
        h.connector.awaitToken()

        assertTrue("sidecar 失败不能拖死主服务", h.callbacks.awaitConnected())
        assertEquals(listOf(primary), h.callbacks.connected)
        assertTrue(h.callbacks.errors.isEmpty())
        assertTrue("失败的 sidecar 必须清残留", h.spawner.awaitKill())
        assertEquals(
            "连接预算只覆盖 primary + sidecar spawn，不包含后续虚拟显示生命周期预算",
            2 * (300L + 3_000L),
            h.connector.worstCaseConnectMs,
        )
    }

    @Test
    fun sidecarFromSupersededLaunch_isDestroyedBeforePublish() {
        val h = Harness(timeoutMs = 5_000L, enableSidecar = true)
        val attachEntered = CountDownLatch(1)
        val allowAttachToReturn = CountDownLatch(1)
        h.connector.onAttachSidecar = {
            attachEntered.countDown()
            allowAttachToReturn.await(5, TimeUnit.SECONDS)
        }

        h.connector.connect(h.callbacks)
        assertTrue(h.registry.attach(h.connector.awaitToken(), mockk(relaxed = true)))
        assertTrue(h.registry.attach(h.connector.awaitToken(), mockk(relaxed = true)))
        assertTrue("旧 sidecar 应进入同步 attach", attachEntered.await(5, TimeUnit.SECONDS))

        // connect() 会立即换 token；新任务仍排在单线程队列里，旧 attach 返回后必须先
        // 销毁自己的 sidecar，不能通过 getAndSet 发布并顶掉当前连接。
        h.connector.connect(h.callbacks)
        allowAttachToReturn.countDown()

        val replacementPrimaryToken = h.connector.awaitToken()
        assertTrue("被取代的 sidecar 必须被销毁", waitUntil { h.connector.destroyedSidecars.get() == 1 })
        assertNotNull("新 launch 仍应等待自己的 primary binder", h.registry.pending[replacementPrimaryToken])
        h.connector.disconnect(null)
    }

    @Test
    fun primaryDeath_doesNotClearReplacementSidecar() {
        val h = Harness(timeoutMs = 5_000L, enableSidecar = true)
        val oldPrimary = mockk<IBinder>(relaxed = true)
        val oldSidecar = mockk<IBinder>(relaxed = true)
        val oldDeath = slot<IBinder.DeathRecipient>()
        justRun { oldPrimary.linkToDeath(capture(oldDeath), any()) }

        h.connector.connect(h.callbacks)
        assertTrue(h.registry.attach(h.connector.awaitToken(), oldPrimary))
        assertTrue(h.registry.attach(h.connector.awaitToken(), oldSidecar))
        assertTrue(h.callbacks.awaitConnected())

        val destroyEntered = CountDownLatch(1)
        val allowDestroyToReturn = CountDownLatch(1)
        val deathFinished = CountDownLatch(1)
        h.connector.onDestroySidecar = {
            destroyEntered.countDown()
            allowDestroyToReturn.await(5, TimeUnit.SECONDS)
        }
        Thread {
            oldDeath.captured.binderDied()
            deathFinished.countDown()
        }.start()
        assertTrue("旧 death 必须先摘取并开始销毁自己的 sidecar", destroyEntered.await(5, TimeUnit.SECONDS))

        val newPrimary = mockk<IBinder>(relaxed = true)
        val newSidecar = mockk<IBinder>(relaxed = true)
        h.connector.connect(h.callbacks)
        assertTrue(h.registry.attach(h.connector.awaitToken(), newPrimary))
        assertTrue(h.registry.attach(h.connector.awaitToken(), newSidecar))
        assertTrue("替代 launch 应能发布新 sidecar", waitUntil { h.callbacks.connected.size == 2 })

        allowDestroyToReturn.countDown()
        assertTrue(deathFinished.await(5, TimeUnit.SECONDS))
        assertEquals(oldSidecar, h.connector.destroyedSidecarBinders.poll(1, TimeUnit.SECONDS))

        h.connector.disconnect(null)
        assertEquals(
            "旧 death 回调不得清除替代 launch 持有的 sidecar",
            newSidecar,
            h.connector.destroyedSidecarBinders.poll(1, TimeUnit.SECONDS),
        )
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }
}
