package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.fastjson2.JSON
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextDynamicOr
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class TaskOverrideEditorViewModel(
    private val pathConfig: MaaPathConfig,
    private val resourceLoader: MaaResourceLoader,
) : ViewModel() {

    sealed class SaveState {
        data object Idle : SaveState()
        data object Saving : SaveState()
        data object Success : SaveState()
        data class Error(val text: UiText) : SaveState()
    }

    private val _editorText = MutableStateFlow("{}")
    val editorText: StateFlow<String> = _editorText.asStateFlow()

    val isJsonValid: StateFlow<Boolean> = _editorText
        .map { JSON.isValid(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val text = runCatching {
                pathConfig.overrideTasksFile.readText()
            }.getOrElse {
                Timber.w(it, "读取覆盖文件失败，使用默认值")
                "{}"
            }
            _editorText.value = text
        }
    }

    fun onTextChange(text: String) {
        _editorText.value = text
        if (_saveState.value is SaveState.Success) {
            _saveState.value = SaveState.Idle
        }
    }

    fun onSave() {
        val content = _editorText.value
        if (!JSON.isValid(content)) {
            _saveState.value = SaveState.Error(uiTextOf(R.string.task_override_error_json_invalid))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.value = SaveState.Saving
            runCatching {
                val dest = pathConfig.overrideTasksFile
                dest.parentFile?.mkdirs()
                dest.writeText(content)
                resourceLoader.invalidate()
                Timber.i("覆盖文件已保存: ${dest.absolutePath}")
            }.fold(
                onSuccess = { _saveState.value = SaveState.Success },
                onFailure = {
                    Timber.e(it, "覆盖文件保存失败")
                    _saveState.value = SaveState.Error(
                        uiTextDynamicOr(it.message, R.string.task_override_error_save_failed)
                    )
                }
            )
        }
    }

    fun clearSaveState() {
        _saveState.value = SaveState.Idle
    }
}
