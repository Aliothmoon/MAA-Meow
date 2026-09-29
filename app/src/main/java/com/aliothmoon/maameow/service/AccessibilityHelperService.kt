package com.aliothmoon.maameow.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

class AccessibilityHelperService : AccessibilityService() {

    companion object {
        /** 同时按下两个键的时间容差 (毫秒) */
        private const val SIMULTANEOUS_PRESS_THRESHOLD = 300L
        const val SERVICE_ID = "com.aliothmoon.maameow/.service.AccessibilityHelperService"

        private val onVolumeUpDownPressed = AtomicReference<(() -> Unit)?>()

        @Volatile
        private var instance: AccessibilityHelperService? = null

        private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

        /**
         * 装卸音量键组合监听；null 表示不需要拦截，按键过滤随之关掉
         *
         * 切到主线程改 serviceInfo：组合键状态只在主线程的 onKeyEvent 里读写
         */
        fun setVolumeComboListener(listener: (() -> Unit)?) {
            onVolumeUpDownPressed.set(listener)
            mainHandler.post { instance?.applyKeyFiltering() }
        }

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }

    private var volumeUpPressTime = 0L

    private var volumeDownPressTime = 0L

    private var triggered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // 连上之前就可能已经装了监听，按当前状态补一次
        applyKeyFiltering()
        _isConnected.value = true
        Timber.d("AccessibilityHelperService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (onVolumeUpDownPressed.get() == null) {
            return super.onKeyEvent(event)
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    volumeUpPressTime = System.currentTimeMillis()
                    if (checkSimultaneousPress()) {
                        return true
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    volumeUpPressTime = 0L
                    triggered = false
                }
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    volumeDownPressTime = System.currentTimeMillis()
                    if (checkSimultaneousPress()) {
                        return true
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    volumeDownPressTime = 0L
                    triggered = false
                }
            }
        }
        return super.onKeyEvent(event)
    }

    /**
     * 只在有人监听组合键时才让系统把按键先交给本服务；没人监听时按键根本不经过这里
     *
     * 常开的话，服务一启用所有物理按键都要先绕本服务一圈，哪怕最后原样放行
     */
    private fun applyKeyFiltering() {
        val wanted = onVolumeUpDownPressed.get() != null
        if (!wanted) {
            // 过滤关掉时半截的组合状态不能留到下次打开
            volumeUpPressTime = 0L
            volumeDownPressTime = 0L
            triggered = false
        }
        val info = serviceInfo ?: return
        val filtering = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
        if (filtering == wanted) return
        info.flags = if (wanted) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        serviceInfo = info
        Timber.d("Key event filtering %s", if (wanted) "on" else "off")
    }

    private fun checkSimultaneousPress(): Boolean {
        if (triggered) return true

        if (volumeUpPressTime > 0 && volumeDownPressTime > 0) {
            val timeDiff = abs(volumeUpPressTime - volumeDownPressTime)
            if (timeDiff < SIMULTANEOUS_PRESS_THRESHOLD) {
                Timber.d("Volume up + down pressed simultaneously")
                triggered = true
                onVolumeUpDownPressed.get()?.invoke()
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isConnected.value = false
        Timber.d("AccessibilityHelperService destroyed")
    }
}
