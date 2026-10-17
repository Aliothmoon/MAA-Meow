package com.aliothmoon.maameow.presentation.view.background

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.SystemClock
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isInvisible
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.models.RunDurationLimit
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.UnifiedStateDispatcher
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.overlay.screensaver.ScreenSaverOverlayManager
import com.aliothmoon.maameow.presentation.LocalInputFocusManager
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.PanelPromptDialog
import com.aliothmoon.maameow.presentation.components.BackendReadyFixHost
import com.aliothmoon.maameow.presentation.components.LocalPageVisible
import com.aliothmoon.maameow.presentation.components.PageBackHandler
import com.aliothmoon.maameow.presentation.components.LogExportController
import com.aliothmoon.maameow.presentation.components.MaaWindowInsets
import com.aliothmoon.maameow.presentation.components.collectWhilePageVisible
import com.aliothmoon.maameow.presentation.components.BottomBarOverlay
import com.aliothmoon.maameow.presentation.components.LocalSettingRowBleed
import com.aliothmoon.maameow.presentation.components.horizontalBleed
import com.aliothmoon.maameow.presentation.components.rememberBackendReadyFixState
import com.aliothmoon.maameow.presentation.components.rememberPageVisible
import com.aliothmoon.maameow.presentation.components.rememberStagedBeyondViewportCount
import com.aliothmoon.maameow.presentation.navigation.BottomNavTab
import com.aliothmoon.maameow.presentation.onboarding.LocalOnboardingState
import com.aliothmoon.maameow.presentation.onboarding.OnboardingTarget
import com.aliothmoon.maameow.presentation.onboarding.onboardingTarget
import com.aliothmoon.maameow.presentation.pip.LocalIsInPip
import com.aliothmoon.maameow.presentation.pip.PipController
import com.aliothmoon.maameow.presentation.pip.PipHost
import com.aliothmoon.maameow.presentation.pip.PipRequest
import com.aliothmoon.maameow.presentation.search.ProvideSettingSearch
import com.aliothmoon.maameow.presentation.search.SettingLocation
import com.aliothmoon.maameow.presentation.search.SettingSearchNavigator
import com.aliothmoon.maameow.presentation.search.SettingSearchTarget
import com.aliothmoon.maameow.presentation.state.PreviewPointerSlots
import com.aliothmoon.maameow.presentation.view.panel.AutoBattlePanel
import com.aliothmoon.maameow.presentation.view.panel.LocalToolboxFileExporter
import com.aliothmoon.maameow.presentation.view.panel.LogPanel
import com.aliothmoon.maameow.presentation.view.panel.PanelHeader
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import com.aliothmoon.maameow.presentation.view.panel.TaskListDetailLayout
import com.aliothmoon.maameow.presentation.view.panel.ToolboxPanel
import com.aliothmoon.maameow.presentation.view.panel.rememberSafToolboxFileExporter
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.presentation.viewmodel.CopilotViewModel
import com.aliothmoon.maameow.presentation.viewmodel.ToolboxTab
import com.aliothmoon.maameow.presentation.viewmodel.ToolboxViewModel
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaMotion
import com.aliothmoon.maameow.theme.MaaThemeAlphas
import com.aliothmoon.maameow.theme.OpaqueTheme
import com.aliothmoon.maameow.theme.PagerPageSpacing
import com.aliothmoon.maameow.theme.rememberMaaPagerFling
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber
import kotlin.math.roundToInt

@Composable
private fun StartBarProgress(visible: Boolean, color: Color) {
    val pageVisible by LocalPageVisible.current
    Box(Modifier.size(20.dp)) {
        if (visible && pageVisible) {
            CircularProgressIndicator(modifier = Modifier.fillMaxSize(), color = color, strokeWidth = 2.dp)
        }
    }
}

