package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.config.GamePackageSettings
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Owns game resource selection and guarded saves, independently of Task Override. */
@OptIn(FlowPreview::class)
class GamePackageEditorViewModel(
    private val editor: GamePackageSettings,
    private val compositionService: MaaCompositionService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    sealed class SaveState {
        data object Idle : SaveState()
        data object Saving : SaveState()
        data object Success : SaveState()
        data class Error(val text: UiText) : SaveState()
    }

    private val _editorText = MutableStateFlow("")
    private val _loadedText = MutableStateFlow<String?>(null)
    val loadedText = _loadedText.asStateFlow()
    private var readJob: Job? = null

    val canEdit = combine(compositionService.state, editor.enabled) { state, enabled ->
        enabled == true && state in setOf(MaaExecutionState.IDLE, MaaExecutionState.ERROR)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), isIdle() && editor.enabled.value == true)
    private val _defaultPackage = MutableStateFlow("")
    val defaultPackage = _defaultPackage.asStateFlow()
    val isJsonValid: StateFlow<Boolean> = _editorText.debounce(300L)
        .map { editor.isValid(it) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    private fun isIdle() = compositionService.state.value in setOf(MaaExecutionState.IDLE, MaaExecutionState.ERROR)

    fun load() {
        if (!isIdle()) {
            _saveState.value = SaveState.Error(uiTextOf(R.string.resource_editor_busy))
            return
        }
        readJob?.cancel()
        _loadedText.value = null
        readJob = viewModelScope.launch {
            try {
                val (text, defaultPackage) = withContext(ioDispatcher) {
                    editor.refresh()
                    check(editor.enabled.value == true) { "Custom packages are disabled" }
                    editor.read() to editor.defaultPackageName()
                }
                _defaultPackage.value = defaultPackage
                _editorText.value = text
                _loadedText.value = text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Read resource editor failed")
                _saveState.value = SaveState.Error(uiTextOf(R.string.resource_editor_load_failed))
            }
        }
    }

    fun onTextChange(text: String) {
        _editorText.value = text
        if (_saveState.value is SaveState.Success) _saveState.value = SaveState.Idle
    }

    fun onSave() {
        if (_saveState.value is SaveState.Saving) return
        if (_loadedText.value == null) return
        val content = _editorText.value
        if (!editor.isValid(content)) {
            _saveState.value = SaveState.Error(uiTextOf(R.string.game_package_invalid_json))
            return
        }
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            runCatching {
                check(compositionService.updateResourceConfig { editor.save(content) }) { "Task is active" }
            }.fold(
                onSuccess = {
                    _loadedText.value = content
                    _saveState.value = SaveState.Success
                    Timber.i("Package config saved")
                },
                onFailure = {
                    Timber.w(it, "Save package config failed")
                    _saveState.value = SaveState.Error(uiTextOf(
                        if (!isIdle()) R.string.resource_editor_busy else R.string.task_override_error_save_failed
                    ))
                }
            )
        }
    }

    fun onReset() {
        if (_saveState.value is SaveState.Saving || _loadedText.value == null) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            runCatching {
                check(compositionService.updateResourceConfig { editor.reset() }) { "Task is active" }
            }.fold(
                onSuccess = { load(); _saveState.value = SaveState.Success },
                onFailure = { _saveState.value = SaveState.Error(uiTextOf(R.string.task_override_error_save_failed)) },
            )
        }
    }

    fun clearSaveState() { _saveState.value = SaveState.Idle }
}
