package com.aliothmoon.maameow

import com.aliothmoon.maameow.manager.ShizukuFailureDiagnostics
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 签名匹配行为锁定：manager 更新可能改变日志形态，错配只会静默丢失提示 */
class ShizukuFailureDiagnosticsTest {

    @Test
    fun classify_matchesMediaTekMakeApplicationSignature() {
        val log = """
            [2025-06-08 03:53:28.775 Uid(value=shell):18002:18002 W/ShizukuServiceStarter]
            unable to start service dev.pkg/dev.pkg.Service...
            java.lang.reflect.InvocationTargetException
                at rikka.shizuku.Vr.a(SourceFile:209)
                at moe.shizuku.starter.ServiceStarter.main(SourceFile:14)
            Caused by: java.lang.NullPointerException
                at android.app.LoadedApk.makeApplicationInner(LoadedApk.java:1526)
                at android.app.LoadedApk.makeApplication(LoadedApk.java:1427)
        """.trimIndent()
        val hint = ShizukuFailureDiagnostics.classify(log)
        assertNotNull("MTK 签名必须命中", hint)
        assertTrue(hint!!.contains("makeApplication"))
        assertTrue(hint.contains("1198"))
    }

    @Test
    fun classify_matchesGenericStarterCrash() {
        val log = """
            [2025-01-01 00:00:00.000 W/ShizukuServiceStarter]
            unable to start service dev.pkg/dev.pkg.Service...
            java.lang.ClassNotFoundException: Didn't find class "dev.pkg.Service"
        """.trimIndent()
        val hint = ShizukuFailureDiagnostics.classify(log)
        assertNotNull("一般崩溃也要给出定位指引", hint)
        assertTrue(hint!!.contains("ShizukuServiceStarter"))
    }

    @Test
    fun classify_ignoresUnrelatedLogs() {
        assertNull(ShizukuFailureDiagnostics.classify("logcat: nothing to dump"))
        assertNull(ShizukuFailureDiagnostics.classify(""))
    }

    @Test
    fun classify_isCaseInsensitive() {
        val hint = ShizukuFailureDiagnostics.classify(
            "UNABLE TO START SERVICE x/y\nat android.app.LoadedApk.MAKEAPPLICATIONINNER"
        )
        assertNotNull("大小写漂移不应丢失归因", hint)
    }
}
