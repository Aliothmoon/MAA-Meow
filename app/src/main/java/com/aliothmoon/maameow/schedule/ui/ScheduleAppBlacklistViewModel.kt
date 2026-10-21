package com.aliothmoon.maameow.schedule.ui

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.utils.LauncherApps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

// UI 和位图加载共用同一逻辑尺寸；实际像素数随设备密度变化，不固定为某个 px 值。
internal val ScheduleAppBlacklistIconSize = 40.dp

class ScheduleAppBlacklistViewModel(
    private val appSettings: AppSettingsManager,
    private val appContext: Context,
) : ViewModel() {

    data class AppRow(
        val label: String,
        val packageName: String,
        val checked: Boolean,
        val icon: ImageBitmap? = null,
    )

    sealed interface UiState {
        data object Loading : UiState
        data object Failed : UiState
        data class Loaded(val rows: List<AppRow>, val checkedCount: Int) : UiState
    }

    private sealed interface Load {
        data object Pending : Load
        data object Failed : Load

        /** 进页时排好序，勾选不挪位置。图标随快照复用，重组时不再读取。 */
        data class Ready(
            val apps: List<LauncherApps.App>,
            val pinned: Set<String>,
            val icons: Map<String, ImageBitmap>,
        ) : Load
    }

    private val load = MutableStateFlow<Load>(Load.Pending)

    val runMode: StateFlow<RunMode> = appSettings.runMode

    val state: StateFlow<UiState> = combine(load, appSettings.scheduleAppBlacklist) { load, blacklist ->
        when (load) {
            Load.Pending -> UiState.Loading
            Load.Failed -> UiState.Failed
            is Load.Ready -> UiState.Loaded(buildRows(load, blacklist), blacklist.size)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ready = loadApps()
                // 先显示应用条目和占位图标，图标读取较慢时仍可搜索、勾选。
                load.value = ready
                loadIcons(ready)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Load launcher apps or icons failed")
                // 列表已可用时，后续图标加载异常只保留占位显示。
                if (load.value !is Load.Ready) load.value = Load.Failed
            }
        }
    }

    fun setBlacklisted(packageName: String, blacklisted: Boolean) {
        viewModelScope.launch { appSettings.setScheduleAppBlacklisted(packageName, blacklisted) }
    }

    private fun loadApps(): Load.Ready {
        val pinned = appSettings.scheduleAppBlacklist.value
        val apps = LauncherApps.load(appContext)
            .filter { it.packageName != appContext.packageName }
            .sortedByDescending { it.packageName in pinned }
        return Load.Ready(apps, pinned, emptyMap())
    }

    private suspend fun loadIcons(ready: Load.Ready) {
        val density = appContext.resources.displayMetrics.density
        val iconSizePx = (ScheduleAppBlacklistIconSize.value * density).roundToInt().coerceAtLeast(1)
        var icons = ready.icons
        for (app in ready.apps) {
            // 系统图标读取本身不可挂起，但页面销毁后不再继续处理后续应用。
            currentCoroutineContext().ensureActive()
            val icon = loadIcon(app.packageName, iconSizePx) ?: continue
            // 发布新快照，避免修改 Compose 已在使用的图标缓存；勾选仍取最新设置。
            icons = icons + (app.packageName to icon)
            load.value = ready.copy(icons = icons)
        }
    }

    private fun loadIcon(packageName: String, sizePx: Int): ImageBitmap? = try {
        val drawable = appContext.packageManager.getApplicationIcon(packageName)
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        // 等比缩放到显示区域所需像素；兼容自适应/矢量图标，也避免拉伸非正方形图标。
        val scale = if (width > 0 && height > 0) sizePx.toFloat() / maxOf(width, height) else 1f
        val targetWidth = if (width > 0 && height > 0) (width * scale).roundToInt() else sizePx
        val targetHeight = if (width > 0 && height > 0) (height * scale).roundToInt() else sizePx
        drawable.toBitmap(targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1)).asImageBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 查询列表后应用可能刚被卸载，图标失败不应使整个列表不可用。
        Timber.w(e, "Load app icon failed: %s", packageName)
        null
    }

    private fun buildRows(ready: Load.Ready, blacklist: Set<String>): List<AppRow> {
        val installed = ready.apps.mapTo(HashSet()) { it.packageName }
        // 已卸载的还留在名单里，给个入口删掉
        val missing = (ready.pinned + blacklist)
            .filter { it !in installed }
            .map { AppRow(it, it, it in blacklist) }
        return missing + ready.apps.map {
            AppRow(it.label, it.packageName, it.packageName in blacklist, ready.icons[it.packageName])
        }
    }
}
