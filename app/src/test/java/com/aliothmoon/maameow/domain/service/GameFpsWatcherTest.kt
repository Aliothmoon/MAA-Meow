package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GameFpsWatcherTest {

    private val sessionLogger: MaaSessionLogger = mockk(relaxed = true)
    private val context: Context = mockk {
        every { getString(R.string.runlog_game_fps_low, *anyVararg()) } answers {
            "low ${secondArg<Array<Any>>()[0]}"
        }
        every { getString(R.string.runlog_game_fps_degraded, *anyVararg()) } answers {
            "degraded ${secondArg<Array<Any>>()[0]}"
        }
    }

    private val advisor = GameFpsAdvisor()

    private class ScriptedReader(private val values: List<Float?>) : GameFpsReader {
        private var reads = 0
        override suspend fun readGameFps(): Float? = values[minOf(reads++, values.lastIndex)]
    }

    /** 第一次读数挂住且不响应取消，模拟卡住的 Binder 调用 */
    private class StuckFirstReader(private val fps: Float) : GameFpsReader {
        var stuck: Continuation<Float?>? = null
        private var reads = 0
        override suspend fun readGameFps(): Float? =
            if (reads++ == 0) suspendCoroutine { stuck = it } else fps
    }

    private fun watcher(vararg values: Float?) = GameFpsWatcher(ScriptedReader(values.toList()), sessionLogger, context)

    @Test
    fun publishesRemoteFps() = runBlocking {
        val watcher = watcher(58f)

        watcher.pollOnce(advisor)
        assertEquals(58f, watcher.fps.value!!, 0f)

        // 停止后回到未监控，预览不再显示
        watcher.stop()
        assertNull(watcher.fps.value)
    }

    @Test
    fun unreadableRemoteReportsNullWithoutAdvising() = runBlocking {
        val watcher = watcher(null)

        repeat(GameFpsAdvisor.DEFAULT_WINDOW_SIZE + 3) { watcher.pollOnce(advisor) }

        assertNull(watcher.fps.value)
        verify(exactly = 0) { sessionLogger.append(any(), any()) }
    }

    @Test
    fun sustainedLowFpsLogsErrorOnceWithWindowMedian() = runBlocking {
        val watcher = watcher(20f)

        repeat(GameFpsAdvisor.DEFAULT_WINDOW_SIZE + 3) { watcher.pollOnce(advisor) }

        verify(exactly = 1) { sessionLogger.append("low 20", LogLevel.ERROR) }
        verify(exactly = 0) { sessionLogger.append(any(), LogLevel.WARNING) }
    }

    @Test
    fun sustainedDegradedFpsLogsWarningOnce() = runBlocking {
        val watcher = watcher(42f)

        repeat(GameFpsAdvisor.DEFAULT_WINDOW_SIZE + 3) { watcher.pollOnce(advisor) }

        verify(exactly = 1) { sessionLogger.append("degraded 42", LogLevel.WARNING) }
        verify(exactly = 0) { sessionLogger.append(any(), LogLevel.ERROR) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun restartKeepsInFlightSampleOutOfNewWindow() = runTest {
        val reader = StuckFirstReader(20f)
        val watcher = GameFpsWatcher(reader, sessionLogger, context, backgroundScope)

        watcher.start()
        advanceTimeBy(1_001)
        // 旧一轮卡在读数里时重启，随后读数返回
        watcher.start()
        reader.stuck!!.resume(20f)
        runCurrent()

        // 新一轮只差一个样本攒满窗口，旧一轮的样本混进来就会提前提示
        advanceTimeBy(1_000L * (GameFpsAdvisor.DEFAULT_WINDOW_SIZE - 1) + 1)
        verify(exactly = 0) { sessionLogger.append(any(), any()) }

        advanceTimeBy(1_000)
        verify(exactly = 1) { sessionLogger.append("low 20", LogLevel.ERROR) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun stoppedRoundDiscardsInFlightReading() = runTest {
        val reader = StuckFirstReader(58f)
        val watcher = GameFpsWatcher(reader, sessionLogger, context, backgroundScope)

        watcher.start()
        advanceTimeBy(1_001)
        watcher.stop()
        // 停止后才读回来的值不能再写上去，否则预览一直挂着旧帧率
        reader.stuck!!.resume(58f)
        runCurrent()

        assertNull(watcher.fps.value)
    }
}
