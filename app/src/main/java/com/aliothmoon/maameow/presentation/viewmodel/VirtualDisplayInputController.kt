package com.aliothmoon.maameow.presentation.viewmodel

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
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

internal enum class InputFailure {
    SEND_FAILED,
    PASTE_UNSUPPORTED,
}

internal interface ClipboardPort {
    fun snapshot(): ClipSnapshot
    fun setText(text: String): Boolean
    fun currentText(): String?
    fun restore(snapshot: ClipSnapshot)
}

internal interface RemoteInputPort {
    suspend fun hasOwnFocus(): Boolean
    suspend fun injectText(text: String): Int
    suspend fun sendKey(keyCode: Int, metaState: Int): Boolean
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
            val clip = ClipData.newPlainText(CLIP_LABEL, text).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
            }
            cm.setPrimaryClip(clip)
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
    override suspend fun hasOwnFocus(): Boolean = runCatching {
        RemoteServiceManager.getInstanceOrNull()?.getVirtualDisplayStatus()?.hasOwnFocus == true
    }.getOrDefault(false)

    override suspend fun injectText(text: String): Int {
        val service = RemoteServiceManager.getInstanceOrNull() ?: return TextInjectResult.REJECTED
        return runCatching { service.injectText(text) }
            .onFailure { Timber.w(it, "injectText failed") }
            .getOrDefault(TextInjectResult.FAILED)
    }

    override suspend fun sendKey(keyCode: Int, metaState: Int): Boolean {
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
    private val onFailure: (InputFailure) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pasteSettleMs: Long = PASTE_SETTLE_MS,
    private val clipboardRestoreDelayMs: Long = CLIPBOARD_RESTORE_DELAY_MS,
) {
    private val inputMutex = Mutex()
    private var restoreJob: Job? = null
    private var prePasteSnapshot: ClipSnapshot? = null
    private var lastPastedText: String? = null
    // settle 完成前不还原，防游戏粘进原剪贴板
    private var pasteSettled = false

    suspend fun queryOwnFocus(): Boolean =
        withContext(ioDispatcher) { remote.hasOwnFocus() }

    fun sendText(text: String, onResult: (Boolean) -> Unit = {}) {
        if (text.isEmpty()) return
        scope.launch {
            val sent = inputMutex.withLock {
                when (withContext(ioDispatcher) { remote.injectText(text) }) {
                    TextInjectResult.OK -> true

                    TextInjectResult.UNMAPPABLE -> pasteViaHostClipboard(text)

                    TextInjectResult.PASTE_UNSUPPORTED -> {
                        Timber.w("sendText paste unsupported on isolated clipboard")
                        onFailure(InputFailure.PASTE_UNSUPPORTED)
                        false
                    }

                    TextInjectResult.REJECTED -> {
                        Timber.w("sendText rejected by remote")
                        onCapabilityLost()
                        onFailure(InputFailure.SEND_FAILED)
                        false
                    }

                    else -> {
                        Timber.w("sendText failed")
                        onFailure(InputFailure.SEND_FAILED)
                        false
                    }
                }
            }
            onResult(sent)
        }
    }

    fun sendKey(keyCode: Int) {
        scope.launch {
            inputMutex.withLock {
                val ok = withContext(ioDispatcher) { remote.sendKey(keyCode, 0) }
                if (ok) return@withLock
                onFailure(InputFailure.SEND_FAILED)
                if (!queryOwnFocus()) onCapabilityLost()
            }
        }
    }

    fun clear() {
        restoreJob?.cancel()
        restoreJob = null
        val expected = lastPastedText
        if (pasteSettled && expected != null) {
            restoreIfUnchanged(expected)
        } else {
            resetPasteState()
        }
    }

    private suspend fun pasteViaHostClipboard(text: String): Boolean {
        val capturedNow = prePasteSnapshot == null
        if (capturedNow) {
            prePasteSnapshot = clipboard.snapshot()
        }
        if (!clipboard.setText(text)) {
            if (capturedNow) {
                prePasteSnapshot = null
            } else if (restoreJob?.isActive != true) {
                lastPastedText?.let(::scheduleRestore)
            }
            onFailure(InputFailure.SEND_FAILED)
            return false
        }
        restoreJob?.cancel()
        restoreJob = null
        lastPastedText = text
        pasteSettled = false

        val pasted = withContext(ioDispatcher) { remote.sendKey(KeyEvent.KEYCODE_PASTE, 0) }
        if (!pasted) {
            Timber.w("pasteViaHostClipboard: KEYCODE_PASTE failed")
            onFailure(InputFailure.SEND_FAILED)
            if (!queryOwnFocus()) {
                restoreIfUnchanged(text)
                onCapabilityLost()
                return false
            }
        }

        // 持锁等目标消费粘贴（WAIT_FOR_FINISH 超时也可能已送达）
        delay(pasteSettleMs)
        pasteSettled = true
        scheduleRestore(text)
        return pasted
    }

    private fun scheduleRestore(expectedText: String) {
        restoreJob?.cancel()
        restoreJob = scope.launch {
            delay(clipboardRestoreDelayMs)
            restoreIfUnchanged(expectedText)
            restoreJob = null
        }
    }

    private fun restoreIfUnchanged(expectedText: String) {
        val snapshot = prePasteSnapshot ?: return resetPasteState()
        resetPasteState()
        // 后台读剪贴板返回 null 时仍还原
        val current = clipboard.currentText()
        if (current == null || current == expectedText) {
            clipboard.restore(snapshot)
        }
    }

    private fun resetPasteState() {
        prePasteSnapshot = null
        lastPastedText = null
        pasteSettled = false
    }

    private companion object {
        const val PASTE_SETTLE_MS = 150L
        const val CLIPBOARD_RESTORE_DELAY_MS = 300L
    }
}
