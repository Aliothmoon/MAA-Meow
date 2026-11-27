package com.aliothmoon.maameow.presentation.viewmodel

import android.content.ClipData
import android.view.KeyEvent
import com.aliothmoon.maameow.constant.TextInjectResult
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualDisplayInputControllerTest {

    @Test
    fun isAsciiInjectable_distinguishesAsciiAndUnicode() {
        assertTrue(VirtualDisplayInputController.isAsciiInjectable("Abc123!@#_-\n\t"))
        assertFalse(VirtualDisplayInputController.isAsciiInjectable(""))
        assertFalse(VirtualDisplayInputController.isAsciiInjectable("你好"))
        assertFalse(VirtualDisplayInputController.isAsciiInjectable("abc中文"))
    }

    @Test
    fun pureAscii_ok_injectsDirectlyWithoutTouchingClipboard() = runTest {
        val env = Env(this)
        env.controller.sendText("user@123")
        runCurrent()

        assertEquals(listOf("user@123"), env.remote.injectedTexts)
        assertTrue(env.clipboard.written.isEmpty())
        assertTrue(env.remote.sentKeys.isEmpty())
        assertEquals(0, env.capabilityLostCount)
    }

    @Test
    fun pureAscii_failedMidSequence_doesNotFallbackToClipboardPaste() = runTest {
        val env = Env(this, injectResult = { TextInjectResult.FAILED })
        env.controller.sendText("Abc")
        runCurrent()

        assertEquals(listOf("Abc"), env.remote.injectedTexts)
        assertTrue(env.clipboard.written.isEmpty())
        assertTrue(env.remote.sentKeys.isEmpty())
    }

    @Test
    fun pureAscii_rejected_notifiesCapabilityLostWithoutTouchingClipboard() = runTest {
        val env = Env(this, injectResult = { TextInjectResult.REJECTED })
        env.controller.sendText("abc")
        runCurrent()

        assertTrue(env.clipboard.written.isEmpty())
        assertEquals(1, env.capabilityLostCount)
    }

    @Test
    fun pureAscii_unmappable_fallsBackToClipboardAndRestoresOriginalClip() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Value(originalClip),
            injectResult = { TextInjectResult.UNMAPPABLE },
        )

        env.controller.sendText("~")
        runCurrent()
        assertEquals(listOf("~"), env.clipboard.written)
        assertEquals(listOf(KeyEvent.KEYCODE_PASTE to 0), env.remote.sentKeys)
        assertTrue(env.clipboard.restored.isEmpty())

        advanceTimeBy(450L)
        runCurrent()
        assertEquals(listOf(ClipSnapshot.Value(originalClip)), env.clipboard.restored)
    }

    @Test
    fun rapidChineseSends_serializeAcrossSettleWindowAndRestoreOriginalClip() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(this, initialSnapshot = ClipSnapshot.Value(originalClip))

        env.controller.sendText("你好")
        env.controller.sendText("世界")
        runCurrent()

        assertEquals(listOf("你好"), env.clipboard.written)
        assertEquals(1, env.remote.sentKeys.size)

        advanceTimeBy(150L)
        runCurrent()
        assertEquals(listOf("你好", "世界"), env.clipboard.written)
        assertEquals(2, env.remote.sentKeys.size)

        advanceTimeBy(450L)
        runCurrent()
        assertEquals(listOf(ClipSnapshot.Value(originalClip)), env.clipboard.restored)
    }

    @Test
    fun unreadableInitialClipboard_isNeverClearedOnRestore() = runTest {
        val env = Env(this, initialSnapshot = ClipSnapshot.Unreadable)
        env.controller.sendText("中文")
        advanceTimeBy(500L)
        runCurrent()

        assertEquals(listOf(ClipSnapshot.Unreadable), env.clipboard.restored)
        assertFalse(env.clipboard.cleared)
    }

    @Test
    fun emptyInitialClipboard_isClearedOnRestore() = runTest {
        val env = Env(this, initialSnapshot = ClipSnapshot.Empty)
        env.controller.sendText("中文")
        advanceTimeBy(500L)
        runCurrent()

        assertTrue(env.clipboard.cleared)
    }

    @Test
    fun userOverwritesClipboardDuringDelay_skipsRestore() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(this, initialSnapshot = ClipSnapshot.Value(originalClip))
        env.controller.sendText("中文")
        advanceTimeBy(200L)
        runCurrent()

        env.clipboard.current = "user_new_copy"
        advanceTimeBy(300L)
        runCurrent()

        assertTrue(env.clipboard.restored.isEmpty())
    }

    @Test
    fun pasteRejected_restoresImmediatelyAndSignalsCapabilityLost() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Value(originalClip),
            ownFocus = false,
            sendKeyResult = false,
        )
        env.controller.sendText("中文")
        runCurrent()

        assertEquals(listOf(ClipSnapshot.Value(originalClip)), env.clipboard.restored)
        assertEquals(1, env.capabilityLostCount)
    }

    @Test
    fun clearDuringPendingRestore_restoresSynchronously() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(this, initialSnapshot = ClipSnapshot.Value(originalClip))
        env.controller.sendText("中文")
        advanceTimeBy(200L)
        runCurrent()
        assertTrue(env.clipboard.restored.isEmpty())

        env.controller.clear()
        assertEquals(listOf(ClipSnapshot.Value(originalClip)), env.clipboard.restored)
    }

    private class Env(
        scope: TestScope,
        initialSnapshot: ClipSnapshot = ClipSnapshot.Empty,
        ownFocus: Boolean = true,
        injectResult: (String) -> Int = { TextInjectResult.OK },
        sendKeyResult: Boolean = true,
    ) {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val clipboard = FakeClipboardPort(initialSnapshot)
        val remote = FakeRemoteInputPort(ownFocus, injectResult, sendKeyResult)
        var capabilityLostCount = 0
        val controller = VirtualDisplayInputController(
            scope = scope,
            clipboard = clipboard,
            remote = remote,
            onCapabilityLost = { capabilityLostCount++ },
            ioDispatcher = dispatcher,
            pasteSettleMs = 150L,
            clipboardRestoreDelayMs = 300L,
        )
    }

    private class FakeClipboardPort(private val initial: ClipSnapshot) : ClipboardPort {
        val written = mutableListOf<String>()
        val restored = mutableListOf<ClipSnapshot>()
        var current: String? = null
        var cleared = false

        override fun snapshot(): ClipSnapshot = initial

        override fun setText(text: String): Boolean {
            written += text
            current = text
            return true
        }

        override fun currentText(): String? = current

        override fun restore(snapshot: ClipSnapshot) {
            restored += snapshot
            if (snapshot === ClipSnapshot.Empty) cleared = true
        }
    }

    private class FakeRemoteInputPort(
        var ownFocus: Boolean,
        val injectResult: (String) -> Int,
        var sendKeyResult: Boolean,
    ) : RemoteInputPort {
        val injectedTexts = mutableListOf<String>()
        val sentKeys = mutableListOf<Pair<Int, Int>>()

        override fun hasOwnFocus(): Boolean = ownFocus

        override fun injectText(text: String): Int {
            injectedTexts += text
            return injectResult(text)
        }

        override fun sendKey(keyCode: Int, metaState: Int): Boolean {
            sentKeys += keyCode to metaState
            return sendKeyResult
        }
    }
}
