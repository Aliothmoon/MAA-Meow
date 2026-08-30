package com.aliothmoon.maameow.data.api

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, _, _ -> runCatching { response.close() } }
        }
    })
}

/**
 * 拿到响应后还要阻塞读 body 的场景用这个
 *
 * [await] 只覆盖响应头阶段，body 的阻塞读卡住要等到 readTimeout 才醒，
 * 这里靠哨兵协程在取消时关掉 socket
 */
suspend fun <T> Call.useCancellable(block: suspend (Response) -> T): T = coroutineScope {
    val finished = AtomicBoolean(false)
    // UNDISPATCHED：确保哨兵返回前已挂到 awaitCancellation，不漏取消
    val watchdog = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!finished.get()) this@useCancellable.cancel()
        }
    }
    try {
        block(await())
    } finally {
        finished.set(true)
        watchdog.cancel()
    }
}
