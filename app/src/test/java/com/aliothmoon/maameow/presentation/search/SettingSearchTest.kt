package com.aliothmoon.maameow.presentation.search

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingSearchTest {

    // ===== 匹配 =====

    private val anyEntry = SettingSearchIndex.entries.first()

    private fun item(title: String, description: String = "", keywords: String = "", path: String = "") =
        SearchableSetting(anyEntry, title, description, keywords, path)

    private val screenOff = item("熄屏挂机时关闭屏幕", keywords = "息屏 黑屏 锁屏", path = "后台任务 › 快捷操作")
    private val pip = item("首页画中画", keywords = "小窗 PiP", path = "后台运行")
    private val wake = item("定时任务解锁方式", description = "若设备已锁屏会自动解锁", path = "定时 › 唤醒解锁")

    private fun titles(query: String) =
        SettingSearchMatcher.filter(listOf(screenOff, pip, wake), query).map { it.title }

    @Test
    fun `blank query yields nothing`() {
        assertTrue(titles("").isEmpty())
        assertTrue(titles("   ").isEmpty())
    }

    @Test
    fun `matches title, description, keywords and path`() {
        assertEquals(listOf("熄屏挂机时关闭屏幕"), titles("黑屏"))
        assertEquals(listOf("首页画中画"), titles("后台运行"))
        assertEquals(listOf("定时任务解锁方式"), titles("自动解锁"))
    }

    @Test
    fun `case insensitive`() {
        assertEquals(listOf("首页画中画"), titles("pip"))
    }

    @Test
    fun `every token must match`() {
        assertEquals(listOf("熄屏挂机时关闭屏幕"), titles("锁屏 快捷操作"))
        assertTrue(titles("锁屏 画中画").isEmpty())
    }

    @Test
    fun `title hits rank before other hits`() {
        // 都非标题命中，保持清单顺序
        assertEquals(listOf("熄屏挂机时关闭屏幕", "定时任务解锁方式"), titles("锁屏"))
        // 标题命中排前
        val unlockTitle = item("解锁凭证")
        val mention = item("其它", description = "解锁相关")
        assertEquals(
            listOf("解锁凭证", "其它"),
            SettingSearchMatcher.filter(listOf(mention, unlockTitle), "解锁").map { it.title },
        )
    }

    // ===== 请求时效 =====

    @Test
    fun `request expires after ttl`() {
        val request = SettingSearchRequest(anyEntry, requestedAtMs = 1_000)
        assertTrue(request.isFresh(nowMs = 1_000))
        assertTrue(request.isFresh(nowMs = 6_000))
        assertFalse(request.isFresh(nowMs = 6_001))
    }

    // ===== 清单与页面的契约 =====

    private val stringNames: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    private fun source(relativePath: String): String {
        val file = listOf(File(relativePath), File("app/$relativePath"), File("../app/$relativePath"))
            .firstOrNull { it.isFile } ?: error("source not found: $relativePath")
        return file.readText()
    }

    private fun sourceFor(location: SettingLocation): String = when (location) {
        is SettingLocation.Section ->
            source("src/main/java/com/aliothmoon/maameow/presentation/view/settings/SettingsView.kt")

        SettingLocation.BackgroundActions ->
            source("src/main/java/com/aliothmoon/maameow/presentation/view/background/BackgroundTaskView.kt")

        is SettingLocation.Page -> when (location.route) {
            Routes.SCHEDULE_WAKE_UNLOCK ->
                source("src/main/java/com/aliothmoon/maameow/schedule/ui/ScheduleWakeUnlockView.kt")

            Routes.SCHEDULE_APP_BLACKLIST ->
                source("src/main/java/com/aliothmoon/maameow/schedule/ui/ScheduleAppBlacklistView.kt")

            else -> error("no source mapping for route ${location.route}")
        }
    }

    @Test
    fun `every entry has its anchor on the target page`() {
        val missing = SettingSearchIndex.entries.filterNot { entry ->
            val name = stringNames.getValue(entry.anchorRes)
            "SettingSearchTarget(R.string.$name)" in sourceFor(entry.location)
        }.map { stringNames.getValue(it.titleRes) }
        assertTrue("anchors missing on page: $missing", missing.isEmpty())
    }

    @Test
    fun `titles are unique`() {
        val titles = SettingSearchIndex.entries.map { it.titleRes }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun `settings page section order matches SettingsSections ORDER`() {
        val view = source("src/main/java/com/aliothmoon/maameow/presentation/view/settings/SettingsView.kt")
        val inPage = Regex("""sectionKey = SettingsSections\.(\w+),""").findAll(view)
            .map { SettingsSections::class.java.getField(it.groupValues[1]).get(null) as String }
            .toList()
        assertEquals(SettingsSections.ORDER, inPage)
        val indexed = SettingSearchIndex.entries.mapNotNull { (it.location as? SettingLocation.Section)?.sectionKey }
        assertTrue(SettingsSections.ORDER.containsAll(indexed))
    }
}
