package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.log.ApplicationLogWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * 错误日志 ViewModel
 */
class ErrorLogViewModel(
    private val applicationLogWriter: ApplicationLogWriter,
) : ViewModel() {

    data class ErrorLogFile(
        val file: File,
        val name: String,
        val size: Long,
        val lastModified: Long
    )

    private val _logFiles = MutableStateFlow<List<ErrorLogFile>>(emptyList())
    val logFiles: StateFlow<List<ErrorLogFile>> = _logFiles.asStateFlow()

    private val _selectedContent = MutableStateFlow<String?>(null)
    val selectedContent: StateFlow<String?> = _selectedContent.asStateFlow()

    private val _selectedFileName = MutableStateFlow<String?>(null)
    val selectedFileName: StateFlow<String?> = _selectedFileName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadLogFiles()
    }

    fun loadLogFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val files = withContext(Dispatchers.IO) {
                    applicationLogWriter.getLogFiles().map { file ->
                        ErrorLogFile(
                            file = file,
                            name = file.name,
                            size = file.length(),
                            lastModified = file.lastModified()
                        )
                    }
                }
                _logFiles.value = files
            } catch (e: Exception) {
                Timber.e(e, "Failed to load error log files")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadLogContent(errorLogFile: ErrorLogFile) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val content = withContext(Dispatchers.IO) {
                    errorLogFile.file.readText()
                }
                _selectedContent.value = content
                _selectedFileName.value = errorLogFile.name
            } catch (e: Exception) {
                Timber.e(e, "Failed to read error log file")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSelectedLog() {
        _selectedContent.value = null
        _selectedFileName.value = null
    }

    fun cleanupAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                applicationLogWriter.cleanupAll()
            }
            loadLogFiles()
        }
    }
}
