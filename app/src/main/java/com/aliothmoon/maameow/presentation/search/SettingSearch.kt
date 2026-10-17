package com.aliothmoon.maameow.presentation.search

import android.os.Build
import android.os.SystemClock
import androidx.annotation.StringRes
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 折叠存档键兼搜索定位标识 */
object SettingsSections {
    const val UPDATE = "settings_section_update"
    const val LOG = "settings_section_log"
    const val DISPLAY = "settings_section_display"
    const val RUNTIME = "settings_section_runtime"
    const val BACKGROUND_RUN = "settings_section_background_run"
    const val THIRD_PARTY = "settings_section_third_party"
    const val TASK = "settings_section_task"
    const val DATA = "settings_section_data"
    const val NOTIFICATION = "settings_section_notification"
    const val ACHIEVEMENT = "settings_section_achievement"
    const val ABOUT = "settings_section_about"

    /** 与设置页分区顺序一致 */
    val ORDER = listOf(
        UPDATE, LOG, DISPLAY, RUNTIME, BACKGROUND_RUN, THIRD_PARTY,
        TASK, DATA, NOTIFICATION, ACHIEVEMENT, ABOUT,
    )
}

/** [path] 为结果面包屑 */
sealed interface SettingLocation {
    val path: List<Int>

    data class Section(val sectionKey: String, @StringRes val titleRes: Int) : SettingLocation {
        override val path: List<Int> get() = listOf(titleRes)
    }

    data class Page(val route: String, override val path: List<Int>) : SettingLocation

    /** 后台任务页的「快捷操作」浮层 */
    data object BackgroundActions : SettingLocation {
        override val path: List<Int> =
            listOf(R.string.bottom_nav_background_task, R.string.bg_actions_title)
    }
}

/**
 * @param anchorRes 复合控件的子项借用外层锚点
 * @param keywordsRes 空格分隔的同义词
 */
data class SettingSearchEntry(
    @StringRes val titleRes: Int,
    val location: SettingLocation,
    @StringRes val descRes: Int? = null,
    @StringRes val keywordsRes: Int? = null,
    @StringRes val anchorRes: Int = titleRes,
    val minSdk: Int = Build.VERSION_CODES.BASE,
) {
    fun isAvailable(sdkInt: Int): Boolean = sdkInt >= minSdk
}

/** 只收常显项：依赖其它开关才出现的子项定位不到，交给父项 */
object SettingSearchIndex {

    private fun section(key: String, @StringRes titleRes: Int) = SettingLocation.Section(key, titleRes)

    private val update = section(SettingsSections.UPDATE, R.string.settings_section_update)
    private val log = section(SettingsSections.LOG, R.string.settings_section_log)
    private val display = section(SettingsSections.DISPLAY, R.string.settings_section_display)
    private val runtime = section(SettingsSections.RUNTIME, R.string.settings_section_runtime)
    private val backgroundRun =
        section(SettingsSections.BACKGROUND_RUN, R.string.settings_section_background_run)
    private val thirdParty = section(SettingsSections.THIRD_PARTY, R.string.settings_section_third_party)
    private val task = section(SettingsSections.TASK, R.string.settings_section_task)
    private val data = section(SettingsSections.DATA, R.string.settings_section_data)
    private val notification =
        section(SettingsSections.NOTIFICATION, R.string.settings_section_notification)
    private val achievement = section(SettingsSections.ACHIEVEMENT, R.string.settings_section_achievement)
    private val about = section(SettingsSections.ABOUT, R.string.settings_section_about)

    private val wakeUnlockPage = SettingLocation.Page(
        route = Routes.SCHEDULE_WAKE_UNLOCK,
        path = listOf(R.string.bottom_nav_schedule, R.string.schedule_wake_unlock_title),
    )

    private val appBlacklistPage = SettingLocation.Page(
        route = Routes.SCHEDULE_APP_BLACKLIST,
        path = listOf(R.string.bottom_nav_schedule, R.string.schedule_app_blacklist_title),
    )

