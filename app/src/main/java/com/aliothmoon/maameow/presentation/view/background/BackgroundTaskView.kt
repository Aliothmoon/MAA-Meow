package com.aliothmoon.maameow.presentation.view.background

import android.app.Activity
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.aliothmoon.maameow.presentation.components.PlaceholderContent
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
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import timber.log.Timber

@Composable
fun BackgroundTaskView(
    onFullscreenChanged: (Boolean) -> Unit = {},
    viewModel: BackgroundTaskViewModel = koinViewModel(),
    copilotViewModel: CopilotViewModel = koinInject(),
    compositionService: MaaCompositionService = koinInject(),
    dispatcher: UnifiedStateDispatcher = koinInject(),
    permissionManager: PermissionManager = koinInject(),
    appSettingsManager: AppSettingsManager = koinInject()
) {
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maaState by compositionService.state.collectAsStateWithLifecycle()
    val permissions by permissionManager.state.collectAsStateWithLifecycle()
    var isRequestingShizuku by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }

    val nodes by viewModel.chainState.chain.collectAsStateWithLifecycle()
    val selectedNode = nodes.find { it.id == state.selectedNodeId }

    val pagerState = rememberPagerState(
        initialPage = state.currentTab.ordinal,
        pageCount = { PanelTab.entries.size }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val newTab = PanelTab.entries[page]
            if (newTab != state.currentTab) {
                viewModel.onTabChange(newTab)
            }
        }
    }

    LaunchedEffect(state.currentTab) {
        if (pagerState.currentPage != state.currentTab.ordinal) {
            pagerState.scrollToPage(state.currentTab.ordinal)
        }
    }
    val context = LocalContext.current

    if (!permissions.shizuku) {
        ShizukuPermissionDialog(
            isRequesting = isRequestingShizuku,
            onConfirm = {
                if (isRequestingShizuku) return@ShizukuPermissionDialog
                coroutineScope.launch {
                    isRequestingShizuku = true
                    val granted = permissionManager.requestShizuku()
                    isRequestingShizuku = false
                    if (!granted) {
                        Toast.makeText(context, "Shizuku 权限未获取，请重试", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        )
    }


    LaunchedEffect(state.isFullscreenMonitor) {
        onFullscreenChanged(state.isFullscreenMonitor)
    }

    LaunchedEffect(Unit) {
        dispatcher.serviceDiedEvent.collect {
            Toast.makeText(context, "MaaService 异常关闭，请尝试重新启动", Toast.LENGTH_SHORT).show()
        }
    }


    var isSurfaceAvailable by remember { mutableStateOf(false) }
    val previewContent = remember {
        movableContentOf {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                surfaceTexture.setDefaultBufferSize(
                                    DefaultDisplayConfig.WIDTH,
                                    DefaultDisplayConfig.HEIGHT
                                )
                                isSurfaceAvailable = true
                                viewModel.onSurfaceAvailable(surfaceTexture)
                                updateTextureTransform(this@apply, width, height)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                Timber.d("SurfaceTexture size changed to $width x $height")
                                updateTextureTransform(this@apply, width, height)
                            }

                            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                isSurfaceAvailable = false
                                viewModel.onSurfaceDestroyed()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) =
                                Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(3f)
            ) {
                if (!state.isFullscreenMonitor) {
                    VirtualDisplayPreview(
                        isRunning = maaState == MaaExecutionState.RUNNING,
                        isSurfaceAvailable = isSurfaceAvailable,
                        onClick = { viewModel.onToggleFullscreenMonitor() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        previewContent()
                    }
                } else {
                    Spacer(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(7f)
            ) {
                PanelHeader(
                    selectedTab = state.currentTab,
                    onTabSelected = { tab ->
                        viewModel.onTabChange(tab)
                    },
                    showActions = false
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    userScrollEnabled = true,
                    beyondViewportPageCount = PanelTab.entries.size - 1
                ) { page ->
                    when (page) {
                        0 -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                TaskListPanel(
                                    nodes = nodes,
                                    selectedNodeId = state.selectedNodeId,
                                    onNodeEnabledChange = { nodeId, enabled ->
                                        viewModel.onNodeEnabledChange(nodeId, enabled)
                                    },
                                    onNodeSelected = { nodeId ->
                                        viewModel.onNodeSelected(nodeId)
                                    },
                                    onNodeMove = { fromIndex, toIndex ->
                                        viewModel.onNodeMove(fromIndex, toIndex)
                                    },
                                    onAddNode = { typeInfo ->
                                        viewModel.onAddNode(typeInfo)
                                    },
                                    onRemoveNode = { nodeId ->
                                        viewModel.onRemoveNode(nodeId)
                                    },
                                    onRenameNode = { nodeId, newName ->
                                        viewModel.onRenameNode(nodeId, newName)
                                    },
                                    modifier = Modifier
                                        .fillMaxHeight(),
                                    showEditButton = true,
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
                                            onConfigChange = { config ->
                                                val nodeId =
                                                    state.selectedNodeId ?: return@TaskConfigPanel
                                                viewModel.onNodeConfigChange(nodeId, config)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            AutoBattlePanel(modifier = Modifier.fillMaxSize())
                        }

                        2 -> {
                            PlaceholderContent(
                                title = "小工具",
                                description = "功能开发中...",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        3 -> {
                            val logs by viewModel.runtimeLogs.collectAsStateWithLifecycle()
                            LogPanel(
                                logs = logs,
                                onClearLogs = viewModel::onClearLogs,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                if (state.currentTab == PanelTab.TASKS || state.currentTab == PanelTab.AUTO_BATTLE) {
                    Spacer(modifier = Modifier.height(12.dp))

                    var showMoreActions by remember { mutableStateOf(false) }
                    val focusManager = LocalFocusManager.current

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                when (state.currentTab) {
                                    PanelTab.TASKS -> viewModel.onStartTasks()
                                    PanelTab.AUTO_BATTLE -> copilotViewModel.onStart()
                                    else -> {}
                                }
                            },
                            enabled = maaState != MaaExecutionState.RUNNING
                                    && maaState != MaaExecutionState.STARTING,
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
                                Text("开始任务")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                when (state.currentTab) {
                                    PanelTab.TASKS -> viewModel.onStopTasks()
                                    PanelTab.AUTO_BATTLE -> copilotViewModel.onStop()
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
                            Text("停止任务")
                        }

                        IconButton(
                            onClick = { showMoreActions = !showMoreActions },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (showMoreActions)
                                    Icons.Filled.KeyboardArrowUp
                                else
                                    Icons.Filled.KeyboardArrowDown,
                                contentDescription = "更多操作"
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showMoreActions,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                val muteOnGameLaunch by appSettingsManager.muteOnGameLaunch.collectAsStateWithLifecycle()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = muteOnGameLaunch,
                                        onCheckedChange = {
                                            coroutineScope.launch { appSettingsManager.setMuteOnGameLaunch(it) }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "游戏启动时静音",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    OutlinedButton(
                                        onClick = { viewModel.onMuteGameSound() },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("关闭声音", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.onUnmuteGameSound() },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("打开声音", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                // 操作按钮区
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.onScreenOff() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("熄屏挂机")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            if (maaState == MaaExecutionState.RUNNING) {
                                                showCloseConfirm = true
                                            } else {
                                                coroutineScope.launch { compositionService.stopVirtualDisplay() }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("关闭应用")
                                    }
                                }
                            }
                        }
                    }
                }
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
                    .graphicsLayer {
                        alpha = fullscreenProgress.value
                    }
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                val coords = viewToVirtualDisplay(
                                    change.position.x, change.position.y,
                                    size.width, size.height
                                ) ?: continue
                                when (event.type) {
                                    PointerEventType.Press -> viewModel.onTouchDown(
                                        coords.first,
                                        coords.second
                                    )

                                    PointerEventType.Move -> {
                                        if (change.pressed) {
                                            viewModel.onTouchMove(coords.first, coords.second)
                                        }
                                    }

                                    PointerEventType.Release -> viewModel.onTouchUp(
                                        coords.first,
                                        coords.second
                                    )
                                }
                                change.consume()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
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
                        contentDescription = "关闭",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        state.dialog?.let { dialog ->
            val confirmColor = when (dialog.type) {
                PanelDialogType.SUCCESS -> MaterialTheme.colorScheme.primary
                PanelDialogType.WARNING -> MaterialTheme.colorScheme.tertiary
                PanelDialogType.ERROR -> MaterialTheme.colorScheme.error
            }
            AdaptiveTaskPromptDialog(
                visible = true,
                title = dialog.title,
                message = AnnotatedString(dialog.message),
                onDismissRequest = viewModel::onDialogDismiss,
                onConfirm = viewModel::onDialogConfirm,
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
                title = "确认关闭",
                message = AnnotatedString("任务正在运行中，确认关闭应用吗？"),
                onDismissRequest = { showCloseConfirm = false },
                onConfirm = {
                    showCloseConfirm = false
                    coroutineScope.launch { compositionService.stopVirtualDisplay() }
                },
                confirmText = "确认关闭",
                dismissText = "取消",
                icon = Icons.Filled.Warning,
                iconTint = MaterialTheme.colorScheme.error,
                confirmColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun updateTextureTransform(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
    if (viewWidth == 0 || viewHeight == 0) return

    val viewW = viewWidth.toFloat()
    val viewH = viewHeight.toFloat()
    val bufferW = DefaultDisplayConfig.WIDTH.toFloat()
    val bufferH = DefaultDisplayConfig.HEIGHT.toFloat()

    val matrix = Matrix()
    val scale = minOf(viewW / bufferW, viewH / bufferH)

    matrix.postScale(bufferW / viewW, bufferH / viewH)
    matrix.postScale(scale, scale)

    val scaledW = bufferW * scale
    val scaledH = bufferH * scale
    val offsetX = (viewW - scaledW) / 2f
    val offsetY = (viewH - scaledH) / 2f
    matrix.postTranslate(offsetX, offsetY)

    textureView.setTransform(matrix)
}

private fun viewToVirtualDisplay(
    viewX: Float, viewY: Float,
    viewWidth: Int, viewHeight: Int
): Pair<Int, Int>? {
    val bufferW = DefaultDisplayConfig.WIDTH.toFloat()
    val bufferH = DefaultDisplayConfig.HEIGHT.toFloat()
    val scale = minOf(viewWidth / bufferW, viewHeight / bufferH)
    val offsetX = (viewWidth - bufferW * scale) / 2f
    val offsetY = (viewHeight - bufferH * scale) / 2f
    val vx = ((viewX - offsetX) / scale).toInt()
    val vy = ((viewY - offsetY) / scale).toInt()
    if (vx < 0 || vx >= bufferW.toInt() || vy < 0 || vy >= bufferH.toInt()) return null
    return vx to vy
}
