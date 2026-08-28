package com.aliothmoon.maameow.presentation.viewmodel

import android.os.SystemClock
import android.view.MotionEvent
import com.aliothmoon.maameow.ITouchEventCallback
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.presentation.state.PreviewTouchMarker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class TouchPreviewController(
    private val scope: CoroutineScope,
) {
    private val _markers = MutableStateFlow<List<PreviewTouchMarker>>(emptyList())
    val markers: StateFlow<List<PreviewTouchMarker>> = _markers.asStateFlow()

    private var markerId = AtomicLong(0L)
    private var job: Job? = null

    val callback = lazy {
        object : ITouchEventCallback.Stub() {
            override fun onCallback(x: Int, y: Int, type: Int, contact: Int) {
                val action = when (type) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_UP -> type
                    MotionEvent.ACTION_POINTER_DOWN -> MotionEvent.ACTION_DOWN
                    MotionEvent.ACTION_POINTER_UP -> MotionEvent.ACTION_UP
                    else -> return
                }
                val newMarker = PreviewTouchMarker(
                    id = markerId.incrementAndGet(),
                    x = x,
                    y = y,
                    action = action,
                    contact = contact,
                    createdAtMs = SystemClock.elapsedRealtime()
                )
                _markers.update { current ->
                    val max = PreviewTouchMarker.MAX_ACTIVE_MARKERS
                    buildList(max) {
                        val start = if (current.size >= max) current.size - max + 1 else 0
                        for (i in start until current.size) add(current[i])
                        add(newMarker)
                    }
                }
                ensureCleanupJob()
            }
        }
    }

    fun onTouchCallbackChange(enabled: Boolean) {
        val service = RemoteServiceManager.getInstanceOrNull()?: return
        if (enabled) {
            scope.launch(Dispatchers.IO) {
                runCatching { service.setTouchCallback(callback.value) }
            }
        } else if (callback.isInitialized()) {
            scope.launch(Dispatchers.IO) {
                runCatching { service.setTouchCallback(null) }
            }
            onClear()
        }
    }

    fun onClear() {
        job?.cancel()
        job = null
        _markers.value = emptyList()
    }

    private fun ensureCleanupJob() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (true) {
                delay(PreviewTouchMarker.CLEANUP_INTERVAL_MS)
                val cutoff = SystemClock.elapsedRealtime() - PreviewTouchMarker.TTL_MS
                _markers.update { markers ->
                    markers.filter { it.createdAtMs > cutoff }
                }
                if (_markers.value.isEmpty()) break
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (this@TouchPreviewController.job === job) this@TouchPreviewController.job = null
            }
        }
    }
}
