package com.aliothmoon.maameow.manager

import android.os.IBinder
import com.aliothmoon.maameow.domain.models.RemoteBackend

interface RemoteServiceConnectorBackend {
    val backend: RemoteBackend

    /** 本连接器最坏连接时长，上层兜底超时据此推算 */
    val worstCaseConnectMs: Long

    fun connect(callbacks: Callbacks)

    fun disconnect(currentBinder: IBinder?)

    interface Callbacks {
        fun onConnected(backend: RemoteBackend, binder: IBinder)

        fun onDisconnected(backend: RemoteBackend)

        fun onError(backend: RemoteBackend, throwable: Throwable)
    }
}
