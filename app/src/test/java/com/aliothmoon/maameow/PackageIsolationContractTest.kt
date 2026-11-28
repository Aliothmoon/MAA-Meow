package com.aliothmoon.maameow

import com.aliothmoon.maameow.remote.CoreDataDir
import com.aliothmoon.maameow.schedule.model.ScheduledExecutionRequest
import com.aliothmoon.maameow.schedule.receiver.ScheduleCountdownActionReceiver
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import com.aliothmoon.maameow.service.AccessibilityHelperService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class PackageIsolationContractTest {

    @Test
    fun accessibilityServiceId_matchesComponentNameShortFormRule() {
        assertEquals(
            "com.aliothmoon.maameow/.service.AccessibilityHelperService",
            AccessibilityHelperService.serviceId("com.aliothmoon.maameow"),
        )
        // .dev 包名与源码 namespace 不同，不能生成前导点简写，否则系统会找 .dev.service.*
        assertEquals(
            "com.aliothmoon.maameow.dev/com.aliothmoon.maameow.service.AccessibilityHelperService",
            AccessibilityHelperService.serviceId("com.aliothmoon.maameow.dev"),
        )
    }

    @Test
    fun coreDataDir_keepsStablePathForRelease_andIsolatesSuffixedBuilds() {
        assertEquals("/data/local/tmp/maameow", CoreDataDir.rootFor("com.aliothmoon.maameow"))
        assertEquals("/data/local/tmp/maameow_dev", CoreDataDir.rootFor("com.aliothmoon.maameow.dev"))
        assertEquals(CoreDataDir.rootFor(BuildConfig.APPLICATION_ID), CoreDataDir.ROOT)
    }

    @Test
    fun customActions_areScopedToApplicationId() {
        val expectedAppId = if (BuildConfig.DEBUG) {
            "com.aliothmoon.maameow.dev"
        } else {
            "com.aliothmoon.maameow"
        }
        assertEquals(expectedAppId, BuildConfig.APPLICATION_ID)

        val prefix = "${BuildConfig.APPLICATION_ID}."
        assertTrue(ScheduledExecutionRequest.ACTION_LAUNCH_PROFILE.startsWith(prefix))
        assertTrue(ScheduledExecutionRequest.ACTION_SHOW_SCHEDULE_EXECUTION.startsWith(prefix))
        assertTrue(ScheduleAlarmManager.ACTION_SCHEDULE_TRIGGER.startsWith(prefix))
        assertTrue(ScheduleAlarmManager.ACTION_SCHEDULE_RESYNC.startsWith(prefix))
        assertTrue(ScheduleCountdownActionReceiver.ACTION_CANCEL.startsWith(prefix))
        assertTrue(ScheduleCountdownActionReceiver.ACTION_START_NOW.startsWith(prefix))
    }

    @Test
    fun manifestProvidersAndCustomActions_useApplicationIdPlaceholder() {
        val manifestFile = TestSources.resolve("src/main/AndroidManifest.xml")
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifestFile)
        val androidNs = "http://schemas.android.com/apk/res/android"

        val providers = doc.getElementsByTagName("provider")
        assertTrue(providers.length > 0)
        for (i in 0 until providers.length) {
            val el = providers.item(i) as Element
            val authorities = el.getAttributeNS(androidNs, "authorities")
            assertTrue(
                "provider authorities should start with \${applicationId}: $authorities",
                authorities.startsWith("\${applicationId}."),
            )
        }

        val actions = doc.getElementsByTagName("action")
        for (i in 0 until actions.length) {
            val el = actions.item(i) as Element
            val name = el.getAttributeNS(androidNs, "name")
            assertFalse(
                "custom action in Manifest should use \${applicationId} instead of hardcoded package: $name",
                name.startsWith("com.aliothmoon.maameow."),
            )
        }
    }

}
