package com.aliothmoon.maameow.presentation.view.panel.depot

import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.DepotPlanOutcome
import com.aliothmoon.maameow.data.model.LogColorRole
import com.aliothmoon.maameow.data.model.depotPlanOutcome
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.data.resource.StageAliasMapper
import com.aliothmoon.maameow.presentation.components.ItemIcon
import com.aliothmoon.maameow.presentation.components.SectionHeader
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.theme.MaaMotion
import com.aliothmoon.maameow.theme.OpaqueTheme
import com.aliothmoon.maameow.theme.themedColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

internal data class DepotPlanSheetTarget(
    val itemId: String,
    val itemName: String,
    val planIndex: Int?,
)

/**
 * 统一的库存保持底部抽屉（主界面使用，不用于悬浮窗）
 * - [initialTarget] 为 null：新建流程，固定高度内卡片侧滑切换「选物品」与「编计划」，避免锚点重算卡顿与返回误拉全屏
 * - [initialTarget] 非 null：编辑已有计划，按内容自适应高度；未选物品的空计划先走选物品，保存回原位
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DepotPlanFlowSheet(
    initialTarget: DepotPlanSheetTarget?,
    unplannedCells: List<DepotItemCellUi>,
    existingForIndex: (Int) -> DepotMaintainPlan?,
    countOf: (String) -> Int,
    dualChipExtraOf: (String) -> Int,
    nodeEnabled: Boolean,
    synced: Boolean,
    showMedicine: Boolean,
    showStone: Boolean,
    iconLoader: ItemIconLoader,
    onDismiss: () -> Unit,
    onSave: (planIndex: Int?, plan: DepotMaintainPlan) -> Unit,
    onRemove: (planIndex: Int) -> Unit,
    itemHelper: ItemHelper = koinInject(),
    activityManager: ActivityManager = koinInject(),
) {
    val isAddFlow = initialTarget == null || initialTarget.itemId.isBlank()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val gridState = rememberLazyGridState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val addFlowHeight = (screenHeight * 0.68f).coerceAtMost((screenHeight - 72.dp).coerceAtLeast(240.dp))
    val editMaxHeight = (screenHeight * 0.85f).coerceAtMost((screenHeight - 72.dp).coerceAtLeast(240.dp))

    var pickedTarget by remember(initialTarget) { mutableStateOf<DepotPlanSheetTarget?>(null) }
    // 退场滑出期间保留最后一次选中的物品，避免滑出半途内容突然变空
    var lastPickedTarget by remember(initialTarget) { mutableStateOf<DepotPlanSheetTarget?>(null) }
    val canGoBack = isAddFlow && pickedTarget != null

    val sheetVisibleProgress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val closeWithAnimation: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            scope.launch {
                try {
                    sheetVisibleProgress.animateTo(0f, MaaMotion.spec(reduceMotion, MaaMotion.Fast))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 非取消异常时仍继续完成关闭回调
                }
                onDismiss()
                action()
            }
        }
    }

    LaunchedEffect(Unit) {
        sheetVisibleProgress.animateTo(1f, MaaMotion.spec(reduceMotion, MaaMotion.Medium))
    }

    OpaqueTheme {
        DepotBottomSheetDialog(
            visibleProgress = sheetVisibleProgress,
            reduceMotion = reduceMotion,
            onDismissRequest = { closeWithAnimation {} },
        ) {
            BackHandler(enabled = !canGoBack) {
                closeWithAnimation {}
            }
            BackHandler(enabled = canGoBack) {
                pickedTarget = null
            }

            if (isAddFlow) {
                // 底层网格轻微左移视差 + 压暗，顶层编辑卡片整页右进右出，纯 GPU 变换不触发重排
                val editorProgressState = animateFloatAsState(
                    targetValue = if (pickedTarget != null) 1f else 0f,
                    animationSpec = MaaMotion.spec(reduceMotion, MaaMotion.Medium),
                    label = "depot-sheet-card-progress",
                )
                // 卡片完全盖住后跳过底层网格的测量与绘制，IME 期间零开销且返回零重建
                val pickerOccluded by remember(initialTarget) {
                    derivedStateOf { pickedTarget != null && editorProgressState.value >= 1f }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(addFlowHeight)
                        .clipToBounds(),
                ) {
                    Column(
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                if (pickerOccluded) {
                                    layout(0, 0) {}
                                } else {
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) {
                                        placeable.place(0, 0)
                                    }
                                }
                            }
                            .fillMaxSize()
                            .graphicsLayer {
                                val progress = editorProgressState.value
                                translationX = -size.width * 0.12f * progress
                                alpha = 1f - 0.35f * progress
                            }
                            .then(if (pickedTarget != null) Modifier.clearAndSetSemantics {} else Modifier)
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (unplannedCells.isEmpty()) {
                            Text(
                                text = stringResource(R.string.panel_depot_unplanned_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.panel_depot_unplanned_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            DepotItemLazyGrid(
                                cells = unplannedCells,
                                synced = synced,
                                iconLoader = iconLoader,
                                gridState = gridState,
                                onClick = { cell ->
                                    val next = DepotPlanSheetTarget(
                                        itemId = cell.itemId,
                                        itemName = cell.name,
                                        planIndex = initialTarget?.planIndex,
                                    )
                                    lastPickedTarget = next
                                    pickedTarget = next
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }
                    }

                    MaaAnimatedVisibility(
                        visible = pickedTarget != null,
                        enter = if (reduceMotion) {
                            EnterTransition.None
                        } else {
                            slideInHorizontally(MaaMotion.spec(false, MaaMotion.Medium)) { it }
                        },
                        exit = if (reduceMotion) {
                            ExitTransition.None
                        } else {
                            slideOutHorizontally(MaaMotion.spec(false, MaaMotion.Medium)) { it }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val target = pickedTarget ?: lastPickedTarget
                        if (target != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shadowElevation = if (pickerOccluded) 0.dp else 6.dp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                    ),
                            ) {
                                DepotPlanEditorContent(
                                    itemId = target.itemId,
                                    itemName = target.itemName,
                                    count = countOf(target.itemId),
                                    countOf = countOf,
                                    dualChipExtraOf = dualChipExtraOf,
                                    existing = target.planIndex?.let(existingForIndex),
                                    nodeEnabled = nodeEnabled,
                                    synced = synced,
                                    showMedicine = showMedicine,
                                    showStone = showStone,
                                    fillHeight = true,
                                    onBack = { pickedTarget = null },
                                    onSave = { plan ->
                                        closeWithAnimation { onSave(target.planIndex, plan) }
                                    },
                                    onRemove = {
                                        target.planIndex?.let { index ->
                                            closeWithAnimation { onRemove(index) }
                                        }
                                    },
                                    itemHelper = itemHelper,
                                    activityManager = activityManager,
                                    iconLoader = iconLoader,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            } else if (initialTarget != null) {
                val target = initialTarget
                val existing = target.planIndex?.let(existingForIndex)
                DepotPlanEditorContent(
                    itemId = target.itemId,
                    itemName = target.itemName,
                    count = countOf(target.itemId),
                    countOf = countOf,
                    dualChipExtraOf = dualChipExtraOf,
                    existing = existing,
                    nodeEnabled = nodeEnabled,
                    synced = synced,
                    showMedicine = showMedicine,
                    showStone = showStone,
                    fillHeight = false,
                    onBack = null,
                    onSave = { plan ->
                        closeWithAnimation { onSave(target.planIndex, plan) }
                    },
                    onRemove = {
                        target.planIndex?.let { index ->
                            closeWithAnimation { onRemove(index) }
                        }
                    },
                    itemHelper = itemHelper,
                    activityManager = activityManager,
                    iconLoader = iconLoader,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = editMaxHeight),
                )
            }
        }
    }
}

/** 上报当前聚焦输入框坐标给 [DepotBottomSheetDialog]，用于按需避让输入法 */
internal val LocalDepotSheetFocusTracker = staticCompositionLocalOf<((LayoutCoordinates?) -> Unit)?> { null }

