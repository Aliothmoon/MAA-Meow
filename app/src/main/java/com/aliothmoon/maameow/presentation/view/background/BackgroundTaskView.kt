package com.aliothmoon.maameow.presentation.view.background

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.StayCurrentPortrait
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.AppWatchdog
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.UnifiedStateDispatcher
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.overlay.screensaver.ScreenSaverOverlayManager
import com.aliothmoon.maameow.presentation.LocalInputFocusManager
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.MaaWindowInsets
import com.aliothmoon.maameow.presentation.components.ShizukuReadinessGate
import com.aliothmoon.maameow.presentation.navigation.BottomNavTab
import com.aliothmoon.maameow.presentation.onboarding.LocalOnboardingState
import com.aliothmoon.maameow.presentation.onboarding.OnboardingTarget
import com.aliothmoon.maameow.presentation.onboarding.onboardingBlocksStartupDialogs
import com.aliothmoon.maameow.presentation.onboarding.onboardingTarget
import com.aliothmoon.maameow.presentation.pip.LocalIsInPip
import com.aliothmoon.maameow.presentation.pip.PipController
import com.aliothmoon.maameow.presentation.pip.PipHost
import com.aliothmoon.maameow.presentation.pip.PipRequest
import com.aliothmoon.maameow.presentation.state.PreviewPointerSlots
import com.aliothmoon.maameow.presentation.view.panel.AutoBattlePanel
import com.aliothmoon.maameow.presentation.view.panel.LocalToolboxFileExporter
import com.aliothmoon.maameow.presentation.view.panel.LogPanel
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
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
import com.aliothmoon.maameow.utils.i18n.asString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber
import kotlin.math.roundToInt

