package com.aliothmoon.maameow.presentation.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import com.aliothmoon.maameow.constant.TextInjectResult
import com.aliothmoon.maameow.manager.RemoteServiceManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

internal sealed interface ClipSnapshot {
    data class Value(val clip: ClipData) : ClipSnapshot
    data object Empty : ClipSnapshot
    data object Unreadable : ClipSnapshot
}

internal interface ClipboardPort {
    fun snapshot(): ClipSnapshot
    fun setText(text: String): Boolean
    fun currentText(): String?
    fun restore(snapshot: ClipSnapshot)
}

internal interface RemoteInputPort {
    fun hasOwnFocus(): Boolean
    fun injectText(text: String): Int
    fun sendKey(keyCode: Int, metaState: Int): Boolean
}

internal class AndroidClipboardPort(context: Context) : ClipboardPort {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    override fun snapshot(): ClipSnapshot {
        val cm = clipboard ?: return ClipSnapshot.Unreadable
        val hasClip = runCatching { cm.hasPrimaryClip() }.getOrElse { return ClipSnapshot.Unreadable }
        if (!hasClip) return ClipSnapshot.Empty
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return ClipSnapshot.Unreadable
        return ClipSnapshot.Value(clip)
    }

    override fun setText(text: String): Boolean {
        val cm = clipboard ?: return false
        return runCatching {
            cm.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
            true
        }.onFailure { Timber.w(it, "setPrimaryClip failed") }
            .getOrDefault(false)
    }

    override fun currentText(): String? {
        val cm = clipboard ?: return null
        return runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
    }

    override fun restore(snapshot: ClipSnapshot) {
        val cm = clipboard ?: return
        when (snapshot) {
            is ClipSnapshot.Value -> runCatching { cm.setPrimaryClip(snapshot.clip) }
                .onFailure { Timber.w(it, "restore clipboard failed") }

            ClipSnapshot.Empty -> runCatching { cm.clearPrimaryClip() }
                .onFailure { Timber.w(it, "clearPrimaryClip failed") }

            ClipSnapshot.Unreadable -> Unit
        }
    }

    private companion object {
        const val CLIP_LABEL = "maa_vd_input"
    }
}

internal object RemoteServiceInputPort : RemoteInputPort {
    override fun hasOwnFocus(): Boolean = runCatching {
        RemoteServiceManager.getInstanceOrNull()?.getVirtualDisplayStatus()?.hasOwnFocus == true
    }.getOrDefault(false)

    override fun injectText(text: String): Int {
        val service = RemoteServiceManager.getInstanceOrNull() ?: return TextInjectResult.REJECTED
        return runCatching { service.injectText(text) }
            .onFailure { Timber.w(it, "injectText failed") }
            .getOrDefault(TextInjectResult.FAILED)
    }

    override fun sendKey(keyCode: Int, metaState: Int): Boolean {
        val service = RemoteServiceManager.getInstanceOrNull() ?: return false
        return runCatching { service.sendKey(keyCode, metaState) }
            .onFailure { Timber.w(it, "sendKey($keyCode, $metaState) failed") }
            .getOrDefault(false)
    }
}

/** 须从主线程调用 */
internal class VirtualDisplayInputController(
    private val scope: CoroutineScope,
    private val clipboard: ClipboardPort,
    private val remote: RemoteInputPort,
    private val onCapabilityLost: () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pasteSettleMs: Long = PASTE_SETTLE_MS,
    private val clipboardRestoreDelayMs: Long = CLIPBOARD_RESTORE_DELAY_MS,
) {
    private val inputMutex = Mutex()
    private var restoreJob: Job? = null
    private var prePasteSnapshot: ClipSnapshot? = null
    private var lastPastedText: String? = null

    suspend fun queryOwnFocus(): Boolean = withContext(ioDispatcher) { remote.hasOwnFocus() }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        scope.launch {
            inputMutex.withLock {
                if (isAsciiInjectable(text)) {
                    when (withContext(ioDispatcher) { remote.injectText(text) }) {
                        TextInjectResult.OK -> return@withLock
                        TextInjectResult.FAILED -> return@withLock
                        TextInjectResult.REJECTED -> {
                            onCapabilityLost()
                            return@withLock
                        }

                        TextInjectResult.UNMAPPABLE -> Unit
                    }
                }
                pasteText(text)
            }
        }
    }

    fun sendKey(keyCode: Int) {
        scope.launch {
            inputMutex.withLock {
                val ok = withContext(ioDispatcher) { remote.sendKey(keyCode, 0) }
                if (!ok) {
                    val stillSupported = withContext(ioDispatcher) { remote.hasOwnFocus() }
                    if (!stillSupported) onCapabilityLost()
                }
            }
        }
    }

    fun clear() {
        restoreJob?.cancel()
        restoreJob = null
        val expected = lastPastedText ?: return
        restoreIfUnchanged(expected)
    }

    private suspend fun pasteText(text: String) {
        restoreJob?.cancel()
        restoreJob = null
        val capturedNow = prePasteSnapshot == null
        if (capturedNow) {
            prePasteSnapshot = clipboard.snapshot()
        }
        if (!clipboard.setText(text)) {
            if (capturedNow) prePasteSnapshot = null
            return
        }
        lastPastedText = text

        val pasted = withContext(ioDispatcher) { remote.sendKey(KeyEvent.KEYCODE_PASTE, 0) }
        if (!pasted) {
            Timber.w("pasteText: KEYCODE_PASTE rejected")
            restoreIfUnchanged(text)
            val stillSupported = withContext(ioDispatcher) { remote.hasOwnFocus() }
            if (!stillSupported) onCapabilityLost()
            return
        }

        // 持锁等目标进程读完剪贴板，防止连发第二条中文覆盖第一条
        delay(pasteSettleMs)
        restoreJob = scope.launch {
            delay(clipboardRestoreDelayMs)
            restoreIfUnchanged(text)
            restoreJob = null
        }
    }

    private fun restoreIfUnchanged(expectedText: String) {
        val snapshot = prePasteSnapshot
        prePasteSnapshot = null
        lastPastedText = null
        if (snapshot != null && clipboard.currentText() == expectedText) {
            clipboard.restore(snapshot)
        }
    }

    companion object {
        private const val PASTE_SETTLE_MS = 150L
        private const val CLIPBOARD_RESTORE_DELAY_MS = 300L

        fun isAsciiInjectable(text: String): Boolean =
            text.isNotEmpty() && text.all { it.code in 0x20..0x7E || it == '\n' || it == '\t' }
    }
}
