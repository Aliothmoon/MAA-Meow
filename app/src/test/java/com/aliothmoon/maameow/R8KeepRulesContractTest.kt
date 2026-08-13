package com.aliothmoon.maameow

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R8KeepRulesContractTest {

    @Test
    fun minifyDefaultsOnAndKeepRulesCoverNamedEntryPoints() {
        val gradle = resolve("build.gradle.kts").readText()
        assertTrue(gradle.contains("maa.minify"))
        assertTrue(gradle.contains(".orElse(true)"))
        assertTrue(
            Regex("""isMinifyEnabled\s*=\s*enableMinify""").containsMatchIn(gradle),
        )

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
            "com.aliothmoon.maameow.remote.RemoteServiceImpl",
            "com.aliothmoon.maameow.remote.LogcatCaptureServiceImpl",
            "com.aliothmoon.maameow.root.RootServiceStarter",
            "touchDown",
            "startApp",
            "org.eclipse.angus.mail.smtp.**",
            "org.eclipse.angus.mail.imap.**",
            "org.eclipse.angus.mail.pop3.**",
            "org.eclipse.angus.mail.handlers.**",
            "org.eclipse.angus.mail.util.MailStreamProvider",
            "org.eclipse.angus.activation.*RegistryProviderImpl",
            "-keepclassmembers enum com.aliothmoon.maameow.**",
            "valueOf(java.lang.String)",
        ).forEach { token ->
            assertTrue("missing keep token: $token", rules.contains(token))
        }

        val keepXml = resolve("src/main/res/raw/keep.xml").readText()
        assertTrue(keepXml.contains("@string/maa_*"))
        assertTrue(keepXml.contains("@string/achievement_*"))
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
