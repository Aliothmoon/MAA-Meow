package com.aliothmoon.maameow.presentation.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.ConfigBackupManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.preferences.UnlockGestureStore
import com.aliothmoon.maameow.data.resource.BackgroundImageStore
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.models.GestureRecordResult
import com.aliothmoon.maameow.domain.models.GestureRecordStatus
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.domain.models.UnlockCredential
import com.aliothmoon.maameow.domain.models.withoutDelay
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.WakeUnlockEngine
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.toLocaleList
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream

class SettingsViewModel(
    private val app: Application,
    private val appSettingsManager: AppSettingsManager,
    private val permissionManager: PermissionManager,
    private val configBackupManager: ConfigBackupManager,
    private val taskChainState: TaskChainState,
    private val resourceDataManager: ResourceDataManager,
    private val resourceLoader: MaaResourceLoader,
    private val achievementReporter: AchievementReporter,
    private val backgroundImageStore: BackgroundImageStore,
    private val wakeUnlockEngine: WakeUnlockEngine,
    private val unlockGestureStore: UnlockGestureStore,
) : ViewModel() {

    // ========== 导入导出 ==========

    private val _backupMessage = MutableStateFlow<UiText?>(null)
    val backupMessage: StateFlow<UiText?> = _backupMessage.asStateFlow()

    private val _showRestartDialog = MutableStateFlow(false)
    val showRestartDialog: StateFlow<Boolean> = _showRestartDialog.asStateFlow()

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    fun dismissRestartDialog() {
        _showRestartDialog.value = false
    }

    fun confirmRestart() {
        _showRestartDialog.value = false
        Misc.restartApp(app)
    }

    fun exportConfig(outputStream: OutputStream) {
        viewModelScope.launch {
            try {
                configBackupManager.exportTo(outputStream)
                _backupMessage.value = uiTextOf(R.string.settings_export_success)
            } catch (e: Exception) {
                Timber.e(e, "export config failed")
                _backupMessage.value =
                    uiTextOf(R.string.settings_export_failed, e.message.orEmpty())
            }
        }
    }

    fun importConfig(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                configBackupManager.importFrom(inputStream)
                _showRestartDialog.value = true
            } catch (e: Exception) {
                Timber.e(e, "import config failed")
                _backupMessage.value =
                    uiTextOf(R.string.settings_import_failed, e.message.orEmpty())
            }
        }
    }

    // ========== 现有设置 ==========

    val debugMode: StateFlow<Boolean> = appSettingsManager.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setDebugMode(enabled)
            achievementReporter.reportDebugModeChanged(enabled)
            val state = RemoteServiceManager.state.value
            if (state is RemoteServiceManager.ServiceState.Connected) {
                RemoteServiceManager.unbind()
            }
            if (enabled) {
                Misc.restartApp(app)
            }
        }
    }

    val autoCheckUpdate: StateFlow<Boolean> = appSettingsManager.autoCheckUpdate
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            !BuildConfig.DEBUG
        )

    fun setAutoCheckUpdate(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setAutoCheckUpdate(enabled)
        }
    }

    val autoDownloadUpdate: StateFlow<Boolean> = appSettingsManager.autoDownloadUpdate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAutoDownloadUpdate(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setAutoDownloadUpdate(enabled)
        }
    }

    val startupBackend: StateFlow<RemoteBackend> = appSettingsManager.startupBackend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemoteBackend.SHIZUKU)

    fun setStartupBackend(backend: RemoteBackend) {
        viewModelScope.launch {
            permissionManager.setStartupBackend(backend)
        }
    }

    val skipShizukuCheck: StateFlow<Boolean> = appSettingsManager.skipShizukuCheck
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSkipShizukuCheck(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setSkipShizukuCheck(enabled)
        }
    }

    val shizukuLaunchPackage: StateFlow<String> = appSettingsManager.shizukuLaunchPackage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OFFICIAL_SHIZUKU_PACKAGE)

    val shizukuShortcutEnabled: StateFlow<Boolean> = appSettingsManager.shizukuShortcutEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setShizukuShortcutEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setShizukuShortcutEnabled(enabled)
        }
    }

    fun setShizukuLaunchPackage(packageName: String) {
        viewModelScope.launch {
            appSettingsManager.setShizukuLaunchPackage(packageName)
        }
    }

    val deploymentWithPause: StateFlow<Boolean> = appSettingsManager.deploymentWithPause
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDeploymentWithPause(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setDeploymentWithPause(enabled)
        }
    }

    val reportToPenguin: StateFlow<Boolean> = appSettingsManager.reportToPenguin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setReportToPenguin(enabled: Boolean) {
        viewModelScope.launch { appSettingsManager.setReportToPenguin(enabled) }
    }

    val reportToYituliu: StateFlow<Boolean> = appSettingsManager.reportToYituliu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setReportToYituliu(enabled: Boolean) {
        viewModelScope.launch { appSettingsManager.setReportToYituliu(enabled) }
    }

    val penguinId: StateFlow<String> = appSettingsManager.penguinId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setPenguinId(id: String) {
        viewModelScope.launch { appSettingsManager.setPenguinId(id) }
    }

    val forceFullscreenOnVirtualDisplay: StateFlow<Boolean> =
        appSettingsManager.forceFullscreenOnVirtualDisplay
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setForceFullscreenOnVirtualDisplay(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setForceFullscreenOnVirtualDisplay(enabled)
        }
    }

    // ───────────────── 定时唤醒解锁 ─────────────────

    val wakeUnlockType: StateFlow<String> =
        appSettingsManager.wakeUnlockType
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "swipe")

    fun setWakeUnlockType(type: String) {
        viewModelScope.launch { appSettingsManager.setWakeUnlockType(type) }
    }

    val wakeCredential: StateFlow<String> =
        appSettingsManager.wakeCredential
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setWakeCredential(credential: String) {
        viewModelScope.launch { appSettingsManager.setWakeCredential(credential) }
    }

    /** null=未测试，Testing=进行中，Done=已出结果 */
    sealed interface WakeTestState {
        data object Testing : WakeTestState
        data class Done(val result: WakeUnlockEngine.WakeResult) : WakeTestState
    }

    private val _wakeTestState = MutableStateFlow<WakeTestState?>(null)
    val wakeTestState: StateFlow<WakeTestState?> = _wakeTestState.asStateFlow()

    fun runWakeTest() {
        if (_wakeTestState.value == WakeTestState.Testing) return
        viewModelScope.launch {
            _wakeTestState.value = WakeTestState.Testing
            val type = appSettingsManager.wakeUnlockType.value
            val credential = UnlockCredential.of(
                type = type,
                pin = appSettingsManager.wakeCredential.value,
                gestureJson = if (type == UnlockCredential.TYPE_GESTURE) {
                    unlockGestureStore.readJson()
                } else {
                    ""
                },
            )
            _wakeTestState.value = WakeTestState.Done(wakeUnlockEngine.testUnlock(credential))
        }
    }

    fun clearWakeTestResult() {
        _wakeTestState.value = null
    }

    // ───────────────── 解锁手势录制 ─────────────────

    val unlockGesture: StateFlow<UnlockGesture?> = unlockGestureStore.gesture

    /** null=空闲；录制期间 App 在锁屏后面，只能靠轮询回收结果 */
    sealed interface GestureRecordState {
        /** 已发起，等提权进程锁屏 */
        data object Preparing : GestureRecordState
        data object Recording : GestureRecordState
        data class Done(val steps: Int) : GestureRecordState
        data class Failed(val result: WakeUnlockEngine.WakeResult) : GestureRecordState
    }

    private val _gestureRecordState = MutableStateFlow<GestureRecordState?>(null)
    val gestureRecordState: StateFlow<GestureRecordState?> = _gestureRecordState.asStateFlow()

    private var recordJob: Job? = null

    fun startGestureRecord() {
        if (recordJob?.isActive == true) return
        recordJob = viewModelScope.launch {
            _gestureRecordState.value = GestureRecordState.Preparing
            if (!wakeUnlockEngine.startGestureRecord(RECORD_TIMEOUT_MS)) {
                _gestureRecordState.value =
                    GestureRecordState.Failed(WakeUnlockEngine.WakeResult.IPC_FAILED)
                return@launch
            }
            _gestureRecordState.value = GestureRecordState.Recording
            awaitRecordResult()
        }
    }

    /** 进设置页时补一次：VM 若在锁屏期间被重建，轮询协程会一起没掉 */
    fun refreshGestureRecord() {
        if (recordJob?.isActive == true) return
        recordJob = viewModelScope.launch {
            val result = wakeUnlockEngine.pollGestureRecord() ?: return@launch
            if (result.status.isTerminal) {
                consume(result)
            } else if (result.status == GestureRecordStatus.RECORDING) {
                _gestureRecordState.value = GestureRecordState.Recording
                awaitRecordResult()
            }
        }
    }

    fun cancelGestureRecord() {
        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            wakeUnlockEngine.cancelGestureRecord()
            // 远端取消后不会留下终态，界面直接收掉，别让用户以为按钮没反应
            _gestureRecordState.value =
                GestureRecordState.Failed(WakeUnlockEngine.WakeResult.RECORD_CANCELLED)
        }
    }

    fun clearGestureRecordState() {
        _gestureRecordState.value = null
    }

    fun clearGesture() {
        viewModelScope.launch { unlockGestureStore.clear() }
    }

    fun deleteGestureStep(index: Int) {
        val gesture = unlockGestureStore.gesture.value ?: return
        if (index !in gesture.steps.indices) return
        viewModelScope.launch {
            val steps = gesture.steps.toMutableList().apply { removeAt(index) }
            if (steps.isEmpty()) {
                unlockGestureStore.clear()
                return@launch
            }
            // 首步不该带间隔，否则回放一开始就白等
            steps[0] = steps[0].withoutDelay()
            unlockGestureStore.save(gesture.copy(steps = steps))
        }
    }

    private suspend fun awaitRecordResult() {
        val deadline = SystemClock.elapsedRealtime() + RECORD_TIMEOUT_MS + RECORD_GRACE_MS
        // 先轮询再判超时：锁屏期间进程可能被冻结，解冻后这一轮仍要能把结果取回来
        while (true) {
            delay(RECORD_POLL_INTERVAL_MS)
            val result = wakeUnlockEngine.pollGestureRecord()
            // IDLE：oneway 的 start 还没落地，或远端重启过，继续等而不是当成已结束
            if (result != null && result.status.isTerminal) {
                consume(result)
                return
            }
            if (SystemClock.elapsedRealtime() >= deadline) break
        }
        _gestureRecordState.value =
            GestureRecordState.Failed(WakeUnlockEngine.WakeResult.RECORD_TIMEOUT)
    }

    private suspend fun consume(result: GestureRecordResult) {
        val gesture = result.gesture
        _gestureRecordState.value = if (
            result.status == GestureRecordStatus.DONE && gesture != null
        ) {
            unlockGestureStore.save(gesture)
            GestureRecordState.Done(gesture.steps.size)
        } else {
            GestureRecordState.Failed(WakeUnlockEngine.WakeResult.fromCode(result.errorCode))
        }
    }

    val updateChannel: StateFlow<UpdateChannel> = appSettingsManager.updateChannel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdateChannel.STABLE)

    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            appSettingsManager.setUpdateChannel(channel)
        }
    }

    val themeMode: StateFlow<AppSettingsManager.ThemeMode> = appSettingsManager.themeMode
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppSettingsManager.ThemeMode.WHITE
        )

    fun setThemeMode(mode: AppSettingsManager.ThemeMode) {
        viewModelScope.launch {
            appSettingsManager.setThemeMode(mode)
        }
    }

    val backgroundResolution: StateFlow<DefaultDisplayConfig.ResolutionPreference> =
        appSettingsManager.backgroundResolution
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                DefaultDisplayConfig.ResolutionPreference.P720
            )

    fun setBackgroundResolution(pref: DefaultDisplayConfig.ResolutionPreference) {
        viewModelScope.launch {
            appSettingsManager.setBackgroundResolution(pref)
        }
    }

    val language: StateFlow<AppSettingsManager.AppLanguage> = appSettingsManager.language
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppSettingsManager.AppLanguage.SYSTEM
        )

    fun setLanguage(lang: AppSettingsManager.AppLanguage) {
        viewModelScope.launch {
            val resolved = resolveSelectedLanguage(lang)
            appSettingsManager.setLanguage(resolved)
            AppCompatDelegate.setApplicationLocales(resolved.toLocaleList())
            resourceDataManager.refreshDisplayLanguage(
                clientType = taskChainState.clientType,
                displayLanguage = ResourceDataManager.displayLanguageCode(resolved)
            )
        }
    }

    // Android 特化任务覆盖
    val tasksOverrideEnabled: StateFlow<Boolean> = appSettingsManager.tasksOverrideEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setTasksOverrideEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setTasksOverrideEnabled(enabled)
            // 开关变更后重置加载状态，下次任务启动时按最新配置重新加载
            resourceLoader.reset()
        }
    }

    // ============ System Monet theme color ============
    val useSystemMonetColor: StateFlow<Boolean> = appSettingsManager.useSystemMonetColor
    fun setUseSystemMonetColor(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setUseSystemMonetColor(enabled)
        }
    }

    // ============ Font Size Scale ============
    val fontSizeScale: StateFlow<Int> = appSettingsManager.fontSizeScale
    fun setFontSizeScale(scale: Int) {
        viewModelScope.launch {
            appSettingsManager.setFontSizeScale(scale)
        }
    }

    // 成就 Snackbar 提示开关
    val showAchievementSnackbar: StateFlow<Boolean> = appSettingsManager.showAchievementSnackbar
    fun setShowAchievementSnackbar(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setShowAchievementSnackbar(enabled)
        }
    }

    // ============ 自定义图片背景 ============
    val customBackgroundEnabled: StateFlow<Boolean> = appSettingsManager.customBackgroundEnabled
    val customBackgroundImageAlpha: StateFlow<Int> = appSettingsManager.customBackgroundImageAlpha
    val customBackgroundScrim: StateFlow<Int> = appSettingsManager.customBackgroundScrim
    val customBackgroundBlur: StateFlow<Int> = appSettingsManager.customBackgroundBlur
    val backgroundImage: StateFlow<ImageBitmap?> = backgroundImageStore.imageBitmap

    fun setCustomBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setCustomBackgroundEnabled(enabled)
        }
    }

    /** 把选中的图片复制到缓存目录，返回文件路径；失败返回 null。 */
    suspend fun prepareBackgroundSource(uri: Uri): String? =
        backgroundImageStore.prepareSource(uri)

    /** 按 EXIF 方向解码裁剪源图片；失败返回 null。 */
    suspend fun decodeBackgroundSource(path: String): Bitmap? =
        backgroundImageStore.decodeSource(path)

    /** 保存裁剪结果并启用背景；返回是否成功。 */
    suspend fun saveCroppedBackground(bitmap: Bitmap): Boolean =
        backgroundImageStore.saveCropped(bitmap)

    /** 取消裁剪或保存完成后清理源图片缓存。 */
    fun discardBackgroundSource() {
        backgroundImageStore.clearSourceCache()
    }

    fun removeBackgroundImage() {
        viewModelScope.launch {
            backgroundImageStore.clear()
        }
    }

    fun setCustomBackgroundImageAlpha(value: Int) {
        viewModelScope.launch {
            appSettingsManager.setCustomBackgroundImageAlpha(value)
        }
    }

    fun setCustomBackgroundScrim(value: Int) {
        viewModelScope.launch {
            appSettingsManager.setCustomBackgroundScrim(value)
        }
    }

    fun setCustomBackgroundBlur(value: Int) {
        viewModelScope.launch {
            appSettingsManager.setCustomBackgroundBlur(value)
        }
    }

    private companion object {
        /** 与提权进程的录制超时对齐，留一段宽限防止两边同时判超时 */
        const val RECORD_TIMEOUT_MS = 90_000
        const val RECORD_GRACE_MS = 15_000
        const val RECORD_POLL_INTERVAL_MS = 1_000L
    }
}
