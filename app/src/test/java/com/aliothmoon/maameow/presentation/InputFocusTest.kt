package com.aliothmoon.maameow.presentation

import android.content.Context
import android.os.IBinder
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class InputFocusTest {
    private val focus = mockk<FocusManager>(relaxed = true)
    private val host = mockk<View>(relaxed = true)
    private val context = mockk<Context>()
    private val token = mockk<IBinder>()
    private val imm = mockk<InputMethodManager>(relaxed = true)
    private val keyboard = mockk<SoftwareKeyboardController>(relaxed = true)

    init {
        every { host.context } returns context
        every { host.windowToken } returns token
        every { context.getSystemService(Context.INPUT_METHOD_SERVICE) } returns imm
    }

    @Test
    fun composeInputUsesControllerWithoutDuplicateImmCall() {
        // 输入会话期间 AndroidComposeView 自报 text editor
        every { host.findFocus() } returns host
        every { host.onCheckIsTextEditor() } returns true

        clearInputFocus(focus, host, keyboard)

        verify(exactly = 1) { focus.clearFocus(force = true) }
        verify(exactly = 1) { keyboard.hide() }
        verify(exactly = 0) { host.clearFocus() }
        verify(exactly = 0) { imm.hideSoftInputFromWindow(any(), any()) }
    }

    @Test
    fun nativeEditorIsCapturedBeforeComposeFocusChanges() {
        val editor = mockk<View>(relaxed = true)
        var focused: View? = editor
        every { host.findFocus() } answers { focused }
        every { editor.onCheckIsTextEditor() } returns true
        every { focus.clearFocus(any()) } answers { focused = null }

        clearInputFocus(focus, host, keyboard)

        verify(exactly = 1) { editor.clearFocus() }
        verify(exactly = 1) { imm.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS) }
        verify(exactly = 0) { keyboard.hide() }
    }

    @Test
    fun missingControllerFallsBackToImmAndPreservesForce() {
        every { host.findFocus() } returns null

        clearInputFocus(focus, host, force = false)

        verify(exactly = 1) { focus.clearFocus(force = false) }
        verify(exactly = 1) { imm.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS) }
    }
}
