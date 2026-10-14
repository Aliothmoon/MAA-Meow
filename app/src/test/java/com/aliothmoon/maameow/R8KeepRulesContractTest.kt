package com.aliothmoon.maameow

import android.content.Context
import com.aliothmoon.maameow.remote.LogcatCaptureServiceImpl
import com.aliothmoon.maameow.remote.RemoteServiceImpl
import com.aliothmoon.maameow.remote.VdmShellServiceImpl
import com.aliothmoon.maameow.root.RootUserService.RemoteProcessServiceEntry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R8KeepRulesContractTest {

    @Test
    fun minifyEnabledAndKeepRulesCoverNamedEntryPoints() {
        val gradle = resolve("build.gradle.kts").readText()
        assertTrue(Regex("""isMinifyEnabled\s*=\s*true""").containsMatchIn(gradle))
        assertTrue(Regex("""isShrinkResources\s*=\s*true""").containsMatchIn(gradle))

        val rules = resolve("proguard-rules.pro").readText()
        val activeRules = rules.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toList()
        assertFalse(activeRules.any { it.startsWith("-dontoptimize") })
        assertFalse(activeRules.any { it.startsWith("-dontshrink") })
        assertFalse(activeRules.any { it.startsWith("-dontobfuscate") })
        assertFalse(activeRules.any { it.startsWith("-keep class com.aliothmoon.maameow.**") })
        assertFalse(activeRules.any { it.contains("com.sun.jna.**") })
        listOf(
            "com.aliothmoon.maameow.bridge.NativeBridgeLib",
            "com.aliothmoon.maameow.maa.DriverClass",
            "com.aliothmoon.maameow.maa.**",
            "com.sun.jna.*",
            "com.sun.jna.Structure",
            "com.sun.jna.Callback",
            "com.aliothmoon.maameow.root.RootUserService\$RemoteProcessServiceEntry",
            "-keep,allowobfuscation interface com.aliothmoon.maameow.root.RootUserService\$RemoteProcessServiceEntry",
            "-keep,allowobfuscation class * implements com.aliothmoon.maameow.root.RootUserService\$RemoteProcessServiceEntry",
            "<init>(...)",
            "com.aliothmoon.maameow.root.RemoteServiceStarter",
            "touchDown",
            "startApp",
            "org.eclipse.angus.mail.smtp.**",
            "org.eclipse.angus.mail.imap.**",
            "org.eclipse.angus.mail.pop3.**",
            "org.eclipse.angus.mail.handlers.**",
            "org.eclipse.angus.mail.util.MailStreamProvider",
            "org.eclipse.angus.activation.*RegistryProviderImpl",
            "org.eclipse.tm4e.**",
            "io.github.rosemoe.sora.langs.textmate.**",
            "org.joni.**",
            "org.jcodings.**",
            "-keepclassmembers enum com.aliothmoon.maameow.**",
            "valueOf(java.lang.String)",
            "com.aliothmoon.maameow.third.FakeContext",
            "com.aliothmoon.maameow.third.FakeContext$*",
            "android.content.ContentResolver",
            "acquireProvider(",
            "com.xzakota.hyper.notification.**",
        ).forEach { token ->
            assertTrue("missing keep token: $token", rules.contains(token))
        }
        assertFalse(rules.contains("-keep class com.aliothmoon.maameow.remote.RemoteServiceImpl"))
        assertFalse(rules.contains("-keep class com.aliothmoon.maameow.remote.LogcatCaptureServiceImpl"))

        val keepXml = resolve("src/main/res/raw/keep.xml").readText()
        assertTrue(keepXml.contains("@string/maa_*"))
        assertTrue(keepXml.contains("@string/achievement_*"))
    }

    @Test
    fun reflectiveProcessServicesDeclareTheEntryContractAndSupportedConstructor() {
        listOf(
            RemoteServiceImpl::class.java,
            LogcatCaptureServiceImpl::class.java,
            VdmShellServiceImpl::class.java,
        ).forEach { serviceClass ->
            assertTrue(
                "${serviceClass.name} must implement RemoteProcessServiceEntry",
                RemoteProcessServiceEntry::class.java.isAssignableFrom(serviceClass),
            )
            assertTrue(
                "${serviceClass.name} needs a no-arg or Context constructor",
                serviceClass.declaredConstructors.any { constructor ->
                    constructor.parameterTypes.isEmpty() ||
                        constructor.parameterTypes.contentEquals(arrayOf(Context::class.java))
                },
            )
        }
    }

    private fun resolve(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath"),
        )
        val file = candidates.firstOrNull { it.isFile }
        checkNotNull(file) { "File not found for test: $relativePath" }
        return file
    }
}