@Composable
fun BackgroundTaskView(
    viewModel: BackgroundTaskViewModel,
    /** 子页面盖上来时 MainScreen 用 alpha 0 保活组合树，本页仍在组合但看不见，此时不能开画中画 */
    isActivePage: Boolean = true,
    copilotViewModel: CopilotViewModel = koinInject(),
    toolboxViewModel: ToolboxViewModel = koinInject(),
    compositionService: MaaCompositionService = koinInject(),
    dispatcher: UnifiedStateDispatcher = koinInject(),
    screenSaverManager: ScreenSaverOverlayManager = koinInject(),
    appWatchdog: AppWatchdog = koinInject(),
    appSettingsManager: AppSettingsManager = koinInject(),
    permissionManager: PermissionManager = koinInject(),
) {

    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maaState by compositionService.state.collectAsStateWithLifecycle()
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
    val appDiedMessage = stringResource(R.string.bg_toast_app_died)
    val displayDriftMessage = stringResource(R.string.bg_toast_display_drift)

    // pageReady 栅栏已移除：启动由 LaunchPipeline 驱动，无需等本页 Surface

    LaunchedEffect(Unit) {
        dispatcher.serviceDiedEvent.collect {
            Toast.makeText(
                context, serviceDiedMessage, Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        appWatchdog.appDiedEvent.collect {
            Toast.makeText(
                context, appDiedMessage, Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        appWatchdog.displayDriftEvent.collect {
            Toast.makeText(
                context, displayDriftMessage, Toast.LENGTH_LONG
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

    val pipHost = context as? PipHost
    val isInPip = LocalIsInPip.current

    // 竞态兜底：pipEligible 已排除全屏态，但 setPictureInPictureParams 要跨进程生效，
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
                        }, modifier = Modifier.fillMaxSize()
                    )
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

    // 全屏预览态排除在外：auto-enter 没有前置钩子，带着强制横屏和收起的系统栏进小窗会互相打架
    val pipEligible = pipOnHome &&
            isActivePage &&
            !state.isFullscreenMonitor &&
            runMode == RunMode.BACKGROUND &&
            (maaState == MaaExecutionState.STARTING || maaState == MaaExecutionState.RUNNING) &&
            isSurfaceAvailable &&
            PipController.isSupported(context)
    val pipActivity = pipHost as? Activity
    DisposableEffect(pipHost, pipActivity, pipEligible, displayResolution, previewBounds) {
        fun arm(enabled: Boolean, sourceRect: Rect?) {
            val host = pipHost ?: return
            val activity = pipActivity ?: return
            val request = PipRequest(displayResolution, sourceRect)
            host.pipRequest = if (enabled) request else null
            PipController.updateParams(activity, enabled, request)
        }
        arm(pipEligible, previewBounds)
        onDispose { arm(enabled = false, sourceRect = null) }
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

    // 放在画中画分支之后，小窗里不该弹准备度引导；首启引导期间也让路
    if (!onboardingBlocksStartupDialogs()) {
        ShizukuReadinessGate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(MaaWindowInsets.topBar)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            // --- 预览图区域：实时加载 ---
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

            // --- 业务内容区域：阶梯加载 ---
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
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .onboardingTarget(OnboardingTarget.BG_TASK_LIST),
                            userScrollEnabled = true,
                            beyondViewportPageCount = 0
                        ) { page ->
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
                                        onSwitchProfile = viewModel::onSwitchProfile,
                                        onRenameProfile = viewModel::onRenameProfile,
                                        onDuplicateProfile = viewModel::onDuplicateProfile,
                                        onDeleteProfile = viewModel::onDeleteProfile,
                                        onCreateProfile = viewModel::onCreateProfile,
                                        onReorderProfile = viewModel::onReorderProfile,
                                        modifier = Modifier.fillMaxSize(),
                                        wrapDetailInCard = true,
                                    )
                                }

                                1 -> AutoBattlePanel(modifier = Modifier.fillMaxSize())
                                2 -> CompositionLocalProvider(
                                    LocalToolboxFileExporter provides rememberSafToolboxFileExporter()
                                ) {
                                    ToolboxPanel(modifier = Modifier.fillMaxSize())
                                }

                                3 -> {
                                    val runtimeLogs by viewModel.logs.collectAsStateWithLifecycle()
                                    LogPanel(
                                        logs = runtimeLogs,
                                        onClearLogs = { viewModel.onClearLogs() },
                                    )
                                }
                            }
                        }

                        if (canShowTaskActions) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val inputFocusManager = LocalInputFocusManager.current
                            val toolboxTab by toolboxViewModel.currentTab.collectAsStateWithLifecycle()
                            val gachaDisclaimerOk by
                            toolboxViewModel.gachaDisclaimerAccepted.collectAsStateWithLifecycle()
                            // 牛牛抽卡：底部栏改为寻访一次/十次，避免与面板内按钮 + 开始任务重复
                            val isGachaActions = state.current == PanelTab.TOOLS &&
                                    toolboxTab == ToolboxTab.GACHA &&
                                    gachaDisclaimerOk
                            // 未同意免责时隐藏开始栏（只在内容区点「知道了」）
                            val hideStartBarForGachaDisclaimer = state.current == PanelTab.TOOLS &&
                                    toolboxTab == ToolboxTab.GACHA &&
                                    !gachaDisclaimerOk
                            // 启动按钮的两种「禁用态」：① 前台模式不从后台任务页启动；
                            // ② 远程后端（Shizuku/Root）不可用。两者均显示为禁用态但仍可点击，
                            // 点击给出对应提示（防呆），与领域层 checkPreconditions 守卫一致。
                            val foregroundBlocked = runMode == RunMode.FOREGROUND
                            val backendBlocked =
                                !permissionState.isStartupBackendAvailable(permissionState.startupBackend)
                            val startBlocked = foregroundBlocked || backendBlocked
                            val switchBackgroundModeMessage =
                                stringResource(R.string.navigation_toast_switch_background_mode)
                            val backendUnavailableMessage = stringResource(
                                R.string.home_toast_backend_unavailable,
                                permissionState.startupBackend.display
                            )
                            val canStart = maaState != MaaExecutionState.RUNNING &&
                                    maaState != MaaExecutionState.STARTING &&
                                    maaState != MaaExecutionState.STOPPING
                            if (!hideStartBarForGachaDisclaimer) {
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
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        color = MaterialTheme.colorScheme.error,
                                                        strokeWidth = 2.dp,
                                                    )
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
                                                    inputFocusManager.clear()
                                                    if (foregroundBlocked) {
                                                        Toast.makeText(
                                                            context,
                                                            switchBackgroundModeMessage,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                        return@Button
                                                    }
                                                    if (backendBlocked) {
                                                        Toast.makeText(
                                                            context,
                                                            backendUnavailableMessage,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                        return@Button
                                                    }
                                                    toolboxViewModel.onStartGacha(once = true)
                                                },
                                                enabled = canStart,
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
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        strokeWidth = 2.dp,
                                                    )
                                                } else {
                                                    Text(
                                                        text = stringResource(R.string.gacha_once),
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    inputFocusManager.clear()
                                                    if (foregroundBlocked) {
                                                        Toast.makeText(
                                                            context,
                                                            switchBackgroundModeMessage,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                        return@OutlinedButton
                                                    }
                                                    if (backendBlocked) {
                                                        Toast.makeText(
                                                            context,
                                                            backendUnavailableMessage,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                        return@OutlinedButton
                                                    }
                                                    toolboxViewModel.onStartGacha(once = false)
                                                },
                                                enabled = canStart,
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
                                        if (taskRunning) {
                                            OutlinedButton(
                                                onClick = {
                                                    when (state.current) {
                                                        PanelTab.TASKS -> viewModel.onStopTasks()
                                                        PanelTab.AUTO_BATTLE -> copilotViewModel.onStop()
                                                        PanelTab.TOOLS -> toolboxViewModel.onStop()
                                                        else -> {}
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
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        color = MaterialTheme.colorScheme.error,
                                                        strokeWidth = 2.dp
                                                    )
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
                                                    inputFocusManager.clear()
                                                    if (foregroundBlocked) {
                                                        Toast.makeText(
                                                            context,
                                                            switchBackgroundModeMessage,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        return@Button
                                                    }
                                                    if (backendBlocked) {
                                                        Toast.makeText(
                                                            context,
                                                            backendUnavailableMessage,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        return@Button
                                                    }
                                                    when (state.current) {
                                                        PanelTab.TASKS -> viewModel.onStartTasks()
                                                        PanelTab.AUTO_BATTLE -> copilotViewModel.onStart()
                                                        PanelTab.TOOLS -> toolboxViewModel.onStart()
                                                        else -> {}
                                                    }
                                                },
                                                enabled = canStart,
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
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        strokeWidth = 2.dp
                                                    )
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
                        }
                    }
                } else {
                    // 初始化中的骨架占位
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

        BackHandler(enabled = showMoreActions) {
            showMoreActions = false
        }

        if (showMoreActions) {
            val isGameMuted by viewModel.isGameMuted.collectAsStateWithLifecycle()
            BackgroundMoreActionsOverlay(
                onDismissRequest = { showMoreActions = false },
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
            val confirmColor = when (dialog.type) {
                PanelDialogType.SUCCESS -> MaterialTheme.colorScheme.primary
                PanelDialogType.WARNING -> MaterialTheme.colorScheme.tertiary
                PanelDialogType.ERROR -> MaterialTheme.colorScheme.error
            }
            val dialogTitle = dialog.title.asString()
            val dialogMessage = dialog.message.asString()
            val dialogConfirmText = dialog.confirmText.asString()
            val dialogDismissText = dialog.dismissText.asString()
            AdaptiveTaskPromptDialog(
                visible = true,
                title = dialogTitle,
                message = AnnotatedString(dialogMessage),
                onDismissRequest = onDismiss,
                onConfirm = onConfirm,
                confirmText = dialogConfirmText.ifBlank {
                    stringResource(R.string.common_confirm)
                },
                dismissText = dialogDismissText.ifBlank {
                    stringResource(R.string.common_close)
                },
                icon = when (dialog.type) {
                    PanelDialogType.SUCCESS -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.Warning
                },
                iconTint = confirmColor,
                confirmColor = confirmColor,
            )
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
    val useHardwareScreenOff by appSettingsManager.useHardwareScreenOff.collectAsStateWithLifecycle()
    val showTouchPreview by appSettingsManager.showTouchPreview.collectAsStateWithLifecycle()
    val debugMode by appSettingsManager.debugMode.collectAsStateWithLifecycle()
    var showHardwareScreenOffConfirm by remember { mutableStateOf(false) }

    val overlayInteractionSource = remember { MutableInteractionSource() }
    val cardInteractionSource = remember { MutableInteractionSource() }

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
            Column(modifier = Modifier.padding(10.dp)) {
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
                        onClick = onToggleGameSound,
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

                SettingSwitchRow(
                    icon = Icons.Filled.NotificationsPaused,
                    label = stringResource(R.string.bg_auto_mute_on_launch),
                    checked = muteOnGameLaunch,
                    onCheckedChange = {
                        coroutineScope.launch { appSettingsManager.setMuteOnGameLaunch(it) }
                    })
                SettingSwitchRow(
                    icon = Icons.Filled.Cancel,
                    label = stringResource(R.string.bg_auto_close_on_end),
                    checked = closeAppOnTaskEnd,
                    onCheckedChange = {
                        coroutineScope.launch { appSettingsManager.setCloseAppOnTaskEnd(it) }
                    })
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
            confirmText = stringResource(R.string.common_confirm),
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
    icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable { onCheckedChange(!checked) },
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
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
