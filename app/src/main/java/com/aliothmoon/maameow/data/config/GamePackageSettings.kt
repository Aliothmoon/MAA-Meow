package com.aliothmoon.maameow.data.config

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Shared settings state. Call disk operations on IO and mutations under the task startup lock. */
class GamePackageSettings(
    pathConfig: MaaPathConfig,
    private val appSettings: AppSettingsManager,
    private val chainState: TaskChainState,
) {
    private val editor = ResourceConfigEditor(File(pathConfig.rootDir))
    private val _enabled = MutableStateFlow<Boolean?>(null)
    val enabled = _enabled.asStateFlow()
    private val client get() = chainState.clientType.ifEmpty { "Official" }
    private val overrides get() = appSettings.tasksOverrideEnabled.value

    fun refresh() { _enabled.value = editor.isEnabled() }
    fun setEnabled(enabled: Boolean) {
        editor.setEnabled(enabled)
        refresh()
    }
    fun read() = editor.read(ResourceConfigEditor.PACKAGE_FILE, client, overrides)
    fun isValid(content: String) = editor.isValid(content, client)
    fun defaultPackageName() = editor.defaultPackageName(client, overrides)
    fun save(content: String) = editor.save(ResourceConfigEditor.PACKAGE_FILE, content, client)
    fun reset() = editor.reset(client)
}
