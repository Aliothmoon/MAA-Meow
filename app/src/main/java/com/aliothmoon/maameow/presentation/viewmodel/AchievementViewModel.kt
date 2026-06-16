package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AchievementViewModel(
    private val repository: AchievementRepository,
    appSettingsManager: AppSettingsManager,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val searchText: StateFlow<String> = query

    val totalCount = repository.achievements
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val unlockedCount = repository.achievements
        .map { achievements -> achievements.count { it.unlocked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val achievements = combine(repository.achievements, query, appSettingsManager.language) { achievements, text, language ->
        val normalized = text.trim()
        val languageTag = resolveSelectedLanguage(language).tag
        achievements.filter { state ->
            state.unlocked && (
                normalized.isEmpty()
                    || state.definition.title.resolve(languageTag).contains(normalized, ignoreCase = true)
                )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateSearchText(text: String) {
        query.update { text }
    }

    fun onScreenOpened() {
        viewModelScope.launch {
            repository.recordEvent(AchievementEvents.AchievementPageOpened)
        }
    }
}
