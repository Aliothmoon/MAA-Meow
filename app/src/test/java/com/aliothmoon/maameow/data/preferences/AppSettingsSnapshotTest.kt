package com.aliothmoon.maameow.data.preferences

import com.aliothmoon.maameow.domain.models.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class AppSettingsSnapshotTest {

    private fun lines(settings: AppSettings) = AppSettingsSnapshot.format(settings)
        .lines()
        .mapNotNull { line ->
            val idx = line.indexOf(" = ").takeIf { it > 0 } ?: return@mapNotNull null
            line.take(idx).trim() to line.substring(idx + 3)
        }
        .toMap()

    @Test
    fun `敏感字段只暴露设没设`() {
        val filled = lines(
            AppSettings(
                mirrorChyanCdk = "ABCD-1234",
                wakeCredential = "135790",
                yituliuOpenApiToken = "tok",
                penguinId = "penguin-uuid",
                customBackgroundToken = "bg-token",
            )
        )
        listOf(
            "mirrorChyanCdk",
            "wakeCredential",
            "yituliuOpenApiToken",
            "penguinId",
            "customBackgroundToken",
        ).forEach { key ->
            assertTrue("$key 应掩码", filled.getValue(key) == "<set>")
        }

        val raw = AppSettingsSnapshot.format(
            AppSettings(mirrorChyanCdk = "ABCD-1234", wakeCredential = "135790")
        )
        assertFalse(raw.contains("ABCD-1234"))
        assertFalse(raw.contains("135790"))

        val empty = lines(AppSettings())
        assertTrue(empty.getValue("mirrorChyanCdk") == "<empty>")
        assertTrue(empty.getValue("penguinId") == "<empty>")
    }

    @Test
    fun `行为类字段原样透出`() {
        val map = lines(
            AppSettings(
                operBoxUseYituliuApi = "true",
                customBackgroundEnabled = "true",
                coreDataLocation = "LOCAL_TMP",
                runMode = "FOREGROUND",
                tasksOverrideEnabled = "true",
            )
        )
        assertTrue(map.getValue("operBoxUseYituliuApi") == "true")
        assertTrue(map.getValue("customBackgroundEnabled") == "true")
        assertTrue(map.getValue("coreDataLocation") == "LOCAL_TMP")
        assertTrue(map.getValue("runMode") == "FOREGROUND")
        assertTrue(map.getValue("tasksOverrideEnabled") == "true")
    }

    @Test
    fun `快捷选项开关在快照里`() {
        val map =
            lines(AppSettings(runDurationLimitEnabled = "true", runDurationLimitMinutes = "90"))
        listOf(
            "muteOnGameLaunch",
            "closeAppOnTaskEnd",
            "runDurationLimitEnabled",
            "runDurationLimitMinutes",
            "useHardwareScreenOff",
            "showTouchPreview",
        ).forEach { key ->
            assertTrue("快照缺少 $key", map.containsKey(key))
        }
        assertTrue(map.getValue("runDurationLimitMinutes") == "90")
    }

    @Test
    fun `更新日志正文不入包`() {
        val text = AppSettingsSnapshot.format(
            AppSettings(
                pendingChangelogContent = "## 巨长的 markdown 正文",
                currentChangelogContent = "## 另一段巨长正文",
            )
        )
        assertFalse(text.contains("巨长"))
        assertTrue(text.contains("omitted: "))
    }

    @Test
    fun `设备本地图标路径不入包`() {
        val text = AppSettingsSnapshot.format(
            AppSettings(
                liveUpdateCustomTrackerPath =
                    "/data/user/0/com.aliothmoon.maameow/files/live_update/tracker_icon_1699",
            )
        )
        assertFalse(text.contains("tracker_icon_1699"))
        assertTrue(text.contains("omitted: "))
    }

    @Test
    fun `疑似凭证字段必须掩码`() {
        // 掩码靠硬编码名单，新增凭证字段容易漏；这里兜底，命中关键字就必须已掩码
        val suspicious = Regex("(?i)cdk|credential|token|secret|key|password|pin")
        // 命中关键字但确非凭证的字段加进来，并在此说明理由
        val allowlist = emptySet<String>()
        val descriptor = AppSettings.serializer().descriptor
        val leaked = (0 until descriptor.elementsCount)
            .map { descriptor.getElementName(it) }
            .filter { suspicious.containsMatchIn(it) }
            .filterNot { it in AppSettingsSnapshot.MASKED || it in allowlist }
        assertTrue("疑似凭证字段未掩码: $leaked", leaked.isEmpty())
    }
}
