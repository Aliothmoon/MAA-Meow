package com.aliothmoon.maameow.overlay

import android.app.Application
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.overlay.border.BorderOverlayManager
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.view.panel.ExpandedControlPanel
import com.aliothmoon.maameow.service.AccessibilityHelperService
import com.aliothmoon.maameow.utils.Misc
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxDisplayMode
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.FxScopeType
import com.petterp.floatingx.compose.enableComposeSupport
import com.petterp.floatingx.listener.IKeyBackListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class OverlayController(
    private val context: Application,
    val borderOverlayManager: BorderOverlayManager,
    private val fwViewModelOwner: OverlayViewModelOwner,
    private val compositionService: MaaCompositionService,
    private val appSettings: AppSettingsManager
) {

    companion object {
        private const val MAIN_PANEL_TAG = "MP_TAG"
        private const val FLOAT_BALL_TAG = "FB_TAG"
        private val ENABLE_LOG = false
    }

    private val _isLocked = MutableStateFlow(true)
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    private var calculatedPanelLayout: Pair<Int, Int>? = null
    private var orientation = context.resources.configuration.orientation


    private val _signal = MutableSharedFlow<MaaExecutionState>(extraBufferCapacity = 1)
    val signal: SharedFlow<MaaExecutionState> = _signal.asSharedFlow()

    private var currentMode: OverlayControlMode = OverlayControlMode.ACCESSIBILITY
    private var maaStateJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val callback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val newLayout = calculatePanelLayout(newConfig)
            val changed = newConfig.orientation != orientation || newLayout != calculatedPanelLayout
            calculatedPanelLayout = newLayout
            orientation = newConfig.orientation
            if (changed) {
                applyPanelLayout(newConfig, newLayout)
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() {
        }
    }

    fun setup() {
        context.registerComponentCallbacks(callback)
        scope.launch {
            appSettings.runMode.collect { mode ->
                when (mode) {
                    RunMode.FOREGROUND -> {
                        install()
                        // 触发初始布局计算
                        callback.onConfigurationChanged(context.resources.configuration)
                        startMaaStateObserver()
                    }

                    RunMode.BACKGROUND -> {
                        stopMaaStateObserver()
                        hideAll()
                        uninstall()
                    }
                }
            }
        }
    }

    private fun startMaaStateObserver() {
        if (maaStateJob != null) return
        maaStateJob = scope.launch {
            var previous = compositionService.state.value
            compositionService.state.collect { state ->
                Timber.d("OverlayController: MaaCompositionService state changed: $state")
                onMaaStateChanged(previous, state)
                previous = state
            }
        }
    }

    private fun stopMaaStateObserver() {
        maaStateJob?.cancel()
        maaStateJob = null
    }

    private suspend fun onMaaStateChanged(previous: MaaExecutionState, current: MaaExecutionState) {
        if (!_isActive.value) return

        val wasActive =
            previous == MaaExecutionState.RUNNING || previous == MaaExecutionState.STARTING
        val isActive = current == MaaExecutionState.RUNNING || current == MaaExecutionState.STARTING

        when {
            // 进入运行态：隐藏主面板，显示悬浮控件
            current == MaaExecutionState.RUNNING && previous != MaaExecutionState.RUNNING -> {
                hideMainPanel()
                when (currentMode) {
                    OverlayControlMode.FLOAT_BALL -> {
                        showFloatBall()
                        Timber.d("OverlayController: 任务运行中，显示悬浮球")
                    }

                    OverlayControlMode.ACCESSIBILITY -> {
                        borderOverlayManager.show()
                        Timber.d("OverlayController: 任务运行中，显示边框")
                    }
                }
            }

            // 离开运行态：恢复主面板
            wasActive && !isActive -> {
                when (currentMode) {
                    OverlayControlMode.ACCESSIBILITY -> {
                        borderOverlayManager.hide()
                        showMainPanel()
                        Timber.d("OverlayController: 任务结束，隐藏边框，恢复主面板")
                    }

                    OverlayControlMode.FLOAT_BALL -> {
                        hideFloatBall()
                        showMainPanel()
                        Timber.d("OverlayController: 任务结束，隐藏悬浮球，恢复主面板")
                    }
                }
                _signal.tryEmit(current)
            }
        }
    }

    fun getNewComposeView(): ComposeView {
        return ComposeView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            fwViewModelOwner.let {
                setViewTreeLifecycleOwner(it)
                setViewTreeViewModelStoreOwner(it)
                setViewTreeSavedStateRegistryOwner(it)
            }
            calculatedPanelLayout?.let {
                layoutParams = ViewGroup.LayoutParams(
                    it.first,
                    it.second
                )
            }
            setContent {
                CompositionLocalProvider(LocalFloatingWindowContext provides true) {
                    val isLocked by _isLocked.collectAsState()
                    ExpandedControlPanel(
                        onClose = ::onCloseControlPanel,
                        onHome = { Misc.bringAppToFront(context) },
                        isLocked = isLocked,
                        onLockToggle = { locked ->
                            _isLocked.value = locked
                            if (locked) {
                                lockMainPanelPosition()
                            } else {
                                unlockMainPanelPosition()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun calculatePanelLayout(config: Configuration): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val height =
            config.screenHeightDp * density * if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                0.85
            } else {
                0.6
            }
        val width = config.screenWidthDp * density * 0.85
        return width.toInt() to height.toInt()
    }

    private fun applyPanelLayout(config: Configuration, layout: Pair<Int, Int>) {
        val density = context.resources.displayMetrics.density
        val (width, height) = layout
        Timber.d("applyPanelLayout: $width x $height")

        FloatingX.controlOrNull(MAIN_PANEL_TAG)?.let {
            val show = it.isShow()
            if (show) {
                it.hide()
            }
            val screenWidth = config.screenWidthDp * density
            val screenHeight = config.screenHeightDp * density
            val centerX = (screenWidth - width) / 2
            val centerY = (screenHeight - height) / 2
            Timber.d("recalculatePanelLayout: screenSize=${screenWidth}x${screenHeight}, panelSize=${width}x${height}, center=($centerX, $centerY)")
            it.move(centerX, centerY)
            it.updateView(getNewComposeView())
            if (show) {
                it.show()
            }
        }

        FloatingX.controlOrNull(FLOAT_BALL_TAG)?.let {
            if (it.isShow()) {
                val screenWidth = config.screenWidthDp * density
                val screenHeight = config.screenHeightDp * density
                Timber.d("FLOAT_BALL_TAG $screenWidth x $screenHeight")
                val ballX = screenWidth
                val ballY = (screenHeight) / 2
                Timber.d("FLOAT_BALL_TAG $ballX x $ballY")
                it.move(ballX, ballY)
            }
        }
    }

    fun install() {
        if (!FloatingX.isInstalled(MAIN_PANEL_TAG)) {
            FloatingX.install {
                enableComposeSupport()
                setContext(context)
                setTag(MAIN_PANEL_TAG)
                setScopeType(FxScopeType.SYSTEM)
                setLayoutView(getNewComposeView())
                setEnableEdgeAdsorption(false)
                setEnableLog(ENABLE_LOG)
                setGravity(FxGravity.CENTER)
                setEnableSafeArea(false)
                setEnableAnimation(true)
                setDisplayMode(FxDisplayMode.ClickOnly)
                setEnableKeyBoardAdapt(true)
                setKeyBackListener(object : IKeyBackListener {
                    override fun onBackPressed(): Boolean {
                        return true
                    }
                })
            }
        }
        if (!FloatingX.isInstalled(FLOAT_BALL_TAG)) {
            FloatingX.install {
                enableComposeSupport()
                setContext(context)
                setTag(FLOAT_BALL_TAG)
                setScopeType(FxScopeType.SYSTEM)
                setLayoutView(
                    ComposeView(context).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        fwViewModelOwner.let {
                            setViewTreeLifecycleOwner(it)
                            setViewTreeViewModelStoreOwner(it)
                            setViewTreeSavedStateRegistryOwner(it)
                        }
                        setContent {
                            val runningState by compositionService.state.collectAsState()
                            FloatBall(
                                onClick = ::onFloatBallClick,
                                runningState = runningState
                            )
                        }
                    }
                )
                setEnableLog(ENABLE_LOG)
                setEnableEdgeAdsorption(true)
                setGravity(FxGravity.RIGHT_OR_CENTER)
                setEnableAnimation(true)
            }
        }
        Timber.d("OverlayController: install completed")
    }

    fun uninstall() {
        FloatingX.uninstallAll()
        Timber.d("OverlayController: uninstall completed")
    }

    fun onFloatBallClick() {
        hideFloatBall()
        if (compositionService.state.value == MaaExecutionState.RUNNING) {
            scope.launch {
                compositionService.stop()
            }
        }
        showMainPanel()
    }

    fun onCloseControlPanel() {
        hideMainPanel()
        if (currentMode == OverlayControlMode.FLOAT_BALL) {
            showFloatBall()
        }
    }

    fun showMainPanel() {
        scope.launch {
            fwViewModelOwner.start()
            FloatingX.controlOrNull(MAIN_PANEL_TAG)?.show()
        }
    }

    fun hideMainPanel() {
        scope.launch {
            FloatingX.controlOrNull(MAIN_PANEL_TAG)?.hide()
            fwViewModelOwner.stop()
        }
    }

    fun showFloatBall() {
        FloatingX.controlOrNull(FLOAT_BALL_TAG)?.show()
    }

    fun hideFloatBall() {
        FloatingX.controlOrNull(FLOAT_BALL_TAG)?.hide()
    }

    fun lockMainPanelPosition() {
        FloatingX.controlOrNull(MAIN_PANEL_TAG)?.updateConfig {
            setDisplayMode(FxDisplayMode.ClickOnly)
        }
    }

    fun unlockMainPanelPosition() {
        FloatingX.controlOrNull(MAIN_PANEL_TAG)?.updateConfig {
            setDisplayMode(FxDisplayMode.Normal)
        }
    }

    fun show(mode: OverlayControlMode = OverlayControlMode.ACCESSIBILITY) {
        if (appSettings.runMode.value != RunMode.FOREGROUND) {
            Timber.w("OverlayController: 非前台模式，忽略 show 请求")
            return
        }
        currentMode = mode

        when (mode) {
            OverlayControlMode.ACCESSIBILITY -> {
                // 快捷键模式：隐藏悬浮球，注册音量键监听，显示主面板
                hideFloatBall()
                registerVolumeKeyListener()
                showMainPanel()
                Timber.d("OverlayController: 快捷键模式已启动")
            }

            OverlayControlMode.FLOAT_BALL -> {
                // 悬浮球模式：注销音量键监听，显示悬浮球
                unregisterVolumeKeyListener()
                showFloatBall()
                Timber.d("OverlayController: 悬浮球模式已启动")
            }
        }
        _isActive.value = true
    }

    suspend fun applyMode(mode: OverlayControlMode) {
        if (currentMode == mode) return

        Timber.d("FloatWindowCompose: 切换模式 $currentMode -> $mode")

        // 隐藏当前模式的 UI
        when (currentMode) {
            OverlayControlMode.ACCESSIBILITY -> {
                // 旧模式是快捷键模式，隐藏边框（如果正在显示）
                borderOverlayManager.hide()
            }

            OverlayControlMode.FLOAT_BALL -> {
                // 旧模式是悬浮球模式，隐藏悬浮球
                hideFloatBall()
            }
        }

        // 应用新模式
        currentMode = mode
        when (mode) {
            OverlayControlMode.ACCESSIBILITY -> {
                registerVolumeKeyListener()
                if (compositionService.state.value == MaaExecutionState.RUNNING) {
                    borderOverlayManager.show()
                }
            }

            OverlayControlMode.FLOAT_BALL -> {
                unregisterVolumeKeyListener()
                showFloatBall()
            }
        }
    }

    suspend fun hideAll() {
        hideMainPanel()
        hideFloatBall()
        borderOverlayManager.hide()
        unregisterVolumeKeyListener()
        _isActive.value = false
    }

    fun toggleMainPanel() {
        val mainPanelShowing = FloatingX.controlOrNull(MAIN_PANEL_TAG)?.isShow() == true
        if (mainPanelShowing) {
            hideMainPanel()
        } else {
            showMainPanel()
        }
    }

    fun registerVolumeKeyListener() {
        AccessibilityHelperService.onVolumeUpDownPressed.set {
            scope.launch {
                toggleMainPanel()
                if (compositionService.state.value == MaaExecutionState.RUNNING) {
                    compositionService.stop()
                }
            }
        }
    }

    fun unregisterVolumeKeyListener() {
        AccessibilityHelperService.onVolumeUpDownPressed.set(null)
    }

}