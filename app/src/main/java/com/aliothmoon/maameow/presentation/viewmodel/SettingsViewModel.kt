package com.aliothmoon.maameow.presentation.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
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
import com.aliothmoon.maameow.data.resource.BackgroundImageStore
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.service.AchievementReporter
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.WakeAlarmScheduler
import com.aliothmoon.maameow.domain.service.WakeUnlockEngine
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.resolveSelectedLanguage
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap.toLocaleList
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextDynamic
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val wakeAlarmScheduler: WakeAlarmScheduler,
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

    val forceFullscreenOnVirtualDisplay: StateFlow<Boolean> =
        appSettingsManager.forceFullscreenOnVirtualDisplay
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setForceFullscreenOnVirtualDisplay(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setForceFullscreenOnVirtualDisplay(enabled)
        }
    }

    // ───────────────── 定时唤醒 + 解锁 ─────────────────

    /**
     * 「定时唤醒 + 解锁」功能是否可用：
     * 仅当后端 = Root 且 RemoteService 已连接时可用。
     *
     * 为什么 Shizuku 不行：Shizuku 进程以 shell uid 跑 shell 命令，
     * 可以执行 KEYCODE_WAKEUP / input swipe / input text，但：
     *  - `svc keyguard disable` 被 SELinux 拒绝（shell uid 无此权限）
     *  - 屏幕完全锁定下部分 ROM 不允许 shell 注入 input 事件
     *  - 设备重启后 Shizuku 需要手动授权一次，定时闹钟醒来时 Shizuku 往往还没就绪
     * 所以这个功能仅推荐 Root 后端。
     */
    val wakeFeatureAvailable: StateFlow<Boolean> =
        combine(
            RemoteServiceManager.state,
            appSettingsManager.startupBackend
        ) { svcState, backend ->
            backend == RemoteBackend.ROOT && svcState is RemoteServiceManager.ServiceState.Connected
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val wakeScheduleEnabled: StateFlow<Boolean> =
        appSettingsManager.wakeScheduleEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setWakeScheduleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setWakeScheduleEnabled(enabled)
            wakeAlarmScheduler.reschedule()
        }
    }

    val wakeScheduleTimesCsv: StateFlow<String> =
        appSettingsManager.wakeScheduleTimesCsv
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setWakeScheduleTimesCsv(csv: String) {
        viewModelScope.launch {
            appSettingsManager.setWakeScheduleTimesCsv(csv)
            wakeAlarmScheduler.reschedule()
        }
    }

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

    val wakeAutoSleepDelaySec: StateFlow<Int> =
        appSettingsManager.wakeAutoSleepDelaySec
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setWakeAutoSleepDelaySec(seconds: Int) {
        viewModelScope.launch { appSettingsManager.setWakeAutoSleepDelaySec(seconds) }
    }

    /** 滑动起点 X 百分比（0.0–1.0），-1.0 表示未校准 */
    val swipeStartXPercent: StateFlow<Float> =
        appSettingsManager.swipeStartXPercent
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1.0f)

    /** 滑动起点 Y 百分比（0.0–1.0），-1.0 表示未校准 */
    val swipeStartYPercent: StateFlow<Float> =
        appSettingsManager.swipeStartYPercent
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1.0f)

    fun setSwipeCalibration(xPercent: Float, yPercent: Float) {
        viewModelScope.launch { appSettingsManager.setSwipeCalibration(xPercent, yPercent) }
    }

    fun clearSwipeCalibration() {
        viewModelScope.launch { appSettingsManager.clearSwipeCalibration() }
    }

    /** swipe 后等待秒数（PIN 键盘弹出 + 密码框获焦预留时间） */
    val wakePinWaitSec: StateFlow<Float> =
        appSettingsManager.wakePinWaitSec
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.5f)

    fun setWakePinWaitSec(seconds: Float) {
        viewModelScope.launch { appSettingsManager.setWakePinWaitSec(seconds) }
    }

    /** 解锁失败最大重试次数 */
    val wakeUnlockMaxRetries: StateFlow<Int> =
        appSettingsManager.wakeUnlockMaxRetries
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    fun setWakeUnlockMaxRetries(retries: Int) {
        viewModelScope.launch { appSettingsManager.setWakeUnlockMaxRetries(retries) }
    }

    /** 唤醒测试结果：null=未测，UiText.Empty=进行中，其它=结果文案。 */
    private val _wakeTestResult = MutableStateFlow<UiText?>(null)
    val wakeTestResult: StateFlow<UiText?> = _wakeTestResult.asStateFlow()

    fun runWakeTest() {
        viewModelScope.launch {
            _wakeTestResult.value = UiText.Empty
            val typeKey = appSettingsManager.wakeUnlockType.first()
            val credential = appSettingsManager.wakeCredential.first()
            val type = WakeUnlockEngine.UnlockType.fromKey(typeKey)
            val calibration = currentSwipeCalibration()
            val pinWait = appSettingsManager.wakePinWaitSec.first()
            val retries = appSettingsManager.wakeUnlockMaxRetries.first()
            val cfg = WakeUnlockEngine.WakeConfig(
                unlockType = type,
                credential = credential,
                swipeStartCalibration = calibration,
                pinWaitSec = pinWait,
                maxRetries = retries,
            )
            // 走「先息屏上锁 → 再唤醒解锁」完整序列，才叫真正的测试。
            val ok = wakeUnlockEngine.lockThenWakeAndUnlock(cfg)
            _wakeTestResult.value = if (ok) uiTextDynamic("OK") else uiTextDynamic("FAIL")
        }
    }

    /** 读取当前校准数据，未校准返回 null */
    private suspend fun currentSwipeCalibration(): WakeUnlockEngine.SwipeCalibration? {
        val x = appSettingsManager.swipeStartXPercent.first()
        val y = appSettingsManager.swipeStartYPercent.first()
        return if (x >= 0f && y >= 0f) WakeUnlockEngine.SwipeCalibration(x, y) else null
    }

    fun clearWakeTestResult() {
        _wakeTestResult.value = null
    }

    // 后台虚拟显示器模式：游戏漂移自动拉回开关
    val driftAutoRepinEnabled: StateFlow<Boolean> =
        appSettingsManager.driftAutoRepinEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDriftAutoRepinEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setDriftAutoRepinEnabled(enabled)
        }
    }

    // 漂移拉回延迟（秒）
    val driftAutoRepinDelaySec: StateFlow<Int> =
        appSettingsManager.driftAutoRepinDelaySec
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    fun setDriftAutoRepinDelaySec(seconds: Int) {
        viewModelScope.launch {
            appSettingsManager.setDriftAutoRepinDelaySec(seconds)
        }
    }

    val allowForegroundScheduledTask: StateFlow<Boolean> =
        appSettingsManager.allowForegroundScheduledTask

    fun setAllowForegroundScheduledTask(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setAllowForegroundScheduledTask(enabled)
        }
    }

    val runScheduleWhenLocked: StateFlow<Boolean> = appSettingsManager.runScheduleWhenLocked

    fun setRunScheduleWhenLocked(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.setRunScheduleWhenLocked(enabled)
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
}
