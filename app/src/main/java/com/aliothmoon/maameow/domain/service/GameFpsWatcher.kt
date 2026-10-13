package com.aliothmoon.maameow.domain.service

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.manager.RemoteServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

interface GameFpsReader {
    /** null = 远端未监控或无法读取 */
    suspend fun readGameFps(): Float?
}

class RemoteGameFpsReader : GameFpsReader {
    override suspend fun readGameFps(): Float? = withContext(Dispatchers.IO) {
        runCatching { RemoteServiceManager.getInstanceOrNull()?.gameFps }.getOrNull()
            ?.takeIf { it >= 0f }
    }
}

/** 后台模式下轮询游戏帧率：供预览显示，并在持续低帧率时写会话日志 */
class GameFpsWatcher(
    private val reader: GameFpsReader,
    private val sessionLogger: MaaSessionLogger,
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    companion object {
        private const val POLL_INTERVAL_MS = 1_000L
    }

    /** null = 未监控 */
    private val _fps = MutableStateFlow<Float?>(null)
    val fps: StateFlow<Float?> = _fps.asStateFlow()

    private var job: Job? = null

    fun start() {
        stop()
        // 每轮自带判定器：stop 不等旧一轮退出，它可能还在判定，共用一个会被两轮同时改
        val advisor = GameFpsAdvisor()
        job = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                pollOnce(advisor)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _fps.value = null
    }

    @VisibleForTesting
    internal suspend fun pollOnce(advisor: GameFpsAdvisor) {
        val value = reader.readGameFps()
        // 读数取消不掉，已停的一轮读回来直接丢，不写旧帧率也不再判定
        currentCoroutineContext().ensureActive()
        _fps.value = value
        advisor.onSample(value ?: 0f)?.let { advice ->
            val fps = advice.medianFps.roundToInt()
            when (advice.level) {
                GameFpsAdvisor.Level.LOW ->
                    sessionLogger.append(
                        context.getString(R.string.runlog_game_fps_low, fps),
                        LogLevel.ERROR
                    )

                GameFpsAdvisor.Level.DEGRADED ->
                    sessionLogger.append(
                        context.getString(R.string.runlog_game_fps_degraded, fps),
                        LogLevel.WARNING
                    )
            }
        }
    }
}