@Composable
fun BackgroundTaskView(
    viewModel: BackgroundTaskViewModel,
    /** 子页面盖上来时 MainScreen 用 alpha 0 保活组合树，本页仍在组合但看不见，此时不能开画中画；传函数免得切页重组整页 */
    isActivePage: () -> Boolean = { true },
    copilotViewModel: CopilotViewModel = koinInject(),
    toolboxViewModel: ToolboxViewModel = koinInject(),
    compositionService: MaaCompositionService = koinInject(),
    dispatcher: UnifiedStateDispatcher = koinInject(),
    screenSaverManager: ScreenSaverOverlayManager = koinInject(),
    appSettingsManager: AppSettingsManager = koinInject(),
    permissionManager: PermissionManager = koinInject(),
) {

    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maaState by compositionService.state.collectAsStateWithLifecycle()
    val isTaskActive = maaState != MaaExecutionState.IDLE && maaState != MaaExecutionState.ERROR
    val runMode by appSettingsManager.runMode.collectAsStateWithLifecycle()
    val permissionState by permissionManager.state.collectAsStateWithLifecycle()
    val markers by viewModel.markers.collectAsStateWithLifecycle()
    val displayResolution by compositionService.displayResolution.collectAsStateWithLifecycle()
    val pipOnHome by appSettingsManager.pipOnHome.collectAsStateWithLifecycle()
    val isChainLoaded by viewModel.chainState.isLoaded.collectAsStateWithLifecycle()
    var hasInitialized by rememberSaveable { mutableStateOf(false) }
    if (isChainLoaded) {
        hasInitialized = true
    }
    val isInitialized = hasInitialized

    var showCloseConfirm by remember { mutableStateOf(false) }
    var showMoreActions by remember { mutableStateOf(false) }
    var showLogExportSheet by remember { mutableStateOf(false) }
    LogExportController(
        sheetVisible = showLogExportSheet,
        onSheetDismiss = { showLogExportSheet = false },
    )

    val copilotDialog by copilotViewModel.dialog.collectAsStateWithLifecycle()
    val toolboxDialog by toolboxViewModel.dialog.collectAsStateWithLifecycle()
    val nodes by viewModel.chainState.chain.collectAsStateWithLifecycle()
    val profiles by viewModel.chainState.profiles.collectAsStateWithLifecycle()
    val profileId by viewModel.chainState.profileId.collectAsStateWithLifecycle()
    val selectedNode = nodes.find { it.id == state.selectedNodeId }
    val clientType = remember(nodes) { viewModel.chainState.clientType }
    val canShowTaskActions = PanelTab.canShowTaskActions(state.current)

    // 引导靶点在任务面板，重看时先切回去
    val onboarding = LocalOnboardingState.current
    val onboardingOnThisPage by remember(onboarding) {
        derivedStateOf {
            onboarding?.active == true && onboarding.currentStep.tab == BottomNavTab.BACKGROUND
        }
    }
    LaunchedEffect(onboardingOnThisPage) {
        if (onboardingOnThisPage && state.current != PanelTab.TASKS) {
            viewModel.onTabChange(PanelTab.TASKS)
        }
    }

    // 搜索定位复用当前支持快捷选项的面板，包括日志页。
    val searchNavigator: SettingSearchNavigator = koinInject()
    val pendingSearch by searchNavigator.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSearch) {
        val request = pendingSearch ?: return@LaunchedEffect
        if (request.entry.location != SettingLocation.BackgroundActions || !request.isFresh()) {
            return@LaunchedEffect
        }
        if (!PanelTab.canShowTaskActions(state.current)) viewModel.onTabChange(PanelTab.TASKS)
        showMoreActions = true
    }

    val pagerState = rememberPagerState(
        initialPage = state.current.ordinal, pageCount = { PanelTab.entries.size })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val newTab = PanelTab.entries[page]
            if (newTab != state.current) {
                viewModel.onTabChange(newTab)
            }
        }
    }

    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(state.current, reduceMotion) {
        if (pagerState.currentPage != state.current.ordinal) {
            pagerState.animateScrollToPage(
                state.current.ordinal,
                animationSpec = tween(
                    durationMillis = MaaMotion.pagerDuration(1, reduceMotion),
                    easing = MaaMotion.Emphasized,
                ),
            )
        }
    }
    val context = LocalContext.current
    val serviceDiedMessage = stringResource(R.string.bg_toast_service_died)


    LaunchedEffect(Unit) {
        dispatcher.serviceDiedEvent.collect {
            Toast.makeText(
                context, serviceDiedMessage, Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.screenshotMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val shouldHideMoreActions by remember {
        derivedStateOf {
            !canShowTaskActions || showCloseConfirm || state.isFullscreenMonitor || state.dialog != null
        }
    }
    LaunchedEffect(shouldHideMoreActions) {
        if (shouldHideMoreActions) showMoreActions = false
    }


    var isSurfaceAvailable by remember { mutableStateOf(false) }
    var lastSentSurface by remember { mutableStateOf<Surface?>(null) }
    val currentResolution by rememberUpdatedState(displayResolution)
    val currentIsActivePage by rememberUpdatedState(isActivePage)

    val pipHost = context as? PipHost
    val isInPip = LocalIsInPip.current

    // 竞态兜底：PiP 条件已排除全屏态，但 setPictureInPictureParams 要跨进程生效，
    // 点开全屏后立刻按 Home 仍可能带着全屏态进小窗，进去就退掉
    LaunchedEffect(isInPip) {
        if (isInPip) viewModel.onExitFullscreenMonitor()
    }

    var previewBounds by remember { mutableStateOf<Rect?>(null) }

    val previewContent = remember {
        movableContentOf {
            val innerScope = rememberCoroutineScope()
            Box(
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.aspectRatio(DefaultDisplayConfig.ASPECT_RATIO)) {
                    // 特权进程换了一个：旧 Surface 还连着上一个进程，新进程接不上，重建 SurfaceView
                    val surfaceEpoch by viewModel.previewSurfaceEpoch.collectAsStateWithLifecycle()
                    key(surfaceEpoch) {
                        AndroidView(
                            factory = { ctx ->
                                SurfaceView(ctx).apply {
                                    holder.setFormat(PixelFormat.RGBA_8888)
                                    holder.addCallback(object : SurfaceHolder.Callback {
                                        override fun surfaceCreated(holder: SurfaceHolder) {
                                            isSurfaceAvailable = true
                                            innerScope.launch {
                                                delay(50)
                                                val res = currentResolution
                                                holder.setFixedSize(res.width, res.height)
                                            }
                                        }

                                        override fun surfaceChanged(
                                            holder: SurfaceHolder, format: Int, width: Int, height: Int
                                        ) {
                                            Timber.d("Surface size changed to $width x $height")
                                            val res = currentResolution
                                            if (width == res.width && height == res.height) {
                                                if (lastSentSurface != holder.surface) {
                                                    lastSentSurface = holder.surface
                                                    viewModel.onSurfaceAvailable(holder.surface)
                                                }
                                            }
                                        }

                                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                                            isSurfaceAvailable = false
                                            lastSentSurface = null
                                            viewModel.onSurfaceDestroyed()
                                        }
                                    })
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            // 本页常驻后离页不再销毁 Surface，隐藏让远端停投屏
                            update = { it.isInvisible = !currentIsActivePage() },
                        )
                    }
                    // 必须重新读，movableContent 是 remember 出来的，捕获外层 val 会拿到陈旧值
                    if (!LocalIsInPip.current && markers.isNotEmpty()) TouchPreviewOverlay(
                        markers = markers,
                        displayResolution = displayResolution,
                        modifier = Modifier.fillMaxSize()
                    )
                    val gameFps by viewModel.gameFps.collectAsStateWithLifecycle()
                    gameFps?.let { fps ->
                        Text(
                            text = "${fps.roundToInt()} FPS",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.35f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    val pipActivity = pipHost as? Activity
    val pipSupported = remember(context) { PipController.isSupported(context) }
    // 只在协程里读：预览随分页滑动时坐标逐帧变，放进组合会逐帧重组并跨进程设参数
    LaunchedEffect(pipHost, pipActivity) {
        val host = pipHost ?: return@LaunchedEffect
        val activity = pipActivity ?: return@LaunchedEffect
        fun arm(enabled: Boolean, sourceRect: Rect?) {
            val request = PipRequest(currentResolution, sourceRect)
            host.pipRequest = if (enabled) request else null
            PipController.updateParams(activity, enabled, request)
        }
        try {
            snapshotFlow {
                // 全屏预览态排除在外：auto-enter 没有前置钩子，带着强制横屏和收起的系统栏进小窗会互相打架
                val eligible = pipOnHome &&
                        currentIsActivePage() &&
                        !state.isFullscreenMonitor &&
                        runMode == RunMode.BACKGROUND &&
                        (maaState == MaaExecutionState.STARTING || maaState == MaaExecutionState.RUNNING) &&
                        isSurfaceAvailable &&
                        pipSupported
                // 不可开时不读坐标
                Triple(eligible, currentResolution, if (eligible) previewBounds else null)
            }.distinctUntilChanged().collect { (eligible, _, bounds) -> arm(eligible, bounds) }
        } finally {
            arm(enabled = false, sourceRect = null)
        }
    }

    if (isInPip) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            previewContent()
        }
        return
    }

    // 点开始时后端没就绪的一次性引导，勾了跳过检查也弹
    val backendFix = rememberBackendReadyFixState()
    BackendReadyFixHost(backendFix)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(MaaWindowInsets.topBar)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(3f)
                    .onboardingTarget(OnboardingTarget.BG_PREVIEW)
            ) {
                if (!state.isFullscreenMonitor) {
                    VirtualDisplayPreview(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                val bounds = coords.boundsInWindow()
                                val next = Rect(
                                    bounds.left.toInt(),
                                    bounds.top.toInt(),
                                    bounds.right.toInt(),
                                    bounds.bottom.toInt(),
                                )
                                if (!next.isEmpty && next != previewBounds) {
                                    previewBounds = next
                                }
                            },
                        isRunning = maaState == MaaExecutionState.RUNNING,
                        isSurfaceAvailable = isSurfaceAvailable,
                        onClick = { viewModel.onToggleFullscreenMonitor() }) {
                        previewContent()
                    }
                } else {
                    Spacer(modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(7f)
            ) {
                PanelHeader(
                    selectedTab = state.current,
                    onTabSelected = { tab -> viewModel.onTabChange(tab) },
                    showActions = false,
                    modifier = Modifier.onboardingTarget(OnboardingTarget.BG_PANEL_TABS),
                )

                if (isInitialized) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val inputFocusManager = LocalInputFocusManager.current
                        // 启动按钮的两种「禁用态」：① 前台模式不从后台任务页启动；
                        // ② 远程后端（Shizuku/Root）没就绪，不可用或未授权。两者均显示为禁用态但仍可点击，
                        // 点击给出对应提示或引导（防呆），与领域层 checkPreconditions 守卫一致。
                        val foregroundBlocked = runMode == RunMode.FOREGROUND
                        // 不可用时授权必为 false，一个判断盖住两种情况
                        val backendBlocked = !permissionState.remoteAccessGranted
                        val startBlocked = foregroundBlocked || backendBlocked
                        val switchBackgroundModeMessage =
                            stringResource(R.string.navigation_toast_switch_background_mode)
                        // 所有启动入口共用，新入口别绕开
                        val guardedStart: (() -> Unit) -> Unit = { start ->
                            inputFocusManager.clear()
                            when {
                                foregroundBlocked ->
                                    Toast.makeText(context, switchBackgroundModeMessage, Toast.LENGTH_SHORT).show()

                                backendBlocked -> backendFix.request()

                                else -> start()
                            }
                        }
                        val retainedPages = rememberStagedBeyondViewportCount(
                            pagerState, PanelTab.entries.size - 1,
                        )
                        val toolboxTab by toolboxViewModel.currentTab.collectAsStateWithLifecycle()
                        val gachaDisclaimerOk by
                        toolboxViewModel.gachaDisclaimerAccepted.collectAsStateWithLifecycle()
                        val toolsHidesStartBar = toolboxTab == ToolboxTab.GACHA && !gachaDisclaimerOk
                        val hideStartBar = state.current == PanelTab.TOOLS && toolsHidesStartBar
                        val showStartBar = canShowTaskActions && !hideStartBar
                        val isGachaActions = state.current == PanelTab.TOOLS &&
                                toolboxTab == ToolboxTab.GACHA && gachaDisclaimerOk
                        BottomBarOverlay(
                            showBottomBar = showStartBar,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            bottomBar = {
                                Column(Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onboardingTarget(OnboardingTarget.BG_START),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isGachaActions) {
                                            // 运行中换成停止，否则再加「快捷选项」会挤成四键
                                            val gachaRunning = maaState == MaaExecutionState.RUNNING ||
                                                    maaState == MaaExecutionState.STOPPING
                                            if (gachaRunning) {
                                                OutlinedButton(
                                                    onClick = { toolboxViewModel.onStop() },
                                                    enabled = maaState == MaaExecutionState.RUNNING,
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.error,
                                                    ),
                                                ) {
                                                    if (maaState == MaaExecutionState.STOPPING) {
                                                        StartBarProgress(visible = showStartBar, color = MaterialTheme.colorScheme.error)
                                                    } else {
                                                        Text(
                                                            text = stringResource(R.string.task_btn_stop),
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        guardedStart {
                                                            toolboxViewModel.onStartGacha(once = true)
                                                        }
                                                    },
                                                    enabled = !isTaskActive,
                                                    colors = if (startBlocked) {
                                                        ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.12f,
                                                            ),
                                                            contentColor = MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = MaaThemeAlphas.DISABLED,
                                                            ),
                                                        )
                                                    } else {
                                                        ButtonDefaults.buttonColors()
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                ) {
                                                    if (maaState == MaaExecutionState.STARTING) {
                                                        StartBarProgress(visible = showStartBar, color = MaterialTheme.colorScheme.onPrimary)
                                                    } else {
                                                        Text(
                                                            text = stringResource(R.string.gacha_once),
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        guardedStart {
                                                            toolboxViewModel.onStartGacha(once = false)
                                                        }
                                                    },
                                                    enabled = !isTaskActive,
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.gacha_ten_times),
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                        } else {
                                            // 运行中换成停止，空出的位置给「快捷选项」
                                            val taskRunning =
                                                maaState == MaaExecutionState.RUNNING ||
                                                        maaState == MaaExecutionState.STOPPING
                                            // 日志页没有明确的启动目标，始终保留停止按钮；
                                            // 非运行态沿用禁用样式，停止中仍显示进度且不可重复点击。
                                            if (taskRunning || state.current == PanelTab.LOG) {
                                                OutlinedButton(
                                                    onClick = {
                                                        when (state.current) {
                                                            // 停止入口作用于当前 Core 任务，不受启动页面限制。
                                                            PanelTab.TASKS, PanelTab.LOG -> viewModel.onStopTasks()
                                                            PanelTab.AUTO_BATTLE -> copilotViewModel.onStop()
                                                            PanelTab.TOOLS -> toolboxViewModel.onStop()
                                                        }
                                                    },
                                                    enabled = maaState == MaaExecutionState.RUNNING,
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.error
                                                    )
                                                ) {
                                                    if (maaState == MaaExecutionState.STOPPING) {
                                                        StartBarProgress(visible = showStartBar, color = MaterialTheme.colorScheme.error)
                                                    } else {
                                                        Text(
                                                            text = stringResource(R.string.task_btn_stop),
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        guardedStart {
                                                            when (state.current) {
                                                                PanelTab.TASKS -> viewModel.onStartTasks()
                                                                PanelTab.AUTO_BATTLE -> copilotViewModel.onStart()
                                                                PanelTab.TOOLS -> toolboxViewModel.onStart()
                                                                else -> {}
                                                            }
                                                        }
                                                    },
                                                    enabled = !isTaskActive,
                                                    colors = if (startBlocked) {
                                                        ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.12f
                                                            ),
                                                            contentColor = MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = MaaThemeAlphas.DISABLED
                                                            ),
                                                        )
                                                    } else {
                                                        ButtonDefaults.buttonColors()
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    if (maaState == MaaExecutionState.STARTING) {
                                                        StartBarProgress(visible = showStartBar, color = MaterialTheme.colorScheme.onPrimary)
                                                    } else {
                                                        Text(
                                                            text = stringResource(R.string.task_btn_start),
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 次级操作不参与等分，按内容取宽，剩下的都留给主操作
                                        OutlinedButton(
                                            onClick = { showMoreActions = !showMoreActions },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = if (showMoreActions) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                if (showMoreActions) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline
                                                }
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.task_btn_quick_options),
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            },
                        ) { actionBarHeight ->
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .padding(bottom = actionBarHeight)
                                    .onboardingTarget(OnboardingTarget.BG_TASK_LIST)
                            )
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = true,
                                flingBehavior = rememberMaaPagerFling(pagerState),
                                pageSpacing = PagerPageSpacing,
                                beyondViewportPageCount = retainedPages
                            ) { page ->
                                CompositionLocalProvider(LocalPageVisible provides rememberPageVisible(pagerState, page)) {
                                    val pageTab = PanelTab.entries[page]
                                    // 各页独立预留底栏高度，日志末条也不能被按钮遮挡。
                                    val reserveBar = PanelTab.canShowTaskActions(pageTab) &&
                                            !(pageTab == PanelTab.TOOLS && toolsHidesStartBar)
                                    Box(Modifier.padding(bottom = if (reserveBar) actionBarHeight else 0.dp)) {
                                        when (page) {
                                            0 -> {
                                                TaskListDetailLayout(
                                                    nodes = nodes,
                                                    selectedNode = selectedNode,
                                                    selectedNodeId = state.selectedNodeId,
                                                    isEditMode = state.isEditMode,
                                                    isAddingTask = state.isAddingTask,
                                                    isProfileMode = state.isProfileMode,
                                                    profiles = profiles,
                                                    activeProfileId = profileId,
                                                    clientType = clientType,
                                                    onNodeEnabledChange = viewModel::onNodeEnabledChange,
                                                    onNodeSelected = viewModel::onNodeSelected,
                                                    onNodeMove = viewModel::onNodeMove,
                                                    onToggleEditMode = viewModel::onToggleEditMode,
                                                    onToggleAddingTask = viewModel::onToggleAddingTask,
                                                    onToggleProfileMode = viewModel::onToggleProfileMode,
                                                    onConfigChange = { config ->
                                                        val nodeId = selectedNode?.id
                                                            ?: return@TaskListDetailLayout
                                                        viewModel.onNodeConfigChange(nodeId, config)
                                                    },
                                                    onAddNode = viewModel::onAddNode,
                                                    onRemoveNode = viewModel::onRemoveNode,
                                                    onDuplicateNode = viewModel::onDuplicateNode,
                                                    onRenameNode = viewModel::onRenameNode,
                                                    onRunFromNode = { nodeId ->
                                                        guardedStart { viewModel.onStartTasksFrom(nodeId) }
                                                    },
                                                    canRunFromNode = !isTaskActive,
                                                    onSwitchProfile = viewModel::onSwitchProfile,
                                                    onRenameProfile = viewModel::onRenameProfile,
                                                    onDuplicateProfile = viewModel::onDuplicateProfile,
                                                    onDeleteProfile = viewModel::onDeleteProfile,
                                                    onCreateProfile = viewModel::onCreateProfile,
                                                    onReorderProfile = viewModel::onReorderProfile,
                                                    modifier = Modifier.fillMaxSize(),
                                                    wrapDetailInCard = true,
                                                    runFromNodeBlocked = startBlocked,
                                                )
                                            }

                                            1 -> AutoBattlePanel(modifier = Modifier.fillMaxSize())
                                            2 -> CompositionLocalProvider(
                                                LocalToolboxFileExporter provides rememberSafToolboxFileExporter()
                                            ) {
                                                ToolboxPanel(modifier = Modifier.fillMaxSize())
                                            }

                                            3 -> {
                                                val runtimeLogs by viewModel.logs.collectWhilePageVisible()
                                                LogPanel(
                                                    logs = runtimeLogs,
                                                    onClearLogs = { viewModel.onClearLogs() },
                                                    onExportLogs = { showLogExportSheet = true },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp), strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        PageBackHandler(enabled = showMoreActions) {
            showMoreActions = false
        }

        if (showMoreActions) {
            val isGameMuted by viewModel.isGameMuted.collectAsStateWithLifecycle()
            val runDeadline by compositionService.runDeadline.collectAsStateWithLifecycle()
            ProvideSettingSearch(pendingSearch, searchNavigator) {
                BackgroundMoreActionsOverlay(
                    onDismissRequest = { showMoreActions = false },
                    isTaskActive = isTaskActive,
                    runDeadline = runDeadline,
                    isGameMuted = isGameMuted,
                    onToggleGameSound = viewModel::onToggleGameSound,
                    onScreenOff = viewModel::onScreenOff,
                    onShowScreenSaver = { coroutineScope.launch { screenSaverManager.show() } },
                    onCaptureScreenshot = viewModel::onCaptureDebugScreenshot,
                    onCloseApp = {
                        if (maaState == MaaExecutionState.RUNNING) {
                            showCloseConfirm = true
                        } else {
                            coroutineScope.launch { compositionService.stopVirtualDisplay() }
                        }
                    },
                )
            }
        }

        // 全屏预览
        if (state.isFullscreenMonitor) {
            val activity = context as? Activity

            DisposableEffect(Unit) {
                val window = activity?.window
                val controller = window?.let {
                    WindowCompat.getInsetsController(it, it.decorView)
                }
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                controller?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                onDispose {
                    controller?.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            DisposableEffect(Unit) {
                val originalOrientation = activity?.requestedOrientation
                onDispose {
                    if (originalOrientation != null) {
                        activity.requestedOrientation = originalOrientation
                    }
                }
            }

            LaunchedEffect(Unit) {
                val current = activity?.resources?.configuration?.orientation
                if (current != Configuration.ORIENTATION_LANDSCAPE) {
                    activity?.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }

            BackHandler { viewModel.onToggleFullscreenMonitor() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        val slots = PreviewPointerSlots()
                        try {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    for (change in event.changes) {
                                        val pointerId = change.id.value
                                        val down = change.changedToDownIgnoreConsumed()
                                        val up = change.changedToUpIgnoreConsumed()
                                        if (!down && !up && !change.positionChangedIgnoreConsumed()) continue

                                        // 画面外按下忽略；拖出画面钳到边缘，保证抬起送达
                                        val point = viewToVirtualDisplay(
                                            change.position,
                                            size,
                                            displayResolution
                                        )
                                        val (x, y) = point.offset
                                        when {
                                            down -> {
                                                if (!point.inside) continue
                                                val contact = slots.acquire(pointerId)
                                                if (contact < 0) continue
                                                viewModel.onTouchDown(x, y, contact)
                                            }

                                            up -> {
                                                val contact = slots.release(pointerId)
                                                if (contact < 0) continue
                                                viewModel.onTouchUp(x, y, contact)
                                            }

                                            else -> {
                                                val contact = slots.indexOf(pointerId)
                                                if (contact < 0) continue
                                                viewModel.onTouchMove(x, y, contact)
                                            }
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        } finally {
                            // 预览退出时仍按着的手指由远端整体 CANCEL
                            viewModel.onTouchCancel()
                        }
                    }, contentAlignment = Alignment.Center
            ) {
                previewContent()

                IconButton(
                    onClick = { viewModel.onToggleFullscreenMonitor() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.task_close_preview_cd),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        val activeDialog = state.dialog ?: copilotDialog ?: toolboxDialog
        val activeDialogCallbacks = when {
            state.dialog != null -> viewModel::onDialogDismiss to viewModel::onDialogConfirm
            copilotDialog != null -> copilotViewModel::onDialogDismiss to copilotViewModel::onDialogConfirm
            toolboxDialog != null -> toolboxViewModel::onDialogDismiss to toolboxViewModel::onDialogConfirm
            else -> null
        }
        activeDialog?.let { dialog ->
            val (onDismiss, onConfirm) = activeDialogCallbacks!!
            PanelPromptDialog(dialog, onDismiss = onDismiss, onConfirm = onConfirm)
        }

        if (showCloseConfirm) {
            AdaptiveTaskPromptDialog(
                visible = true,
                title = stringResource(R.string.dialog_close_app_title),
                message = AnnotatedString(stringResource(R.string.dialog_close_app_message)),
                onDismissRequest = { showCloseConfirm = false },
                onConfirm = {
                    showCloseConfirm = false
                    coroutineScope.launch { compositionService.stopVirtualDisplay() }
                },
                confirmText = stringResource(R.string.dialog_close_app_confirm),
                dismissText = stringResource(R.string.common_cancel),
                icon = Icons.Filled.Warning,
                iconTint = MaterialTheme.colorScheme.error,
                confirmColor = MaterialTheme.colorScheme.error,
            )
        }

    }
}

/** offset 已钳到边缘，inside 取原始落点 */
private class DisplayPoint(val offset: IntOffset, val inside: Boolean)

private fun viewToVirtualDisplay(
    view: Offset,
    viewSize: IntSize,
    display: DefaultDisplayConfig.Resolution,
): DisplayPoint {
    val bufferW = display.width.toFloat()
    val bufferH = display.height.toFloat()
    val scale = minOf(viewSize.width / bufferW, viewSize.height / bufferH)
    val offsetX = (viewSize.width - bufferW * scale) / 2f
    val offsetY = (viewSize.height - bufferH * scale) / 2f
    val vx = ((view.x - offsetX) / scale).toInt()
    val vy = ((view.y - offsetY) / scale).toInt()
    val inside = vx in 0 until display.width && vy in 0 until display.height
    return DisplayPoint(
        IntOffset(vx.coerceIn(0, display.width - 1), vy.coerceIn(0, display.height - 1)),
        inside,
    )
}


@Composable
private fun BackgroundMoreActionsOverlay(
    onDismissRequest: () -> Unit,
    isTaskActive: Boolean,
    runDeadline: Long?,
    isGameMuted: Boolean,
    onToggleGameSound: () -> Unit,
    onScreenOff: () -> Unit,
    onShowScreenSaver: () -> Unit,
    onCaptureScreenshot: () -> Unit,
    onCloseApp: () -> Unit,
    appSettingsManager: AppSettingsManager = koinInject(),
) {
    val coroutineScope = rememberCoroutineScope()
    val muteOnGameLaunch by appSettingsManager.muteOnGameLaunch.collectAsStateWithLifecycle()
    val closeAppOnTaskEnd by appSettingsManager.closeAppOnTaskEnd.collectAsStateWithLifecycle()
    val runDurationLimitEnabled by appSettingsManager.runDurationLimitEnabled.collectAsStateWithLifecycle()
    val runDurationLimitMinutes by appSettingsManager.runDurationLimitMinutes.collectAsStateWithLifecycle()
    val useHardwareScreenOff by appSettingsManager.useHardwareScreenOff.collectAsStateWithLifecycle()
    val showTouchPreview by appSettingsManager.showTouchPreview.collectAsStateWithLifecycle()
    val debugMode by appSettingsManager.debugMode.collectAsStateWithLifecycle()
    var showHardwareScreenOffConfirm by remember { mutableStateOf(false) }
    var showRunDurationInput by remember { mutableStateOf(false) }
    // 非空表示静音确认框待确认，确认后执行；仅静音方向弹，解除不弹
    var pendingMuteAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val overlayInteractionSource = remember { MutableInteractionSource() }
    val cardInteractionSource = remember { MutableInteractionSource() }

    // 同窗口浮层无遮罩，玻璃 surface 会透出下层任务列表
    OpaqueTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = overlayInteractionSource,
                    indication = null,
                    onClick = onDismissRequest
                )
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 64.dp)
                    .clickable(
                        interactionSource = cardInteractionSource, indication = null, onClick = {}),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                CompositionLocalProvider(LocalSettingRowBleed provides ActionsCardPadding) {
                    Column(modifier = Modifier.padding(ActionsCardPadding)) {
                        // 标题与快速操作组
                        Text(
                            text = stringResource(R.string.bg_actions_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ActionTile(
                                icon = Icons.Filled.PowerSettingsNew,
                                label = stringResource(R.string.bg_action_screen_off),
                                onClick = {
                                    if (useHardwareScreenOff) onScreenOff() else onShowScreenSaver()
                                },
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                            ActionTile(
                                icon = Icons.AutoMirrored.Filled.ExitToApp,
                                label = stringResource(R.string.bg_action_close_game),
                                onClick = onCloseApp,
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ActionTile(
                                icon = if (isGameMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                label = if (isGameMuted) stringResource(R.string.bg_action_game_muted)
                                else stringResource(R.string.bg_action_mute_game),
                                onClick = {
                                    if (isGameMuted) onToggleGameSound() else pendingMuteAction = onToggleGameSound
                                },
                                modifier = Modifier.weight(1f),
                                containerColor = if (isGameMuted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                contentColor = if (isGameMuted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 调试模式：截图按钮，保存到 {rootDir}/debug/screenshots
                        if (debugMode) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ActionTile(
                                    icon = Icons.Filled.Screenshot,
                                    label = stringResource(R.string.bg_action_screenshot),
                                    onClick = onCaptureScreenshot,
                                    modifier = Modifier.weight(1f),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.bg_auto_settings_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        SettingSearchTarget(R.string.bg_auto_mute_on_launch) {
                            SettingSwitchRow(
                                icon = Icons.Filled.NotificationsPaused,
                                label = stringResource(R.string.bg_auto_mute_on_launch),
                                checked = muteOnGameLaunch,
                                onCheckedChange = { checked ->
                                    val apply = {
                                        coroutineScope.launch { appSettingsManager.setMuteOnGameLaunch(checked) }
                                        Unit
                                    }
                                    if (checked) pendingMuteAction = apply else apply()
                                })
                        }
                        SettingSearchTarget(R.string.bg_auto_close_on_end) {
                            SettingSwitchRow(
                                icon = Icons.Filled.Cancel,
                                label = stringResource(R.string.bg_auto_close_on_end),
                                checked = closeAppOnTaskEnd,
                                onCheckedChange = {
                                    coroutineScope.launch { appSettingsManager.setCloseAppOnTaskEnd(it) }
                                })
                        }
                        // 本轮已按开始时的设置计时，运行中禁改
                        SettingSearchTarget(R.string.bg_auto_run_duration_limit) {
                            SettingSwitchRow(
                                icon = Icons.Filled.Timer,
                                label = stringResource(R.string.bg_auto_run_duration_limit),
                                checked = runDurationLimitEnabled,
                                enabled = !isTaskActive,
                                onCheckedChange = {
                                    coroutineScope.launch { appSettingsManager.setRunDurationLimitEnabled(it) }
                                })
                        }
                        if (runDeadline != null) {
                            RunDurationRemainingRow(deadline = runDeadline)
                        } else if (runDurationLimitEnabled) {
                            RunDurationStepperRow(
                                minutes = runDurationLimitMinutes,
                                enabled = !isTaskActive,
                                onMinutesChange = {
                                    coroutineScope.launch { appSettingsManager.setRunDurationLimitMinutes(it) }
                                },
                                onEditClick = { showRunDurationInput = true })
                        }
                        SettingSearchTarget(R.string.bg_auto_hardware_screen_off) {
                            SettingSwitchRow(
                                icon = Icons.Filled.StayCurrentPortrait,
                                label = stringResource(R.string.bg_auto_hardware_screen_off),
                                checked = useHardwareScreenOff,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        showHardwareScreenOffConfirm = true
                                    } else {
                                        coroutineScope.launch {
                                            appSettingsManager.setUseHardwareScreenOff(
                                                false
                                            )
                                        }
                                    }
                                })
                        }
                        SettingSearchTarget(R.string.bg_auto_show_touch_preview) {
                            SettingSwitchRow(
                                icon = Icons.Filled.TouchApp,
                                label = stringResource(R.string.bg_auto_show_touch_preview),
                                checked = showTouchPreview,
                                onCheckedChange = {
                                    coroutineScope.launch { appSettingsManager.setShowTouchPreview(it) }
                                })
                        }
                    }
                }
            }
        }
    }

    pendingMuteAction?.let { action ->
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.dialog_mute_game_title),
            message = AnnotatedString(stringResource(R.string.dialog_mute_game_message)),
            onDismissRequest = { pendingMuteAction = null },
            onConfirm = {
                pendingMuteAction = null
                action()
            },
            confirmText = stringResource(R.string.dialog_mute_game_confirm),
            dismissText = stringResource(R.string.common_cancel),
            icon = Icons.AutoMirrored.Filled.VolumeOff,
            iconTint = MaterialTheme.colorScheme.primary,
            confirmColor = MaterialTheme.colorScheme.primary,
        )
    }

    // 任务开始即收起，结束后不再冒出来
    LaunchedEffect(isTaskActive) {
        if (isTaskActive) showRunDurationInput = false
    }
    if (showRunDurationInput && !isTaskActive) {
        RunDurationInputDialog(
            minutes = runDurationLimitMinutes,
            onDismissRequest = { showRunDurationInput = false },
            onConfirm = { minutes ->
                showRunDurationInput = false
                coroutineScope.launch { appSettingsManager.setRunDurationLimitMinutes(minutes) }
            },
        )
    }

    if (showHardwareScreenOffConfirm) {
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.dialog_hardware_screen_off_title),
            message = AnnotatedString(stringResource(R.string.dialog_hardware_screen_off_message)),
            onDismissRequest = { showHardwareScreenOffConfirm = false },
            onConfirm = {
                showHardwareScreenOffConfirm = false
                coroutineScope.launch { appSettingsManager.setUseHardwareScreenOff(true) }
            },
            confirmText = stringResource(R.string.dialog_hardware_screen_off_confirm),
            dismissText = stringResource(R.string.common_cancel),
            icon = Icons.Filled.PowerSettingsNew,
            iconTint = MaterialTheme.colorScheme.primary,
            confirmColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(4.dp),
        color = containerColor.copy(alpha = 0.08f),
        contentColor = contentColor,
        border = BorderStroke(0.5.dp, containerColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = containerColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val bleed = LocalSettingRowBleed.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .horizontalBleed(bleed)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = bleed),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = MaaThemeAlphas.DISABLED)
            },
            modifier = Modifier.weight(1f)
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// 开关行按它外扩，涟漪铺满卡片
private val ActionsCardPadding = 10.dp

/** 与开关行文字对齐的缩进行 */
@Composable
private fun RunDurationSubRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(start = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RunDurationStepperRow(
    minutes: Int,
    enabled: Boolean,
    onMinutesChange: (Int) -> Unit,
    onEditClick: () -> Unit,
) {
    RunDurationSubRow {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    enabled = enabled,
                    onClickLabel = stringResource(R.string.bg_auto_run_duration_limit_edit),
                    onClick = onEditClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val valueColor = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MaaThemeAlphas.DISABLED)
            }
            Text(
                text = stringResource(R.string.bg_auto_run_duration_limit_value, minutes),
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = valueColor,
            )
        }
        IconButton(
            onClick = { onMinutesChange(RunDurationStep.decrease(minutes)) },
            enabled = enabled && RunDurationStep.canDecrease(minutes),
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.bg_auto_run_duration_limit_decrease),
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(
            onClick = { onMinutesChange(RunDurationStep.increase(minutes)) },
            enabled = enabled && RunDurationStep.canIncrease(minutes),
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.bg_auto_run_duration_limit_increase),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RunDurationInputDialog(
    minutes: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var input by remember {
        val text = minutes.toString()
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val submit = {
        val parsed = RunDurationStep.parseInput(input.text)
        if (parsed == null) onDismissRequest() else onConfirm(parsed)
    }
    AdaptiveTaskPromptDialog(
        visible = true,
        title = stringResource(R.string.bg_auto_run_duration_limit_input_title),
        onDismissRequest = onDismissRequest,
        onConfirm = submit,
        icon = Icons.Filled.Timer,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { if (RunDurationStep.acceptsInput(it.text)) input = it },
            label = { Text(stringResource(R.string.bg_auto_run_duration_limit_input_label)) },
            supportingText = {
                Text(
                    stringResource(
                        R.string.bg_auto_run_duration_limit_input_hint,
                        RunDurationLimit.MIN_MINUTES,
                        RunDurationLimit.MAX_MINUTES,
                    )
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun RunDurationRemainingRow(deadline: Long) {
    val remainingMs by produceState(deadline - SystemClock.elapsedRealtime(), deadline) {
        while (true) {
            value = deadline - SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    RunDurationSubRow {
        Text(
            text = stringResource(
                R.string.bg_auto_run_duration_limit_remaining,
                RunDurationStep.formatRemaining(remainingMs),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
