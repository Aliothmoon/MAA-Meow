package com.aliothmoon.maameow.presentation.view.background

import android.app.Activity
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer

import android.graphics.PixelFormat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.aspectRatio
import android.widget.Toast
import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.movableContentOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.UnifiedStateDispatcher
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.ShizukuPermissionDialog
import com.aliothmoon.maameow.presentation.view.panel.PanelHeader
import com.aliothmoon.maameow.presentation.view.panel.LogPanel
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import com.aliothmoon.maameow.presentation.view.panel.TaskConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.TaskListPanel
import com.aliothmoon.maameow.presentation.view.panel.AutoBattlePanel
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.presentation.viewmodel.CopilotViewModel
import com.aliothmoon.maameow.presentation.viewmodel.ToolboxViewModel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import com.aliothmoon.maameow.domain.service.AppWatchdog
import com.aliothmoon.maameow.overlay.screensaver.ScreenSaverOverlayManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber
import androidx.compose.ui.res.stringResource
import com.aliothmoon.maameow.R
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.runtime.saveable.rememberSaveable
import com.aliothmoon.maameow.presentation.view.panel.ToolboxPanel

@Composable
fun BackgroundTaskView(
    onFullscreenChanged: (Boolean) -> Unit = {},
    viewModel: BackgroundTaskViewModel,
    copilotViewModel: CopilotViewModel = koinInject(),
    toolboxViewModel: ToolboxViewModel = koinInject(),
    compositionService: MaaCompositionService = koinInject(),
    dispatcher: UnifiedStateDispatcher = koinInject(),
    permissionManager: PermissionManager = koinInject(),
    screenSaverManager: ScreenSaverOverlayManager = koinInject(),
    appWatchdog: AppWatchdog = koinInject(),
) {

    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maaState by compositionService.state.collectAsStateWithLifecycle()
    val markers by viewModel.markers.collectAsStateWithLifecycle()
    val displayResolution by compositionService.displayResolution.collectAsStateWithLifecycle()
    val permissions by permissionManager.state.collectAsStateWithLifecycle()
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
    val activeProfileId by viewModel.chainState.activeProfileId.collectAsStateWithLifecycle()
    val selectedNode = nodes.find { it.id == state.selectedNodeId }
    val canShowTaskActions = PanelTab.canShowTaskActions(state.current)

    val pagerState = rememberPagerState(
        initialPage = state.current.ordinal,
        pageCount = { PanelTab.entries.size }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val newTab = PanelTab.entries[page]
            if (newTab != state.current) {
                viewModel.onTabChange(newTab)
            }
        }
    }

    LaunchedEffect(state.current) {
        if (pagerState.currentPage != state.current.ordinal) {
            pagerState.scrollToPage(state.current.ordinal)
        }
    }
    val context = LocalContext.current

    if (!permissions.remoteAccessGranted) {
        var isRequestingRemoteAccess by remember { mutableStateOf(false) }
        ShizukuPermissionDialog(
            title = stringResource(R.string.needs_permission_title, permissions.startupBackend.display),
            message = stringResource(R.string.needs_permission_msg, permissions.startupBackend.display),
            isRequesting = isRequestingRemoteAccess,
            onConfirm = {
                if (isRequestingRemoteAccess) return@ShizukuPermissionDialog
                coroutineScope.launch {
                    isRequestingRemoteAccess = true
                    val granted = permissionManager.requestRemoteAccess()
                    isRequestingRemoteAccess = false
                    if (!granted) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.permission_not_acquired_retry, permissions.remotePermissionLabel),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
    }


    LaunchedEffect(state.isFullscreenMonitor) {
        onFullscreenChanged(state.isFullscreenMonitor)
    }

    val pendingExecution by viewModel.coordinator.pendingExecution.collectAsStateWithLifecycle()

    LaunchedEffect(pendingExecution?.requestId) {
        pendingExecution?.let { request ->
            viewModel.onScheduledExecutionPageReady(request.requestId)
        }
    }

    LaunchedEffect(Unit) {
        dispatcher.serviceDiedEvent.collect {
            Toast.makeText(context, context.getString(R.string.maa_service_crashed), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        appWatchdog.appDiedEvent.collect {
            Toast.makeText(context, context.getString(R.string.game_process_died), Toast.LENGTH_SHORT).show()
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
                                            holder.setFixedSize(
                                                DefaultDisplayConfig.WIDTH,
                                                DefaultDisplayConfig.HEIGHT
                                            )
                                        }
                                    }

                                    override fun surfaceChanged(
                                        holder: SurfaceHolder, format: Int, width: Int, height: Int
                                    ) {
                                        Timber.d("Surface size changed to $width x $height")
                                        if (width == DefaultDisplayConfig.WIDTH && height == DefaultDisplayConfig.HEIGHT) {
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
                        modifier = Modifier.fillMaxSize()
                    )
                    if (markers.isNotEmpty()) TouchPreviewOverlay(
                        markers = markers,
                        displayResolution = displayResolution,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    val fullscreenProgress = remember { Animatable(0f) }
    val currentOrientation = LocalConfiguration.current.orientation
    val isLandscapeReady = currentOrientation == Configuration.ORIENTATION_LANDSCAPE
    var fullscreenAnimTarget by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.isFullscreenMonitor, isLandscapeReady) {
        fullscreenAnimTarget = if (state.isFullscreenMonitor && isLandscapeReady) 1f else 0f
    }

    LaunchedEffect(fullscreenAnimTarget) {
        if (fullscreenAnimTarget == 0f) {
            fullscreenProgress.snapTo(0f)
        } else {
            fullscreenProgress.snapTo(0f)
            fullscreenProgress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            // --- 预览图区域：实时加载 ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(3f)
            ) {
                if (!state.isFullscreenMonitor) {
                    VirtualDisplayPreview(
                        modifier = Modifier.fillMaxSize(),
                        isRunning = maaState == MaaExecutionState.RUNNING,
                        isSurfaceAvailable = isSurfaceAvailable,
                        onClick = { viewModel.onToggleFullscreenMonitor() }
                    ) {
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
                    showActions = false
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
                                    .weight(1f),
                                userScrollEnabled = true,
                                beyondViewportPageCount = 0
                            ) { page ->
                                when (page) {
                                    0 -> {
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            TaskListPanel(
                                                nodes = nodes,
                                                selectedNodeId = state.selectedNodeId,
                                                isEditMode = state.isEditMode,
                                                isAddingTask = state.isAddingTask,
                                                isProfileMode = state.isProfileMode,
                                                onNodeEnabledChange = viewModel::onNodeEnabledChange,
                                                onNodeSelected = viewModel::onNodeSelected,
                                                onNodeMove = viewModel::onNodeMove,
                                                onToggleEditMode = viewModel::onToggleEditMode,
                                                onToggleAddingTask = viewModel::onToggleAddingTask,
                                                onToggleProfileMode = viewModel::onToggleProfileMode,
                                                modifier = Modifier.fillMaxHeight(),
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                )
                                            ) {
                                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                                    TaskConfigPanel(
                                                        selectedNode = selectedNode,
                                                        isEditMode = state.isEditMode,
                                                        isAddingTask = state.isAddingTask,
                                                        isProfileMode = state.isProfileMode,
                                                        profiles = profiles,
                                                        activeProfileId = activeProfileId,
                                                        onConfigChange = { config ->
                                                            val nodeId =
                                                                selectedNode?.id
                                                                    ?: return@TaskConfigPanel
                                                            viewModel.onNodeConfigChange(
                                                                nodeId,
                                                                config
                                                            )
                                                        },
                                                        onAddNode = { viewModel.onAddNode(it) },
                                                        onRemoveNode = { viewModel.onRemoveNode(it) },
                                                        onRenameNode = { id, name ->
                                                            viewModel.onRenameNode(
                                                                id,
                                                                name
                                                            )
                                                        },
                                                        onSwitchProfile = {
                                                            viewModel.onSwitchProfile(
                                                                it
                                                            )
                                                        },
                                                        onRenameProfile = { id, name ->
                                                            viewModel.onRenameProfile(
                                                                id,
                                                                name
                                                            )
                                                        },
                                                        onDuplicateProfile = {
                                                            viewModel.onDuplicateProfile(
                                                                it
                                                            )
                                                        },
                                                        onDeleteProfile = {
                                                            viewModel.onDeleteProfile(
                                                                it
                                                            )
                                                        },
                                                        onCreateProfile = { viewModel.onCreateProfile() }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    1 -> AutoBattlePanel(modifier = Modifier.fillMaxSize())
                                    2 -> ToolboxPanel(modifier = Modifier.fillMaxSize())
                                    3 -> {
                                        val runtimeLogs by viewModel.logs.collectAsStateWithLifecycle()
                                        LogPanel(
                                            logs = runtimeLogs,
                                            onClearLogs = { viewModel.onClearLogs() },
                                            onClose = { viewModel.onTabChange(PanelTab.TASKS) })
                                    }
                                }
                            }

                            if (canShowTaskActions) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val focusManager = LocalFocusManager.current
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            focusManager.clearFocus()
                                            when (state.current) {
                                                PanelTab.TASKS -> viewModel.onStartTasks()
                                                PanelTab.AUTO_BATTLE -> copilotViewModel.onStart()
                                                PanelTab.TOOLS -> toolboxViewModel.onStart()
                                                else -> {}
                                            }
                                        },
                                        enabled = maaState != MaaExecutionState.RUNNING && maaState != MaaExecutionState.STARTING && maaState != MaaExecutionState.STOPPING,
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
                                            Text(stringResource(R.string.start_task))
                                        }
                                    }

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
                                            Text(stringResource(R.string.stop_task))
                                        }
                                    }

                                    IconButton(
                                        onClick = { showMoreActions = !showMoreActions },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = stringResource(R.string.more_actions)
                                        )
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
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
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
                onShowScreenSaver = { screenSaverManager.show(context as? Activity) },
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
                    .graphicsLayer {
                        alpha = fullscreenProgress.value
                    }
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                viewToVirtualDisplay(
                                    viewX = change.position.x,
                                    viewY = change.position.y,
                                    viewWidth = size.width,
                                    viewHeight = size.height,
                                    bufferWidth = displayResolution.width,
                                    bufferHeight = displayResolution.height
                                ) { vx, vy ->
                                    when (event.type) {
                                        PointerEventType.Press -> viewModel.onTouchDown(vx, vy)
                                        PointerEventType.Move -> {
                                            if (change.pressed) {
                                                viewModel.onTouchMove(vx, vy)
                                            }
                                        }
                                        PointerEventType.Release -> viewModel.onTouchUp(vx, vy)
                                    }
                                }
                                change.consume()
                            }
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
                        contentDescription = stringResource(R.string.close),
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
            AdaptiveTaskPromptDialog(
                visible = true,
                title = dialog.title,
                message = AnnotatedString(dialog.message),
                onDismissRequest = onDismiss,
                onConfirm = onConfirm,
                confirmText = dialog.confirmText,
                dismissText = dialog.dismissText,
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
                title = stringResource(R.string.confirm_close_title),
                message = AnnotatedString(stringResource(R.string.confirm_close_msg)),
                onDismissRequest = { showCloseConfirm = false },
                onConfirm = {
                    showCloseConfirm = false
                    coroutineScope.launch { compositionService.stopVirtualDisplay() }
                },
                confirmText = stringResource(R.string.confirm_close_title),
                dismissText = stringResource(R.string.cancel),
                icon = Icons.Filled.Warning,
                iconTint = MaterialTheme.colorScheme.error,
                confirmColor = MaterialTheme.colorScheme.error,
            )
        }

    }
}

private inline fun viewToVirtualDisplay(
    viewX: Float,
    viewY: Float,
    viewWidth: Int,
    viewHeight: Int,
    bufferWidth: Int,
    bufferHeight: Int,
    block: (vx: Int, vy: Int) -> Unit,
) {
    val bufferW = bufferWidth.toFloat()
    val bufferH = bufferHeight.toFloat()
    val scale = minOf(viewWidth / bufferW, viewHeight / bufferH)
    val offsetX = (viewWidth - bufferW * scale) / 2f
    val offsetY = (viewHeight - bufferH * scale) / 2f
    val vx = ((viewX - offsetX) / scale).toInt()
    val vy = ((viewY - offsetY) / scale).toInt()
    if (vx < 0 || vx >= bufferW.toInt() || vy < 0 || vy >= bufferH.toInt()) return
    block(vx, vy)
}


@Composable
private fun BackgroundMoreActionsOverlay(
    onDismissRequest: () -> Unit,
    isGameMuted: Boolean,
    onToggleGameSound: () -> Unit,
    onScreenOff: () -> Unit,
    onShowScreenSaver: () -> Unit,
    onCloseApp: () -> Unit,
    appSettingsManager: AppSettingsManager = koinInject(),
) {
    val coroutineScope = rememberCoroutineScope()
    val muteOnGameLaunch by appSettingsManager.muteOnGameLaunch.collectAsStateWithLifecycle()
    val closeAppOnTaskEnd by appSettingsManager.closeAppOnTaskEnd.collectAsStateWithLifecycle()
    val useHardwareScreenOff by appSettingsManager.useHardwareScreenOff.collectAsStateWithLifecycle()
    val showTouchPreview by appSettingsManager.showTouchPreview.collectAsStateWithLifecycle()
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
                    interactionSource = cardInteractionSource,
                    indication = null,
                    onClick = {}
                ),
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
                        text = stringResource(R.string.quick_actions),
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
                            label = stringResource(R.string.screen_off_idle),
                            onClick = {
                                if (useHardwareScreenOff) onScreenOff() else onShowScreenSaver()
                            },
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                        ActionTile(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            label = stringResource(R.string.close_game),
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
                            label = if (isGameMuted) stringResource(R.string.game_muted) else stringResource(R.string.mute_game_sound),
                            onClick = onToggleGameSound,
                            modifier = Modifier.weight(1f),
                            containerColor = if (isGameMuted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                            contentColor = if (isGameMuted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.auto_settings),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    SettingSwitchRow(
                        icon = Icons.Filled.NotificationsPaused,
                        label = stringResource(R.string.mute_on_game_launch),
                        checked = muteOnGameLaunch,
                        onCheckedChange = {
                            coroutineScope.launch { appSettingsManager.setMuteOnGameLaunch(it) }
                        }
                    )
                    SettingSwitchRow(
                        icon = Icons.Filled.Cancel,
                        label = stringResource(R.string.close_game_on_task_end),
                        checked = closeAppOnTaskEnd,
                        onCheckedChange = {
                            coroutineScope.launch { appSettingsManager.setCloseAppOnTaskEnd(it) }
                        }
                    )
                    SettingSwitchRow(
                        icon = Icons.Filled.StayCurrentPortrait,
                        label = stringResource(R.string.screen_off_on_idle),
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
                        }
                    )
                    SettingSwitchRow(
                        icon = Icons.Filled.TouchApp,
                        label = stringResource(R.string.show_touch_preview),
                        checked = showTouchPreview,
                        onCheckedChange = {
                            coroutineScope.launch { appSettingsManager.setShowTouchPreview(it) }
                        }
                    )
                }
            }
        }

    if (showHardwareScreenOffConfirm) {
        AdaptiveTaskPromptDialog(
            visible = true,
            title = stringResource(R.string.confirm_hardware_screen_off_title),
            message = AnnotatedString(stringResource(R.string.confirm_hardware_screen_off_msg)),
            onDismissRequest = { showHardwareScreenOffConfirm = false },
            onConfirm = {
                showHardwareScreenOffConfirm = false
                coroutineScope.launch { appSettingsManager.setUseHardwareScreenOff(true) }
            },
            confirmText = stringResource(R.string.confirm),
            dismissText = stringResource(R.string.cancel),
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
    onCheckedChange: (Boolean) -> Unit
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
