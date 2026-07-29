package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.achievement.AchievementDefinitions
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementField
import com.aliothmoon.maameow.data.achievement.AchievementIds
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.achievement.AchievementState
import com.aliothmoon.maameow.data.achievement.PallasClickResult
import com.aliothmoon.maameow.data.achievement.PallasDebugEasterEgg
import com.aliothmoon.maameow.data.achievement.achievementText
import com.aliothmoon.maameow.data.achievement.buildAchievementStates

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 成就页 UI 状态:打包屏幕所需全部可变数据,作为单一真实数据源。 */
data class AchievementUiState(
    val searchQuery: String = "",
    val totalCount: Int = 0,
    val unlockedCount: Int = 0,
    val achievements: List<AchievementState> = emptyList(),
    /** 全部可见成就(含未解锁),供调试页下拉选择等需要完整列表的场景使用。 */
    val allAchievements: List<AchievementState> = emptyList(),
    /** 帕拉斯头像彩蛋：会话内 Debug 态（金色 + Unlock All）。 */
    val pallasDebugActive: Boolean = false,
)

class AchievementViewModel(
    private val repository: AchievementRepository,
    private val application: Context,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val pallasEgg = PallasDebugEasterEgg()
    private val _pallasDebugActive = MutableStateFlow(false)

    val uiState: StateFlow<AchievementUiState> = combine(
        repository.records,
        _query,
        _pallasDebugActive,
    ) { records, query, pallasDebug ->
        val all = buildAchievementStates(records, AchievementDefinitions.all)
        val normalized = query.trim()
        val visible = if (normalized.isEmpty()) {
            all.filter { it.unlocked }
        } else {
            val ctx = ContextCompat.getContextForLanguage(application)
            all.filter { state ->
                state.unlocked && ctx.achievementText(state.definition.id, AchievementField.TITLE)
                    .contains(normalized, ignoreCase = true)
            }
        }
        AchievementUiState(
            searchQuery = query,
            totalCount = all.size,
            unlockedCount = all.count { it.unlocked },
            achievements = visible,
            allAchievements = all,
            pallasDebugActive = pallasDebug,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementUiState())

    private val _effects = Channel<AchievementEffect>(Channel.BUFFERED)

    /** 一次性副作用流(Toast 等),配置变更不丢失。 */
    val effects = _effects.receiveAsFlow()

    fun onEvent(input: AchievementEvent) {
        when (input) {
            is AchievementEvent.UpdateSearchText -> _query.update { input.text }
            is AchievementEvent.Unlock -> viewModelScope.launch {
                repository.unlock(input.id)
                _effects.send(AchievementEffect.Unlocked)
            }

            AchievementEvent.UnlockAll -> viewModelScope.launch {
                repository.unlockAll()
                _effects.send(AchievementEffect.UnlockedAll)
            }

            AchievementEvent.ClearAllRecords -> viewModelScope.launch {
                repository.clearAllRecords()
                _effects.send(AchievementEffect.Cleared)
            }

            AchievementEvent.ScreenOpened -> viewModelScope.launch {
                repository.report {
                    event = AchievementEvents.ACHIEVEMENT_PAGE_OPENED
                }
            }

            AchievementEvent.PallasAvatarClicked -> viewModelScope.launch {
                when (pallasEgg.onClick()) {
                    PallasClickResult.Ignored -> return@launch
                    PallasClickResult.EnteredDebug -> {
                        _pallasDebugActive.value = true
                        _effects.send(AchievementEffect.PallasEnteredDebug)
                    }

                    PallasClickResult.ExitedDebug -> {
                        _pallasDebugActive.value = false
                        _effects.send(AchievementEffect.PallasExitedDebug)
                    }

                    is PallasClickResult.Counting, PallasClickResult.MissedRoll -> Unit
                }
                repository.unlock(AchievementIds.PALLAS_CHEERS)
            }
        }
    }
}

/** 成就页用户意图,所有 UI 事件经此入口上抛。 */
sealed interface AchievementEvent {
    data class UpdateSearchText(val text: String) : AchievementEvent
    data class Unlock(val id: String) : AchievementEvent
    data object UnlockAll : AchievementEvent
    data object ClearAllRecords : AchievementEvent
    data object ScreenOpened : AchievementEvent

    /** 设置成就区帕拉斯头像点击。 */
    data object PallasAvatarClicked : AchievementEvent
}

/** 一次性 UI 副作用,由 ViewModel 发出、View 消费。 */
sealed interface AchievementEffect {
    data object Unlocked : AchievementEffect
    data object UnlockedAll : AchievementEffect
    data object Cleared : AchievementEffect
    data object PallasEnteredDebug : AchievementEffect
    data object PallasExitedDebug : AchievementEffect
}
