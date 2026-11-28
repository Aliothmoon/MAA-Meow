package com.aliothmoon.maameow.presentation.viewmodel

import android.content.ClipData
import android.view.KeyEvent
import com.aliothmoon.maameow.constant.TextInjectResult
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualDisplayInputControllerTest {

    @Test
    fun pureAscii_ok_injectsDirectlyWithoutTouchingClipboard() = runTest {
        val env = Env(this)
        env.controller.sendText("user@123")
        runCurrent()

        assertEquals(listOf("user@123"), env.remote.injectedTexts)
        assertTrue(env.clipboard.written.isEmpty())
        assertTrue(env.remote.sentKeys.isEmpty())
        assertEquals(0, env.capabilityLostCount)
        assertTrue(env.failures.isEmpty())
    }

    @Test
    fun failedMidSequence_notifiesSendFailedWithoutFallbackPaste() = runTest {
        val env = Env(this, injectResult = { TextInjectResult.FAILED })
        env.controller.sendText("Abc")
        runCurrent()

        assertEquals(listOf("Abc"), env.remote.injectedTexts)
        assertTrue(env.clipboard.written.isEmpty())
        assertTrue(env.remote.sentKeys.isEmpty())
        assertEquals(listOf(InputFailure.SEND_FAILED), env.failures)
    }

    @Test
    fun pasteUnsupported_notifiesPasteUnsupportedWithoutTouchingClipboardOrClosingBar() = runTest {
        val env = Env(this, injectResult = { TextInjectResult.PASTE_UNSUPPORTED })
        env.controller.sendText("你好")
        runCurrent()

        assertEquals(listOf("你好"), env.remote.injectedTexts)
        assertTrue(env.clipboard.written.isEmpty())
        assertTrue(env.remote.sentKeys.isEmpty())
        assertEquals(0, env.capabilityLostCount)
        assertEquals(listOf(InputFailure.PASTE_UNSUPPORTED), env.failures)
    }

    @Test
    fun rejected_notifiesCapabilityLostAndSendFailedWithoutTouchingClipboard() = runTest {
        val env = Env(this, injectResult = { TextInjectResult.REJECTED })
        env.controller.sendText("abc")
        runCurrent()

        assertTrue(env.clipboard.written.isEmpty())
        assertEquals(1, env.capabilityLostCount)
        assertEquals(listOf(InputFailure.SEND_FAILED), env.failures)
    }

    @Test
    fun unmappable_fallsBackToHostClipboardAndRestoresOriginalClip() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Value(originalClip),
            injectResult = { TextInjectResult.UNMAPPABLE },
        )

        env.controller.sendText("你好")
        runCurrent()
        assertEquals(listOf("你好"), env.clipboard.written)
        assertEquals(listOf(KeyEvent.KEYCODE_PASTE to 0), env.remote.sentKeys)
        assertTrue(env.clipboard.restored.isEmpty())

        advanceTimeBy(450L)
        runCurrent()
        assertEquals(listOf(ClipSnapshot.Value(originalClip)), env.clipboard.restored)
    }

    @Test
    fun rapidHostPasteSends_serializeAcrossSettleWindowAndRestoreOriginalClip() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Value(originalClip),
            injectResult = { TextInjectResult.UNMAPPABLE },
        )

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
    fun secondHostPasteSetTextFailure_preservesFirstRestoreSchedule() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Value(originalClip),
            injectResult = { TextInjectResult.UNMAPPABLE },
        )

        env.controller.sendText("第一条")
        advanceTimeBy(200L)
        runCurrent()
        assertTrue(env.clipboard.restored.isEmpty())

        env.clipboard.failNextSetText = true
        env.controller.sendText("第二条")
        runCurrent()
        assertEquals(listOf(InputFailure.SEND_FAILED), env.failures)
        assertTrue(env.clipboard.restored.isEmpty())

        advanceTimeBy(250L)
        runCurrent()
        assertEquals(listOf(ClipSnapshot.Value(originalClip)), env.clipboard.restored)
    }

    @Test
    fun clearDuringInFlightPaste_doesNotPrematurelyRestoreOriginalClipboard() = runTest {
        val originalClip = mockk<ClipData>()
        val pasteGate = CompletableDeferred<Boolean>()
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Value(originalClip),
            injectResult = { TextInjectResult.UNMAPPABLE },
            sendKeyBlock = { _, _ -> pasteGate.await() },
        )

        env.controller.sendText("测试文本")
        runCurrent()
        assertEquals("测试文本", env.clipboard.current)

        env.controller.clear()
        assertTrue(env.clipboard.restored.isEmpty())
        assertEquals("测试文本", env.clipboard.current)

        pasteGate.complete(true)
        advanceTimeBy(500L)
        runCurrent()
        assertTrue(env.clipboard.restored.isEmpty())
    }

    @Test
    fun clearDuringSettleWindow_doesNotPrematurelyRestoreOriginalClipboard() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Value(originalClip),
            injectResult = { TextInjectResult.UNMAPPABLE },
        )

        env.controller.sendText("测试文本")
        advanceTimeBy(50L)
        runCurrent()

        env.controller.clear()
        assertTrue(env.clipboard.restored.isEmpty())
        assertEquals("测试文本", env.clipboard.current)
    }

    @Test
    fun clearAfterSettleBeforeRestoreTimer_restoresOriginalClipboardImmediately() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Value(originalClip),
            injectResult = { TextInjectResult.UNMAPPABLE },
        )

        env.controller.sendText("测试文本")
        advanceTimeBy(200L)
        runCurrent()
        assertTrue(env.clipboard.restored.isEmpty())

        env.controller.clear()
        assertEquals(listOf(ClipSnapshot.Value(originalClip)), env.clipboard.restored)
    }

    @Test
    fun emptyInitialClipboard_isClearedOnRestore() = runTest {
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Empty,
            injectResult = { TextInjectResult.UNMAPPABLE },
        )
        env.controller.sendText("中文")
        advanceTimeBy(500L)
        runCurrent()

        assertTrue(env.clipboard.cleared)
    }

    @Test
    fun userOverwritesClipboardDuringDelay_skipsRestore() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Value(originalClip),
            injectResult = { TextInjectResult.UNMAPPABLE },
        )
        env.controller.sendText("中文")
        advanceTimeBy(200L)
        runCurrent()

        env.clipboard.current = "user_new_copy"
        advanceTimeBy(300L)
        runCurrent()

        assertTrue(env.clipboard.restored.isEmpty())
    }

    @Test
    fun clipboardUnreadableAfterFocusLoss_stillRestoresOriginalClip() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Value(originalClip),
            injectResult = { TextInjectResult.UNMAPPABLE },
        )
        env.controller.sendText("中文")
        advanceTimeBy(200L)
        runCurrent()

        env.clipboard.current = null
        advanceTimeBy(300L)
        runCurrent()

        assertEquals(listOf(ClipSnapshot.Value(originalClip)), env.clipboard.restored)
    }

    @Test
    fun sendText_reportsResultForInputBarRefill() = runTest {
        var okResult: Boolean? = null
        var failedResult: Boolean? = null
        var unsupportedResult: Boolean? = null
        Env(this).controller.sendText("abc") { okResult = it }
        Env(this, injectResult = { TextInjectResult.FAILED }).controller
            .sendText("abc") { failedResult = it }
        Env(this, injectResult = { TextInjectResult.PASTE_UNSUPPORTED }).controller
            .sendText("你好") { unsupportedResult = it }
        runCurrent()

        assertEquals(true, okResult)
        assertEquals(false, failedResult)
        assertEquals(false, unsupportedResult)
    }

    @Test
    fun pasteRejectedWithoutOwnFocus_restoresImmediatelyAndSignalsCapabilityLost() = runTest {
        val originalClip = mockk<ClipData>()
        val env = Env(
            this,
            initialSnapshot = ClipSnapshot.Value(originalClip),
            ownFocus = false,
            injectResult = { TextInjectResult.UNMAPPABLE },
            sendKeyBlock = { _, _ -> false },
        )
        env.controller.sendText("中文")
        runCurrent()

        assertEquals(listOf(ClipSnapshot.Value(originalClip)), env.clipboard.restored)
        assertEquals(1, env.capabilityLostCount)
        assertEquals(listOf(InputFailure.SEND_FAILED), env.failures)
    }

    @Test
    fun sendKeyFailure_notifiesSendFailedAndChecksOwnFocus() = runTest {
        val env = Env(
            this,
            ownFocus = false,
            sendKeyBlock = { _, _ -> false },
        )
        env.controller.sendKey(KeyEvent.KEYCODE_DEL)
        runCurrent()

        assertEquals(listOf(KeyEvent.KEYCODE_DEL to 0), env.remote.sentKeys)
        assertEquals(listOf(InputFailure.SEND_FAILED), env.failures)
        assertEquals(1, env.capabilityLostCount)
    }

    private class Env(
        scope: TestScope,
        initialSnapshot: ClipSnapshot = ClipSnapshot.Empty,
        ownFocus: Boolean = true,
        injectResult: suspend (String) -> Int = { TextInjectResult.OK },
        sendKeyBlock: suspend (Int, Int) -> Boolean = { _, _ -> true },
    ) {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val clipboard = FakeClipboardPort(initialSnapshot)
        val remote = FakeRemoteInputPort(ownFocus, injectResult, sendKeyBlock)
        var capabilityLostCount = 0
        val failures = mutableListOf<InputFailure>()
        val controller = VirtualDisplayInputController(
            scope = scope,
            clipboard = clipboard,
            remote = remote,
            onCapabilityLost = { capabilityLostCount++ },
            onFailure = { failures += it },
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
        var failNextSetText = false

        override fun snapshot(): ClipSnapshot = initial

        override fun setText(text: String): Boolean {
            if (failNextSetText) {
                failNextSetText = false
                return false
            }
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
        val injectResult: suspend (String) -> Int,
        val sendKeyBlock: suspend (Int, Int) -> Boolean,
    ) : RemoteInputPort {
        val injectedTexts = mutableListOf<String>()
        val sentKeys = mutableListOf<Pair<Int, Int>>()

        override suspend fun hasOwnFocus(): Boolean = ownFocus

        override suspend fun injectText(text: String): Int {
            injectedTexts += text
            return injectResult(text)
        }

        override suspend fun sendKey(keyCode: Int, metaState: Int): Boolean {
            sentKeys += keyCode to metaState
            return sendKeyBlock(keyCode, metaState)
        }
    }
}
