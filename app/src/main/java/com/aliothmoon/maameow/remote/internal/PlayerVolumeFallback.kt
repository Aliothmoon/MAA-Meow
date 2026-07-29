package com.aliothmoon.maameow.remote.internal

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.HandlerThread
import com.aliothmoon.maameow.third.FakeContext
import com.aliothmoon.maameow.third.Ln
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("PrivateApi", "SoonBlockedPrivateApi")
object PlayerVolumeFallback {
    private const val TAG = "PlayerVolumeFallback"

    private val engagedUids = ConcurrentHashMap.newKeySet<Int>()

    private val audioManager: AudioManager? by lazy {
        runCatching {
            FakeContext.get().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }.onFailure { Ln.w("$TAG: obtain AudioManager failed: ${it.message}") }.getOrNull()
    }

    private val callbackHandler by lazy {
        Handler(HandlerThread("player-volume-fallback").apply { start() }.looper)
    }
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    private val getIPlayerMethod by lazy {
        runCatching {
            AudioPlaybackConfiguration::class.java.getDeclaredMethod("getIPlayer")
                .apply { isAccessible = true }
        }.getOrNull()
    }

    private val getClientUidMethod by lazy {
        runCatching {
            AudioPlaybackConfiguration::class.java.getDeclaredMethod("getClientUid")
                .apply { isAccessible = true }
        }.getOrNull()
    }

    private val clientUidField by lazy {
        runCatching {
            AudioPlaybackConfiguration::class.java.getDeclaredField("mClientUid")
                .apply { isAccessible = true }
        }.getOrNull()
    }

    private val available: Boolean
        get() = audioManager != null && getIPlayerMethod != null &&
                (getClientUidMethod != null || clientUidField != null)

    /** 压制该 uid 的活跃播放器并持续压制其新建播放器；反射机制不可用时返回 false */
    @Synchronized
    fun engage(uid: Int): Boolean {
        if (!available) {
            Ln.w("$TAG: reflection unavailable, cannot engage for uid=$uid")
            return false
        }
        engagedUids.add(uid)
        applyVolumeToUid(uid, 0f)
        ensureCallback()
        Ln.i("$TAG: engaged for uid=$uid")
        return true
    }

    @Synchronized
    fun disengage(uid: Int) {
        if (!engagedUids.remove(uid)) return
        applyVolumeToUid(uid, 1f)
        maybeStopCallback()
        Ln.i("$TAG: disengaged for uid=$uid")
    }

    private fun applyVolumeToUid(uid: Int, volume: Float) {
        val am = audioManager ?: return
        val configs = runCatching { am.activePlaybackConfigurations }.getOrElse {
            Ln.w("$TAG: getActivePlaybackConfigurations failed: ${it.message}")
            return
        }
        var applied = 0
        for (cfg in configs) {
            if (clientUidOf(cfg) != uid) continue
            iPlayerOf(cfg)?.let { if (setPlayerVolume(it, volume)) applied++ }
        }
        if (volume == 0f && applied == 0) {
            // 多为：本进程拿不到真实 IPlayer（无权限，配置被脱敏）或目标此刻未播放；前者会导致静音不生效
            Ln.w("$TAG: applyVolume(0) matched no player for uid=$uid (no privilege or not playing yet)")
        }
    }

    private fun ensureCallback() {
        if (playbackCallback != null) return
        val am = audioManager ?: return
        val cb = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                // 游戏新建播放器时把目标 uid 的音量重新压到 0
                if (engagedUids.isEmpty()) return
                for (cfg in configs) {
                    if (clientUidOf(cfg) in engagedUids) {
                        iPlayerOf(cfg)?.let { setPlayerVolume(it, 0f) }
                    }
                }
            }
        }
        runCatching { am.registerAudioPlaybackCallback(cb, callbackHandler) }
            .onSuccess { playbackCallback = cb }
            .onFailure { Ln.w("$TAG: registerAudioPlaybackCallback failed: ${it.message}") }
    }

    private fun maybeStopCallback() {
        if (engagedUids.isNotEmpty()) return
        val cb = playbackCallback ?: return
        runCatching { audioManager?.unregisterAudioPlaybackCallback(cb) }
        playbackCallback = null
    }

    private fun iPlayerOf(cfg: AudioPlaybackConfiguration): Any? =
        runCatching { getIPlayerMethod?.invoke(cfg) }.getOrNull()

    private fun clientUidOf(cfg: AudioPlaybackConfiguration): Int = runCatching {
        (getClientUidMethod?.invoke(cfg) as? Int) ?: (clientUidField?.get(cfg) as? Int) ?: -1
    }.getOrDefault(-1)

    private fun setPlayerVolume(player: Any, volume: Float): Boolean = runCatching {
        player.javaClass
            .getMethod("setVolume", Float::class.javaPrimitiveType)
            .invoke(player, volume)
        true
    }.getOrElse { false }
}