/**
 * 轻量底部抽屉容器：结合 [WindowInsets.imeAnimationTarget] 与当前聚焦输入框坐标，
 * 仅在输入框会被键盘遮挡时一次性把抽屉平移到输入框紧贴键盘上方，不顶起无关底部按钮，
 * 同时避免 M3 ModalBottomSheet 逐帧重排与光标 Popup 反复 relayoutWindow 拖慢输入法
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DepotBottomSheetDialog(
    visibleProgress: Animatable<Float, *>,
    reduceMotion: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dragOffsetPx = remember { Animatable(0f) }
    var rootHeightPx by remember { mutableFloatStateOf(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    var sheetCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var focusedBottomInSheetPx by remember { mutableStateOf<Float?>(null) }

    val reportFocusedField: (LayoutCoordinates?) -> Unit = remember {
        { fieldCoords ->
            val sc = sheetCoords
            focusedBottomInSheetPx = if (sc != null && fieldCoords != null && sc.isAttached && fieldCoords.isAttached) {
                sc.localPositionOf(fieldCoords, Offset(0f, fieldCoords.size.height.toFloat())).y
            } else if (fieldCoords == null) {
                null
            } else {
                focusedBottomInSheetPx
            }
        }
    }

    val settleDrag: (Float) -> Unit = { velocityY ->
        val shouldDismiss = velocityY > 1200f ||
            (sheetHeightPx > 0f && dragOffsetPx.value > sheetHeightPx * 0.25f)
        if (shouldDismiss) {
            onDismissRequest()
        } else if (dragOffsetPx.value > 0f) {
            scope.launch {
                dragOffsetPx.animateTo(0f, MaaMotion.spec(reduceMotion, MaaMotion.Fast))
            }
        }
    }

    val nestedScrollConnection = remember(reduceMotion) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f && dragOffsetPx.value > 0f && source == NestedScrollSource.UserInput) {
                    val consumed = delta.coerceAtLeast(-dragOffsetPx.value)
                    scope.launch { dragOffsetPx.snapTo((dragOffsetPx.value + consumed).coerceAtLeast(0f)) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = available.y
                if (delta > 0f && source == NestedScrollSource.UserInput) {
                    scope.launch { dragOffsetPx.snapTo(dragOffsetPx.value + delta) }
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffsetPx.value > 0f) {
                    settleDrag(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dragOffsetPx.value > 0f) {
                    settleDrag(available.y)
                }
                return Velocity.Zero
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val density = LocalDensity.current
        val darkSystemBarIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f
        // IME 动画逐帧重组，窗口配置不能放 SideEffect
        DisposableEffect(view, darkSystemBarIcons) {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@DisposableEffect onDispose {}
            window.setDimAmount(0f)
            window.setWindowAnimations(0)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
            }
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            window.setSoftInputMode(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                },
            )
            window.attributes = window.attributes.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    fitInsetsTypes = 0
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = darkSystemBarIcons
                isAppearanceLightNavigationBars = darkSystemBarIcons
            }
            onDispose {}
        }

        val statusTopInsets = WindowInsets.statusBars.only(WindowInsetsSides.Top)
        val navBottomInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
        val imeTargetBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)
        val statusBarTopPx = WindowInsets.statusBars.getTop(density)
        // 为光标水滴手柄留出 24dp 间距，避免紧贴键盘顶边被裁切
        val imeMarginPx = with(density) { 24.dp.toPx() }
        val focusedBottom = focusedBottomInSheetPx
        val imePanPx = if (imeTargetBottomPx > 0 && focusedBottom != null && sheetHeightPx > 0f && rootHeightPx > 0f) {
            val distanceFromSheetBottom = sheetHeightPx - focusedBottom
            val rawPan = (imeTargetBottomPx + imeMarginPx - distanceFromSheetBottom).coerceAtLeast(0f)
            val maxPanByStatusBar = (rootHeightPx - sheetHeightPx - statusBarTopPx).coerceAtLeast(0f)
            val maxPanByIme = imeTargetBottomPx.toFloat()
            rawPan.coerceIn(0f, minOf(maxPanByStatusBar, maxPanByIme))
        } else {
            0f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { rootHeightPx = it.height.toFloat() },
        ) {
            // 遮罩层铺满全屏，点击空白关闭
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = visibleProgress.value }
                    .background(BottomSheetDefaults.ScrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(statusTopInsets),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    shape = BottomSheetDefaults.ExpandedShape,
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .widthIn(max = BottomSheetDefaults.SheetMaxWidth)
                        .fillMaxWidth()
                        .offset { IntOffset(0, -imePanPx.roundToInt()) }
                        .onGloballyPositioned { coords ->
                            sheetCoords = coords
                            sheetHeightPx = coords.size.height.toFloat()
                        }
                        .graphicsLayer {
                            val enterOffset = (1f - visibleProgress.value) * size.height
                            translationY = enterOffset + dragOffsetPx.value.coerceAtLeast(0f)
                        }
                        .nestedScroll(nestedScrollConnection)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    dragOffsetPx.snapTo((dragOffsetPx.value + delta).coerceAtLeast(0f))
                                }
                            },
                            onDragStopped = { velocity -> settleDrag(velocity) },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    CompositionLocalProvider(LocalDepotSheetFocusTracker provides reportFocusedField) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(navBottomInsets),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                BottomSheetDefaults.DragHandle()
                            }
                            content()
                        }
                    }
                }
            }
        }
    }
}

/** 单条库存保持计划编辑表单内容；头尾固定、中间表单区独立滚动 */
@Composable
private fun DepotPlanEditorContent(
    itemId: String,
    itemName: String,
    count: Int,
    countOf: (String) -> Int,
    dualChipExtraOf: (String) -> Int,
    existing: DepotMaintainPlan?,
    nodeEnabled: Boolean,
    synced: Boolean,
    showMedicine: Boolean,
    showStone: Boolean,
    fillHeight: Boolean,
    onBack: (() -> Unit)?,
    onSave: (DepotMaintainPlan) -> Unit,
    onRemove: () -> Unit,
    itemHelper: ItemHelper,
    activityManager: ActivityManager,
    iconLoader: ItemIconLoader,
    modifier: Modifier = Modifier,
) {
    val stageGroups = rememberDepotStageGroups(activityManager)
    val stageCodes = remember(stageGroups) { stageGroups.allStageCodes() }
    val itemIndex by itemHelper.items.collectAsStateWithLifecycle()
    val itemNameMap = remember(itemIndex) { itemIndex.mapValues { it.value.name } }

    var draft by remember(itemId, existing) {
        mutableStateOf(
            existing?.let { if (it.dropId.isBlank()) it.copy(dropId = itemId) else it }
                ?: DepotMaintainPlan(
                    dropId = itemId,
                    dropCount = count.coerceAtLeast(1),
                ),
        )
    }
    val currentCount = countOf(draft.dropId)
    val currentDualChipExtra = dualChipExtraOf(draft.dropId)

    var customStage by remember(itemId, existing) {
        mutableStateOf(existing?.stage?.let { it.isNotBlank() && it !in stageCodes } ?: false)
    }
    var targetInvalid by remember(itemId, existing) { mutableStateOf(false) }
    var medicineBlank by remember(itemId, existing) { mutableStateOf(false) }
    var stoneBlank by remember(itemId, existing) { mutableStateOf(false) }

    val draftTarget = draft.dropCount.coerceIn(1, MAX_DEPOT_TARGET_INVENTORY)
    val headerItemId = draft.dropId.ifBlank { itemId }
    val headerName = itemNameMap[headerItemId] ?: itemName
    val short = draftTarget > currentCount

    // 按当前草稿实时映射别名并评估去向，改关后提示立即同步
    val resolvedStage = remember(draft.stage, stageCodes) {
        StageAliasMapper.mapToStageCode(draft.stage, stageCodes)
    }
    val liveOutcome = remember(draft, resolvedStage, currentCount, synced, stageGroups) {
        depotPlanOutcome(
            plan = draft.copy(stage = resolvedStage, dropCount = draftTarget),
            currentCount = if (synced) currentCount else 0,
            isStageOpen = { activityManager.isStageOpen(it) },
        )
    }
    val canSave = !targetInvalid &&
        draft.dropId.isNotBlank() &&
        resolvedStage.isNotBlank() &&
        resolvedStage != "Annihilation"

    Column(
        modifier = modifier
            .padding(
                start = MaaDesignTokens.Spacing.lg,
                end = MaaDesignTokens.Spacing.lg,
                bottom = MaaDesignTokens.Spacing.lg,
            ),
        verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
            }
            ItemIcon(
                itemId = headerItemId,
                modifier = Modifier.size(40.dp),
                contentDescription = null,
                loader = iconLoader,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = depotProgressText(currentCount, draftTarget, synced, currentDualChipExtra),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 仅具备库存基线时显示「缺 N / 已够」，未识别仓库不误报缺口
            if (synced) {
                val successColor = LogColorRole.SUCCESS.themedColor()
                Surface(
                    shape = RoundedCornerShape(MaaDesignTokens.CornerRadius.inner),
                    color = if (short) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        successColor.copy(alpha = 0.14f)
                    },
                    contentColor = if (short) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        successColor
                    },
                    modifier = Modifier.widthIn(max = 132.dp),
                ) {
                    Text(
                        text = if (short) {
                            stringResource(
                                R.string.depot_inventory_maintain_need,
                                draftTarget - currentCount,
                            )
                        } else {
                            stringResource(R.string.depot_inventory_maintain_enough)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = fillHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
        ) {
            SectionHeader(
                title = if (existing == null) {
                    stringResource(R.string.depot_inventory_plan_new)
                } else {
                    stringResource(R.string.depot_inventory_maintain_title)
                },
            )

            DepotPlanFields(
                plan = draft,
                onPlanChange = { draft = it },
                stageGroups = stageGroups,
                stageCodes = stageCodes,
                customStageCode = customStage,
                onCustomStageSelected = { customStage = true },
                showStageListWithCustom = true,
                onStageSelected = {
                    draft = draft.copy(stage = it)
                    customStage = false
                },
                showMedicine = showMedicine,
                showStone = showStone,
                onTargetBlankChange = { targetInvalid = it },
                onMedicineBlankChange = { medicineBlank = it },
                onStoneBlankChange = { stoneBlank = it },
            )

            if (liveOutcome != DepotPlanOutcome.Enough && liveOutcome != DepotPlanOutcome.Runnable) {
                val (noticeBg, noticeFg) = when (liveOutcome) {
                    DepotPlanOutcome.StageClosed -> depotWarningColors()
                    DepotPlanOutcome.StageRequired ->
                        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant

                    else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                }
                DepotEditorNoticeBanner(
                    icon = if (liveOutcome == DepotPlanOutcome.StageRequired) {
                        Icons.Outlined.Info
                    } else {
                        Icons.Default.WarningAmber
                    },
                    text = depotPlanOutcomeLabel(
                        outcome = liveOutcome,
                        need = (draftTarget - currentCount).coerceAtLeast(0),
                        stage = resolvedStage,
                    ),
                    containerColor = noticeBg,
                    contentColor = noticeFg,
                )
            }

            if (!nodeEnabled) {
                DepotEditorNoticeBanner(
                    icon = Icons.Default.WarningAmber,
                    text = stringResource(R.string.depot_inventory_plan_node_disabled),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val buttonShape = RoundedCornerShape(MaaDesignTokens.CornerRadius.inner)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
        ) {
            if (existing != null) {
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f),
                    shape = buttonShape,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.depot_inventory_plan_remove))
                }
            }
            Button(
                onClick = {
                    onSave(
                        draft.copy(
                            dropCount = draftTarget,
                            stage = resolvedStage,
                            medicineCount = if (medicineBlank) 0 else draft.medicineCount,
                            stoneCount = if (stoneBlank) 0 else draft.stoneCount,
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.depot_inventory_plan_save))
            }
        }
    }
}

@Composable
private fun DepotEditorNoticeBanner(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(MaaDesignTokens.CornerRadius.inner),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
