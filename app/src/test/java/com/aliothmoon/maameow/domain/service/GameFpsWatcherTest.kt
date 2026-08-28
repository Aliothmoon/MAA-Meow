package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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

    private class ScriptedReader(private val values: List<Float?>) : GameFpsReader {
        private var reads = 0
        override suspend fun readGameFps(): Float? = values[minOf(reads++, values.lastIndex)]
    }

    private fun watcher(vararg values: Float?) = GameFpsWatcher(ScriptedReader(values.toList()), sessionLogger, context)

    @Test
    fun publishesRemoteFps() = runBlocking {
        val watcher = watcher(58f)

        watcher.pollOnce()
        assertEquals(58f, watcher.fps.value!!, 0f)

        // 停止后回到未监控，预览不再显示
        watcher.stop()
        assertNull(watcher.fps.value)
    }

    @Test
    fun unreadableRemoteReportsNullWithoutAdvising() = runBlocking {
        val watcher = watcher(null)

        repeat(GameFpsAdvisor.DEFAULT_WINDOW_SIZE + 3) { watcher.pollOnce() }

        assertNull(watcher.fps.value)
        verify(exactly = 0) { sessionLogger.append(any(), any()) }
    }

    @Test
    fun sustainedLowFpsLogsErrorOnceWithWindowMedian() = runBlocking {
        val watcher = watcher(20f)

        repeat(GameFpsAdvisor.DEFAULT_WINDOW_SIZE + 3) { watcher.pollOnce() }

        verify(exactly = 1) { sessionLogger.append("low 20", LogLevel.ERROR) }
        verify(exactly = 0) { sessionLogger.append(any(), LogLevel.WARNING) }
    }

    @Test
    fun sustainedDegradedFpsLogsWarningOnce() = runBlocking {
        val watcher = watcher(42f)

        repeat(GameFpsAdvisor.DEFAULT_WINDOW_SIZE + 3) { watcher.pollOnce() }

        verify(exactly = 1) { sessionLogger.append("degraded 42", LogLevel.WARNING) }
        verify(exactly = 0) { sessionLogger.append(any(), LogLevel.ERROR) }
    }
}
