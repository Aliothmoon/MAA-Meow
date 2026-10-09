package com.aliothmoon.maameow.remote.internal.display

import android.os.Handler
import android.os.HandlerThread
import android.os.Process

internal object FrameCaptureHelper {

    fun createCaptureHandler(name: String): Handler {
        val thread = object : HandlerThread(name, Process.THREAD_PRIORITY_URGENT_DISPLAY) {
            override fun onLooperPrepared() {
                // Double check and ensure the priority is set correctly
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            }
        }
        thread.start()
        return Handler(thread.looper)
    }
}
