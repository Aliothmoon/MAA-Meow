package com.aliothmoon.maameow.manager

import android.content.pm.PackageManager
import android.os.SystemClock
import com.aliothmoon.maameow.domain.models.RemoteBackend
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

object ShizukuManager : RemoteAccessPermissionBackend {

    override val backend = RemoteBackend.SHIZUKU

    private val listeners = CopyOnWriteArraySet<RemoteAccessStateListener>()
    private val observingState = AtomicBoolean(false)
    private val suiInitialized = AtomicBoolean(false)

    @Volatile
    var isSui: Boolean = false
        private set

    /** 本进程最近一次收到 / 失去 binder 的 elapsedRealtime */
    @Volatile
    var binderReceivedAt: Long? = null
        private set

    @Volatile
    var binderDeadAt: Long? = null
        private set

    /** 最近一次连上时服务端的 uid，死后不清 */
    @Volatile
    var lastServerUid: Int? = null
        private set

    fun initSui(packageName: String) {
        if (!suiInitialized.compareAndSet(false, true)) return
        isSui = try {
            Sui.init(packageName)
        } catch (e: Exception) {
            Timber.e(e, "Sui.init failed")
            false
        }
    }

    fun isShizukuAvailable(): Boolean = isAvailable()

    /** Shizuku 服务的 uid，即经它起的进程的身份；不可用返回 null */
    fun serverUid(): Int? {
        if (!isAvailable()) return null
        return try {
            Shizuku.getUid()
        } catch (e: Exception) {
            Timber.w(e, "Shizuku.getUid failed")
            null
        }
    }

    override fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Timber.e(e, "Error pinging Shizuku binder")
            false
        }
    }

    override fun isGranted(): Boolean {
        if (!isAvailable()) {
            return false
        }
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun requestPermission(): Boolean {
        ensureStateObservation()

        if (!isAvailable()) return false

        if (Shizuku.isPreV11()) {
            notifyStateChanged()
            return true
        }

        if (isGranted()) {
            notifyStateChanged()
            return true
        }

        val granted = try {
            withTimeoutOrNull(15_000) {
                callbackFlow {
                    val requestCode = (1000..9999).random()
                    val listener = Shizuku.OnRequestPermissionResultListener { code, result ->
                        if (code == requestCode) {
                            val granted = result == PackageManager.PERMISSION_GRANTED
                            Timber.d("Permission result: code=%d, granted=%s", code, granted)
                            trySend(granted)
                            close()
                        }
                    }
                    Shizuku.addRequestPermissionResultListener(listener)
                    Timber.d("Requesting Shizuku permission with code=%d", requestCode)
                    Shizuku.requestPermission(requestCode)
                    awaitClose {
                        Shizuku.removeRequestPermissionResultListener(listener)
                    }
                }.catch { e ->
                    Timber.e(e, "Error in permission request flow")
                    emit(false)
                }.first()
            }?.also { granted ->
                Timber.d("Shizuku permission %s", if (granted) "granted" else "denied")
            } ?: false
        } catch (e: Exception) {
            Timber.e(e, "Error requesting permission")
            false
        }

        notifyStateChanged()
        return granted
    }

    suspend fun <T> requireShizukuPermissionGranted(action: suspend () -> T): T {
        val permission = requestPermission()
        if (!permission) {
            throw IllegalStateException("request shizuku permission failed")
        }
        return action()
    }

    override fun addStateListener(listener: RemoteAccessStateListener) {
        ensureStateObservation()
        listeners += listener
    }

    override fun removeStateListener(listener: RemoteAccessStateListener) {
        listeners -= listener
    }

    private fun ensureStateObservation() {
        if (!observingState.compareAndSet(false, true)) {
            return
        }
        Shizuku.addBinderReceivedListenerSticky {
            Timber.d("Shizuku binder received")
            binderReceivedAt = SystemClock.elapsedRealtime()
            lastServerUid = serverUid()
            notifyStateChanged()
        }
        Shizuku.addBinderDeadListener {
            Timber.i("Shizuku binder dead")
            binderDeadAt = SystemClock.elapsedRealtime()
            notifyStateChanged()
        }
    }

    private fun notifyStateChanged() {
        listeners.forEach { listener ->
            runCatching { listener.onStateChanged(backend) }
                .onFailure { Timber.w(it, "notifyStateChanged failed") }
        }
    }
}
