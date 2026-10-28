package com.aliothmoon.maameow.remote.internal

import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_IGNORED
import com.aliothmoon.maameow.third.Ln
import com.android.internal.app.IAppOpsService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppOpsHelperTest {
    private val service = mockk<IAppOpsService>(relaxed = true)
    private val binder = { service }
    private val pkg = "com.example"
    private val uid = 10_100
    private val code = 42
    private val pkgOp = AppOp("op") { code }
    private val uidOp = AppOp("op", uidScoped = true) { code }

    @Before
    fun setUp() {
        mockkStatic(Ln::class)
        every { Ln.i(any()) } just runs
        every { Ln.w(any()) } just runs
    }

    @After
    fun tearDown() = unmockkStatic(Ln::class)

    @Test
    fun set_packageOp_writesPackageModeOnly() {
        every { service.checkOperation(code, uid, pkg) } returnsMany listOf(MODE_IGNORED, MODE_ALLOWED)

        assertTrue(AppOpsHelper.set(pkgOp, pkg, uid, MODE_ALLOWED, binder))
        verify { service.setMode(code, uid, pkg, MODE_ALLOWED) }
        verify(exactly = 0) { service.setUidMode(any(), any(), any()) }
    }

    @Test
    fun set_uidScopedOp_writesBothLevels() {
        every { service.checkOperation(code, uid, pkg) } returnsMany listOf(MODE_IGNORED, MODE_ALLOWED)

        assertTrue(AppOpsHelper.set(uidOp, pkg, uid, MODE_ALLOWED, binder))
        verify { service.setUidMode(code, uid, MODE_ALLOWED) }
        verify { service.setMode(code, uid, pkg, MODE_ALLOWED) }
    }

    @Test
    fun set_uidLevelThrows_stillWritesPackageLevel() {
        every { service.setUidMode(any(), any(), any()) } throws SecurityException("denied")
        every { service.checkOperation(code, uid, pkg) } returnsMany listOf(MODE_IGNORED, MODE_ALLOWED)

        assertTrue(AppOpsHelper.set(uidOp, pkg, uid, MODE_ALLOWED, binder))
        verify { service.setMode(code, uid, pkg, MODE_ALLOWED) }
    }

    @Test
    fun set_alreadyAtTarget_skipsWrites() {
        every { service.checkOperation(code, uid, pkg) } returns MODE_ALLOWED

        assertTrue(AppOpsHelper.set(uidOp, pkg, uid, MODE_ALLOWED, binder))
        verify(exactly = 0) { service.setUidMode(any(), any(), any()) }
        verify(exactly = 0) { service.setMode(any(), any(), any(), any()) }
    }

    @Test
    fun set_failsWhenReadBackDiffers() {
        every { service.checkOperation(code, uid, pkg) } returns MODE_IGNORED

        assertFalse(AppOpsHelper.set(pkgOp, pkg, uid, MODE_ALLOWED, binder))
    }

    @Test
    fun set_binderThrows_returnsFalse() {
        every { service.setMode(any(), any(), any(), any()) } throws SecurityException("denied")
        every { service.checkOperation(code, uid, pkg) } returns MODE_IGNORED

        assertFalse(AppOpsHelper.set(pkgOp, pkg, uid, MODE_ALLOWED, binder))
    }

    @Test
    fun set_invalidUid_neverTouchesBinder() {
        assertFalse(AppOpsHelper.set(pkgOp, pkg, 0, MODE_ALLOWED, binder))
        verify(exactly = 0) { service.checkOperation(any(), any(), any()) }
        verify(exactly = 0) { service.setMode(any(), any(), any(), any()) }
    }

    @Test
    fun unavailableOp_neverTouchesBinder() {
        val op = AppOp("op") { null }

        assertFalse(AppOpsHelper.set(op, pkg, uid, MODE_ALLOWED, binder))
        assertNull(AppOpsHelper.check(op, pkg, uid, binder))
        verify(exactly = 0) { service.checkOperation(any(), any(), any()) }
        verify(exactly = 0) { service.setMode(any(), any(), any(), any()) }
    }
}