    val entries: List<SettingSearchEntry> = listOf(
        SettingSearchEntry(R.string.settings_reinit_resource_title, update, R.string.settings_reinit_resource_desc),
        SettingSearchEntry(R.string.settings_auto_check_update_title, update, R.string.settings_auto_check_update_desc),
        SettingSearchEntry(R.string.settings_auto_download_update_title, update, R.string.settings_auto_download_update_desc),
        SettingSearchEntry(R.string.settings_update_channel_title, update, R.string.settings_update_channel_desc),

        SettingSearchEntry(R.string.settings_log_history_title, log, R.string.settings_log_history_desc),
        SettingSearchEntry(R.string.settings_log_error_title, log, R.string.settings_log_error_desc),
        SettingSearchEntry(R.string.settings_log_export_title, log, R.string.settings_log_export_desc),
        SettingSearchEntry(R.string.settings_debug_mode_title, log, R.string.settings_debug_mode_desc),

        SettingSearchEntry(R.string.settings_language_title, display, keywordsRes = R.string.search_keywords_language),
        SettingSearchEntry(R.string.settings_theme_title, display, keywordsRes = R.string.search_keywords_theme),
        SettingSearchEntry(
            R.string.settings_monet_color_title, display,
            keywordsRes = R.string.search_keywords_theme,
            anchorRes = R.string.settings_theme_title,
        ),
        SettingSearchEntry(
            R.string.settings_font_size_title, display,
            keywordsRes = R.string.search_keywords_font_size,
            anchorRes = R.string.settings_theme_title,
        ),
        SettingSearchEntry(R.string.settings_background_title, display, R.string.settings_background_desc),

        SettingSearchEntry(R.string.settings_startup_backend_title, runtime, keywordsRes = R.string.search_keywords_backend),
        SettingSearchEntry(R.string.settings_core_data_location_title, runtime, keywordsRes = R.string.search_keywords_data_location),
        SettingSearchEntry(R.string.settings_core_data_clear_title, runtime),
        SettingSearchEntry(R.string.settings_skip_shizuku_check, runtime),

        SettingSearchEntry(
            R.string.settings_virtual_display_backend_title,
            backgroundRun,
            R.string.settings_virtual_display_backend_desc,
            minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        ),
        SettingSearchEntry(R.string.settings_background_resolution_title, backgroundRun, keywordsRes = R.string.search_keywords_resolution),
        SettingSearchEntry(
            R.string.settings_force_fullscreen_on_virtual_display, backgroundRun,
            R.string.settings_force_fullscreen_on_virtual_display_desc,
        ),
        SettingSearchEntry(R.string.settings_pip_on_home, backgroundRun, keywordsRes = R.string.search_keywords_pip),

        SettingSearchEntry(R.string.settings_telemetry, thirdParty, keywordsRes = R.string.search_keywords_telemetry),
        SettingSearchEntry(R.string.settings_report_penguin, thirdParty, R.string.settings_report_penguin_desc),
        SettingSearchEntry(R.string.settings_report_yituliu, thirdParty, R.string.settings_report_yituliu_desc),
        SettingSearchEntry(R.string.settings_oper_box_yituliu_title, thirdParty, R.string.settings_oper_box_yituliu_desc),
        SettingSearchEntry(R.string.panel_autobattle_operbox_assist, thirdParty, R.string.settings_oper_box_assist_desc),

        SettingSearchEntry(R.string.settings_deploy_with_pause, task, R.string.settings_deploy_with_pause_desc),
        SettingSearchEntry(R.string.settings_tasks_override_title, task, R.string.settings_tasks_override_desc),

        SettingSearchEntry(R.string.settings_export_config_title, data, R.string.settings_export_config_desc),
        SettingSearchEntry(R.string.settings_import_config_title, data, R.string.settings_import_config_desc),

        SettingSearchEntry(R.string.settings_notification_title, notification, R.string.settings_notification_desc),

        SettingSearchEntry(R.string.settings_achievement_title, achievement, R.string.settings_achievement_desc),
        SettingSearchEntry(
            R.string.settings_achievement_snackbar_title, achievement,
            R.string.settings_achievement_snackbar_desc,
        ),

        SettingSearchEntry(R.string.settings_about_faq_title, about, R.string.settings_about_faq_desc),
        SettingSearchEntry(R.string.settings_about_feedback_title, about),

        SettingSearchEntry(
            R.string.settings_wake_unlock_type, wakeUnlockPage,
            R.string.schedule_wake_unlock_desc, R.string.search_keywords_wake_unlock,
        ),
        SettingSearchEntry(
            R.string.schedule_app_blacklist_title, appBlacklistPage,
            R.string.schedule_app_blacklist_desc, R.string.search_keywords_app_blacklist,
        ),

        SettingSearchEntry(R.string.bg_auto_mute_on_launch, SettingLocation.BackgroundActions, keywordsRes = R.string.search_keywords_mute),
        SettingSearchEntry(R.string.bg_auto_close_on_end, SettingLocation.BackgroundActions),
        SettingSearchEntry(R.string.bg_auto_run_duration_limit, SettingLocation.BackgroundActions, keywordsRes = R.string.search_keywords_run_duration),
        SettingSearchEntry(R.string.bg_auto_hardware_screen_off, SettingLocation.BackgroundActions, keywordsRes = R.string.search_keywords_screen_off),
        SettingSearchEntry(R.string.bg_auto_show_touch_preview, SettingLocation.BackgroundActions),
    )
}

data class SearchableSetting(
    val entry: SettingSearchEntry,
    val title: String,
    val description: String,
    val keywords: String,
    val path: String,
) {
    internal val titleLower = title.lowercase()
    internal val haystack = listOf(title, description, keywords, path).joinToString("\n").lowercase()
}

object SettingSearchMatcher {

    /** 空格分词，每个词都要命中；标题直接命中的排前面，其余保持清单顺序 */
    fun filter(items: List<SearchableSetting>, query: String): List<SearchableSetting> {
        val tokens = query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return items
            .filter { item -> tokens.all { it in item.haystack } }
            .sortedBy { item -> if (tokens.all { it in item.titleLower }) 0 else 1 }
    }

    private val WHITESPACE = Regex("\\s+")
}

data class SettingSearchRequest(
    val entry: SettingSearchEntry,
    val requestedAtMs: Long,
) {
    /** 锚点迟迟未出现就作废，免得过后突然闪 */
    fun isFresh(nowMs: Long = SystemClock.uptimeMillis()): Boolean =
        nowMs - requestedAtMs <= TTL_MS

    private companion object {
        const val TTL_MS = 5_000L
    }
}

/** 跨页定位：目标页展开滚动，锚点高亮并消费 */
class SettingSearchNavigator {

    private val _pending = MutableStateFlow<SettingSearchRequest?>(null)
    val pending: StateFlow<SettingSearchRequest?> = _pending.asStateFlow()

    fun request(entry: SettingSearchEntry) {
        _pending.value = SettingSearchRequest(entry, SystemClock.uptimeMillis())
    }

    fun consume(request: SettingSearchRequest) {
        _pending.compareAndSet(request, null)
    }
}
