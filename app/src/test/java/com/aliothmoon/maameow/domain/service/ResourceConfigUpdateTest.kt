package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.domain.state.MaaExecutionState
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Exercise the real startup lock and state gate, rather than mocking the save permission. */
class ResourceConfigUpdateTest {
    private val loader = mockk<MaaResourceLoader>(relaxed = true)
    private lateinit var service: MaaCompositionService
    @Before fun before() {
        // Skip Android Binder construction on the JVM, but execute the real save method.
        service = mockk()
        setField("resourceLoader", loader)
        setField("startMutex", Mutex())
        setField("_state", MutableStateFlow(MaaExecutionState.IDLE))
        coEvery { service.updateResourceConfig(any()) } coAnswers { callOriginal() }
    }
    private fun setField(name: String, value: Any) {
        MaaCompositionService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.set(service, value)
    }
    private fun field(name: String): Any = checkNotNull(MaaCompositionService::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(service))
    @Suppress("UNCHECKED_CAST")
    private fun state(value: MaaExecutionState) {
        (field("_state") as MutableStateFlow<MaaExecutionState>).value = value
    }

    @Test fun activeTasksNeverRunTheWriteOrInvalidateResources() = runBlocking {
        for (active in listOf(MaaExecutionState.STARTING, MaaExecutionState.RUNNING, MaaExecutionState.STOPPING)) {
            state(active)
            assertFalse(service.updateResourceConfig { fail("Must not write while active") })
        }
        verify(exactly = 0) { loader.invalidate() }
    }

    @Test fun stoppedStatesWriteBeforeInvalidatingResources() = runBlocking {
        var writes = 0
        every { loader.invalidate() } answers { assertTrue(writes > 0) }
        for (stopped in listOf(MaaExecutionState.IDLE, MaaExecutionState.ERROR)) {
            state(stopped)
            assertTrue(service.updateResourceConfig { writes++ })
        }
        assertEquals(2, writes)
        verify(exactly = 2) { loader.invalidate() }
    }

    @Test fun waitsForTaskStartupLockAndRechecksTheStateAfterAcquiringIt() = runBlocking {
        val startupLock = field("startMutex") as Mutex
        startupLock.lock()
        var written = false
        val save = async(start = CoroutineStart.UNDISPATCHED) {
            service.updateResourceConfig { written = true }
        }
        assertFalse(save.isCompleted)
        assertFalse(written)
        state(MaaExecutionState.RUNNING)
        startupLock.unlock()
        assertFalse(save.await())
        assertFalse(written)
        verify(exactly = 0) { loader.invalidate() }
    }
}
