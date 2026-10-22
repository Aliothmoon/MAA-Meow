package com.aliothmoon.maameow.presentation.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.YituliuApiService
import com.aliothmoon.maameow.data.api.message
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.ConfigBackupManager
import com.aliothmoon.maameow.data.resource.BackgroundImageStore
import com.aliothmoon.maameow.domain.models.CoreDataLocation
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.VirtualDisplayBackend
import com.aliothmoon.maameow.domain.notification.LiveUpdatePublisher
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.CopilotOperBoxAssist
import com.aliothmoon.maameow.domain.service.CoreDataPusher
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.state.isIdle
import com.aliothmoon.maameow.domain.usecase.SwitchCoreDataLocationUseCase
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.utils.InputTooLargeException
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream

/** markdown 是 Mirror 酱下发的远端正文，没有资源可以支撑，不套 UiText */
data class ChangelogArchive(
    val version: String,
    val markdown: String,
)

class SettingsViewModel(
    private val app: Application,
    private val appSettingsManager: AppSettingsManager,
    private val permissionManager: PermissionManager,
    private val configBackupManager: ConfigBackupManager,
    private val resourceLoader: MaaResourceLoader,
    private val achievementReporter: AchievementReporter,
    private val backgroundImageStore: BackgroundImageStore,
    private val coreDataPusher: CoreDataPusher,
    private val switchCoreDataLocation: SwitchCoreDataLocationUseCase,
    private val compositionService: MaaCompositionService,
    private val yituliuApiService: YituliuApiService,
    private val livePublisher: LiveUpdatePublisher,
    private val operBoxAssist: CopilotOperBoxAssist,
    private val resourceInitService: ResourceInitService,
) : ViewModel() {

    // ========== 导入导出 ==========

    private val _settingsMessage = MutableStateFlow<UiText?>(null)
    val settingsMessage: StateFlow<UiText?> = _settingsMessage.asStateFlow()

    private val _showRestartDialog = MutableStateFlow(false)
    val showRestartDialog: StateFlow<Boolean> = _showRestartDialog.asStateFlow()

    fun clearSettingsMessage() {
        _settingsMessage.value = null
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
                _settingsMessage.value = uiTextOf(R.string.settings_export_success)
            } catch (e: Exception) {
                Timber.e(e, "export config failed")
                _settingsMessage.value =
                    uiTextOf(R.string.settings_export_failed, e.message.orEmpty())
            }
        }
    }

    fun importConfig(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                configBackupManager.importFrom(inputStream)
                _showRestartDialog.value = true
            } catch (e: InputTooLargeException) {
                Timber.w(e, "import config rejected")
                _settingsMessage.value =
                    uiTextOf(R.string.settings_import_too_large, e.limitBytes / (1024 * 1024))
            } catch (e: Exception) {
                Timber.e(e, "import config failed")
                _settingsMessage.value =
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

    /**
     * 样式页入口：实际生效的展示方式有可配置项才显示
     * capability 含跨进程查询放 IO；重新订阅时会重算，覆盖在系统设置里改权限的情况
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val liveUpdateEntryVisible: StateFlow<Boolean> = appSettingsManager.liveBackendPreference
        .mapLatest {
            withContext(Dispatchers.IO) {
                runCatching { livePublisher.capability.styleConfigurable }.getOrDefault(false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val startupBackend: StateFlow<RemoteBackend> = appSettingsManager.startupBackend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemoteBackend.SHIZUKU)

    fun setStartupBackend(backend: RemoteBackend) {
        viewModelScope.launch {
            permissionManager.setStartupBackend(backend)
        }
    }

    // ========== MaaCore 数据目录 ==========

    val coreDataLocation: StateFlow<CoreDataLocation> = appSettingsManager.coreDataLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), appSettingsManager.coreDataLocation.value)

    private val _pendingCoreDataLocation = MutableStateFlow<CoreDataLocation?>(null)
    val pendingCoreDataLocation: StateFlow<CoreDataLocation?> = _pendingCoreDataLocation.asStateFlow()

    private val _showClearCoreDataDialog = MutableStateFlow(false)
    val showClearCoreDataDialog: StateFlow<Boolean> = _showClearCoreDataDialog.asStateFlow()

    fun requestCoreDataLocationChange(target: CoreDataLocation) {
        if (target == appSettingsManager.coreDataLocation.value) return
        _pendingCoreDataLocation.value = target
    }

    fun dismissCoreDataLocationChange() {
        _pendingCoreDataLocation.value = null
    }

    fun confirmCoreDataLocationChange() {
        val target = _pendingCoreDataLocation.value ?: return
        _pendingCoreDataLocation.value = null
        viewModelScope.launch { switchCoreDataLocation(target) }
    }

    private fun coreBusy(message: Int = R.string.settings_core_data_clear_busy): Boolean {
        if (compositionService.state.value.isIdle) return false
        _settingsMessage.value = uiTextOf(message)
        return true
    }

    fun reInitializeResources() {
        if (coreBusy(R.string.settings_reinit_resource_busy)) return
        viewModelScope.launch { resourceInitService.reInitialize() }
    }

    fun requestClearCoreData() {
        if (coreBusy()) return
        _showClearCoreDataDialog.value = true
    }

    fun dismissClearCoreData() {
        _showClearCoreDataDialog.value = false
    }

    /** 清完断开提权进程，避免 core 继续引用已删的文件；下次连接重解 */
    fun confirmClearCoreData() {
        _showClearCoreDataDialog.value = false
        // 对话框开着期间定时任务可能已启动
        if (coreBusy()) return
        viewModelScope.launch {
            val ok = coreDataPusher.clearRemote()
            if (ok) RemoteServiceManager.unbind()
            _settingsMessage.value = uiTextOf(
                if (ok) R.string.settings_core_data_clear_done else R.string.settings_core_data_clear_failed
            )
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

    val deployWithPause: StateFlow<Boolean> = appSettingsManager.deployWithPause
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDeployWithPause(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setDeployWithPause(enabled)
        }
    }

    val telemetryEnabled: StateFlow<Boolean> = appSettingsManager.telemetryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setTelemetryEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsManager.setTelemetryEnabled(enabled) }
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

    val yituliuOpenApiToken: StateFlow<String> = appSettingsManager.yituliuOpenApiToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setYituliuOpenApiToken(token: String) {
        _yituliuVerifyMessage.value = null
        viewModelScope.launch { appSettingsManager.setYituliuOpenApiToken(token) }
    }

    val operBoxUseYituliuApi: StateFlow<Boolean> = appSettingsManager.operBoxUseYituliuApi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setOperBoxUseYituliuApi(enabled: Boolean) {
        viewModelScope.launch { appSettingsManager.setOperBoxUseYituliuApi(enabled) }
    }

    /** 与自动战斗页同一个开关 */
    val operBoxAssistState: StateFlow<CopilotOperBoxAssist.State> = operBoxAssist.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CopilotOperBoxAssist.State())

    fun setOperBoxAssistEnabled(enabled: Boolean) {
        viewModelScope.launch { operBoxAssist.setEnabled(enabled) }
    }

    /** [ok] 决定提示用什么颜色 */
    data class TokenVerifyMessage(val text: UiText, val ok: Boolean)

    private val _yituliuVerifyMessage = MutableStateFlow<TokenVerifyMessage?>(null)
    val yituliuVerifyMessage: StateFlow<TokenVerifyMessage?> = _yituliuVerifyMessage.asStateFlow()

    private val _yituliuVerifying = MutableStateFlow(false)
    val yituliuVerifying: StateFlow<Boolean> = _yituliuVerifying.asStateFlow()

    /** 只报结果，不自己打开干员识别开关 */
    fun verifyYituliuToken() {
        val token = appSettingsManager.yituliuOpenApiToken.value.trim()
        if (token.isEmpty()) {
            _yituliuVerifyMessage.value = TokenVerifyMessage(
                uiTextOf(R.string.settings_yituliu_token_empty),
                ok = false,
            )
            return
        }
        if (_yituliuVerifying.value) return
        viewModelScope.launch {
            _yituliuVerifying.value = true
            val result = yituliuApiService.fetchOperators(token)
            // 等待期间 Token 被改过就丢掉这次结果
            if (token != appSettingsManager.yituliuOpenApiToken.value.trim()) {
                _yituliuVerifying.value = false
                return@launch
            }
            _yituliuVerifyMessage.value = when (result) {
                is YituliuApiService.Result.Success -> TokenVerifyMessage(
                    text = if (result.operators.isEmpty()) {
                        uiTextOf(R.string.settings_yituliu_token_valid_no_data)
                    } else {
                        uiTextOf(R.string.settings_yituliu_token_valid, result.operators.size)
                    },
                    ok = true,
                )

                is YituliuApiService.Result.Failure -> TokenVerifyMessage(result.message(), ok = false)
            }
            _yituliuVerifying.value = false
        }
    }

    val forceFullscreenOnVirtualDisplay: StateFlow<Boolean> =
        appSettingsManager.forceFullscreenOnVirtualDisplay
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setForceFullscreenOnVirtualDisplay(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setForceFullscreenOnVirtualDisplay(enabled)
        }
    }

    val pipOnHome: StateFlow<Boolean> = appSettingsManager.pipOnHome
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setPipOnHome(enabled: Boolean) {
        viewModelScope.launch { appSettingsManager.setPipOnHome(enabled) }
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

    val virtualDisplayBackend: StateFlow<VirtualDisplayBackend> =
        appSettingsManager.virtualDisplayBackend
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                VirtualDisplayBackend.LEGACY,
            )

    /** 运行中不许换；空闲时先关掉留着的虚拟屏（连同上面的游戏），下一轮按新后端建 */
    fun setVirtualDisplayBackend(backend: VirtualDisplayBackend) {
        if (backend == appSettingsManager.virtualDisplayBackend.value || coreBusy(R.string.settings_game_package_busy)) return
        viewModelScope.launch {
            compositionService.stopVirtualDisplay()
            appSettingsManager.setVirtualDisplayBackend(backend)
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
            LocaleBootstrap.applyLanguage(app, resolved)
        }
    }

    // Android 特化任务覆盖
    val tasksOverrideEnabled: StateFlow<Boolean> = appSettingsManager.tasksOverrideEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setTasksOverrideEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setTasksOverrideEnabled(enabled)
            // 进程还活着，用 invalidate 保留资源档信息
            resourceLoader.invalidate()
        }
    }

    val gamePackages: StateFlow<Map<String, String>> = appSettingsManager.gamePackages

    val coreIdle: StateFlow<Boolean> = compositionService.state.map { it.isIdle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), compositionService.state.value.isIdle)

    /** 运行中改会让 Meow 与 core 用的包对不上；core 侧由下次加载比对重载 */
    fun setGamePackage(clientType: String, packageName: String?) {
        if (coreBusy(R.string.settings_game_package_busy)) return
        viewModelScope.launch { appSettingsManager.setGamePackage(clientType, packageName) }
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
    val customBackgroundMonet: StateFlow<Boolean> = appSettingsManager.customBackgroundMonet

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

    fun setCustomBackgroundMonet(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setCustomBackgroundMonet(enabled)
        }
    }

    // ========== 更新日志 ==========

    private val _showChangelog = MutableStateFlow(false)
    val showChangelog: StateFlow<Boolean> = _showChangelog.asStateFlow()

    /** 当前版本留档的更新日志；版本号对不上（如旁路装了新版）一律视为没有 */
    val currentChangelog: StateFlow<ChangelogArchive?> = combine(
        appSettingsManager.currentChangelogVersion,
        appSettingsManager.currentChangelogContent,
    ) { version, content ->
        if (version == BuildConfig.VERSION_NAME && content.isNotBlank()) {
            ChangelogArchive(version = version, markdown = content)
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onShowChangelog() {
        _showChangelog.value = true
    }

    fun onDismissChangelog() {
        _showChangelog.value = false
    }
}
