package com.aliothmoon.maameow.schedule.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.utils.LauncherApps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ScheduleAppBlacklistViewModel(
    private val appSettings: AppSettingsManager,
    private val appContext: Context,
) : ViewModel() {

    data class AppRow(val label: String, val packageName: String, val checked: Boolean)

    sealed interface UiState {
        data object Loading : UiState
        data object Failed : UiState
        data class Loaded(val rows: List<AppRow>, val checkedCount: Int) : UiState
    }

    private sealed interface Load {
        data object Pending : Load
        data object Failed : Load

        /** 进页时排好序，勾选不挪位置 */
        data class Ready(val apps: List<LauncherApps.App>, val pinned: Set<String>) : Load
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
        viewModelScope.launch {
            load.value = try {
                withContext(Dispatchers.IO) { loadApps() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Load launcher apps failed")
                Load.Failed
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
        return Load.Ready(apps, pinned)
    }

    private fun buildRows(ready: Load.Ready, blacklist: Set<String>): List<AppRow> {
        val installed = ready.apps.mapTo(HashSet()) { it.packageName }
        // 已卸载的还留在名单里，给个入口删掉
        val missing = (ready.pinned + blacklist)
            .filter { it !in installed }
            .map { AppRow(it, it, it in blacklist) }
        return missing + ready.apps.map {
            AppRow(
                it.label,
                it.packageName,
                it.packageName in blacklist
            )
        }
    }
}
