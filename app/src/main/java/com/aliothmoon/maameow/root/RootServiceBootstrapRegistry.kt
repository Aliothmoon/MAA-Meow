package com.aliothmoon.maameow.root

import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

object RootServiceBootstrapRegistry {

    const val AUTHORITY_SUFFIX = ".root.bootstrap"
    const val METHOD_ATTACH_REMOTE_SERVICE = "attachRemoteService"
    const val KEY_TOKEN = "token"
    const val KEY_SERVICE_BINDER = "service_binder"
    const val KEY_APP_BINDER = "app_binder"
    const val KEY_APP_PID = "app_pid"

    private val pendingBinders = ConcurrentHashMap<String, CompletableDeferred<IBinder>>()
    private val appLifecycleBinder = Binder()

    fun register(token: String): CompletableDeferred<IBinder> {
        return CompletableDeferred<IBinder>().also { pendingBinders[token] = it }
    }

    fun unregister(token: String) {
        pendingBinders.remove(token)?.cancel()
    }

    fun attach(token: String, binder: IBinder): IBinder? {
        val deferred = pendingBinders.remove(token) ?: return null
        deferred.complete(binder)
        return appLifecycleBinder
    }
}
