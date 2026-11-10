package com.aliothmoon.maameow.presentation.view.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar as M3TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.profileNameUiText
import com.aliothmoon.maameow.data.model.toolbox.OperBoxExportLabels
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.repository.OperBoxSnapshot
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.ToolboxExportFileType
import com.aliothmoon.maameow.domain.service.ToolboxExportService
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.view.panel.ToolboxFileExporter
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotCellGap
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotItemCell
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotItemCellUi
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotPlanEditorSheet
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotSectionBreak
import com.aliothmoon.maameow.presentation.view.panel.depot.depotItemColumns
import com.aliothmoon.maameow.presentation.view.panel.operbox.OperBoxCard
import com.aliothmoon.maameow.presentation.view.panel.operbox.OperBoxDetailDialog
import com.aliothmoon.maameow.presentation.view.panel.rememberOperBoxExportLabels
import com.aliothmoon.maameow.presentation.view.panel.rememberSafToolboxFileExporter
import com.aliothmoon.maameow.presentation.viewmodel.DepotInventoryCellUi
import com.aliothmoon.maameow.presentation.viewmodel.DepotInventoryViewModel
import com.aliothmoon.maameow.presentation.viewmodel.DepotPngLabels
import com.aliothmoon.maameow.presentation.viewmodel.DepotProfileRow
import com.aliothmoon.maameow.presentation.viewmodel.OperBoxPngLabels
import com.aliothmoon.maameow.presentation.viewmodel.depotCellKey
import com.aliothmoon.maameow.presentation.viewmodel.groupForDisplay
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.theme.MaaMotion
import com.aliothmoon.maameow.theme.OpaqueTheme
import com.aliothmoon.maameow.utils.i18n.asString
import com.aliothmoon.maameow.utils.i18n.formatToolboxSyncTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import timber.log.Timber
import kotlin.math.min
import kotlin.math.roundToInt

/** 「库存为 0」那段标题行的 key：展开时要按它把这一段顶回眼前 */
private const val EMPTY_BREAK_KEY = "empty-break"

/** 展开时逐格错开多少毫秒；同一排差几毫秒，看着还是一起长，排与排之间才拉得开 */
private const val EMPTY_REVEAL_STEP_MS = 6L

/** 错开的上限：再往下的格子都在屏幕外，没必要排到几百毫秒之后 */
private const val EMPTY_REVEAL_MAX_MS = 150L

/**
 * PNG 渲染互斥标记：进程级，不能是 composition 级。
 *
 * 一张底图 ≈12M 像素 / 约 48MB。转屏、深浅色切换、返回再进都会重建 composition，
 * `remember { mutableStateOf(false) }` 会把它归零，而旧 job 可能仍卡在不可中断的
 * `bitmap.compress` 里 —— 于是又放行一次，两张底图并发（低内存机直接 OOM）。
 * 库存图与干员图共用这一个标记（同一份内存压力）：它们不是两个面板，
 * 而是同一个底部面板里的两个二级 section，同一时刻只会有一份渲染在跑。
 */
private val pngRenderBusyState = mutableStateOf(false)

/**
 * 库存数据：二级配置档列表 → 三级配置档详情。
 *
 * 仓库/干员快照按配置档分片，一档一份；三级页把「库存保持」计划并进库存格子，
 * 物品的当前库存就是计划进度，点格子直接改目标库存。
 */
@Composable
fun DepotInventoryView(
    navController: NavController,
    viewModel: DepotInventoryViewModel = koinViewModel(),
) {
    val rows by viewModel.profileRows.collectAsStateWithLifecycle()
    val selectedProfileId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    val exporter = rememberSafToolboxFileExporter()
    val exportLabels = rememberOperBoxExportLabels()
    // 导出协程挂在页面上而不是面板上：渲染要几秒，面板一收就取消会把导出吞掉
    val exportScope = rememberCoroutineScope()
    var exportProfileId by remember { mutableStateOf<String?>(null) }
    var pendingClearProfileId by remember { mutableStateOf<String?>(null) }
    // 「正在生成图片」与那个渲染任务都挂在页面上，而不是导出面板里：
    // 面板关掉再打开是新的 composition，面板内的 remember 会重新变成 false，
    // 于是能再点一次 —— 两张 ≈12M 像素的底图并发，低内存机直接 OOM。
    // 会话结束时取消在跑的渲染，避免在已经切走的界面上弹分享面板
    val exportingPng = remember { mutableStateOf(false) }
    val pngExportJob = remember { mutableStateOf<Job?>(null) }
    // 「真的还有渲染在跑」：`exportingPng` 会在关面板时立刻清零（避免假禁用），
    // 但被取消的 job 可能仍卡在不可中断的 PNG 编码里 —— 这时再放行会并发两张位图（低内存机 OOM）。
    // 这个标志只在 job 的 invokeOnCompletion 里落下，即「body 真的结束了」；它必须是进程级
    // （见 `pngRenderBusyState`），否则转屏把 composition 重建一次，守卫就回 false。
    val pngRenderBusy = pngRenderBusyState
    // 哪一次渲染属于「当前这个面板」：渲染跑在页面 scope 上，面板可能被关掉再打开，
    // 不认主的话上一次渲染结束会把用户新开的面板一起关掉
    val pngExportToken = remember { mutableStateOf<Any?>(null) }
    DisposableEffect(Unit) {
        onDispose { pngExportJob.value?.cancel() }
    }

    BackHandler(enabled = selectedProfileId.isNotEmpty()) { viewModel.clearSelection() }

    AnimatedContent(
        targetState = selectedProfileId,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val forward = targetState.isNotEmpty()
            val spec = tween<IntOffset>(220, easing = MaaMotion.Emphasized)
            val enter = slideInHorizontally(animationSpec = spec) { if (forward) it / 5 else -it / 5 } +
                fadeIn(tween(200))
            val exit = slideOutHorizontally(animationSpec = spec) { if (forward) -it / 5 else it / 5 } +
                fadeOut(tween(160))
            enter togetherWith exit
        },
        label = "depot-inventory-navigation",
    ) { profileId ->
        if (profileId.isEmpty()) {
            DepotProfileListView(
                rows = rows,
                onBack = { navController.navigateUp() },
                onOpen = viewModel::selectProfile,
                onExport = { exportProfileId = it },
                onClear = { pendingClearProfileId = it },
            )
        } else {
            DepotProfileDetailView(
                row = rows.firstOrNull { it.id == profileId },
                profileName = profileNameUiText(rows.firstOrNull { it.id == profileId }?.name ?: profileId).asString(),
                onBack = { viewModel.clearSelection() },
                onExport = { exportProfileId = profileId },
                onClear = { pendingClearProfileId = profileId },
                viewModel = viewModel,
            )
        }
    }

    exportProfileId?.let { id ->
        // 玻璃背景下面板会透出底下的内容，弹层统一换回不透明配色
        OpaqueTheme {
            DepotInventoryExportBottomSheet(
                profileId = id,
                titleLabel = profileNameUiText(rows.firstOrNull { it.id == id }?.name ?: id).asString(),
                exportScope = exportScope,
                exportingPng = exportingPng,
                pngExportJob = pngExportJob,
                pngExportToken = pngExportToken,
                pngRenderBusy = pngRenderBusy,
                onDismiss = { exportProfileId = null },
                viewModel = viewModel,
                exporter = exporter,
                exportLabels = exportLabels,
            )
        }
    }

    pendingClearProfileId?.let { id ->
        val name = profileNameUiText(rows.firstOrNull { it.id == id }?.name ?: id).asString()
        // 与其它确认框一致：玻璃配色下弹窗底色是半透明的，会透出底下的库存网格
        OpaqueTheme {
            AlertDialog(
                onDismissRequest = { pendingClearProfileId = null },
                title = { Text(stringResource(R.string.depot_inventory_delete_title)) },
                text = { Text(stringResource(R.string.depot_inventory_delete_message, name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearProfile(id)
                            pendingClearProfileId = null
                        },
                    ) {
                        Text(
                            stringResource(R.string.depot_inventory_delete_confirm),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingClearProfileId = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}

// ============================== 二级：配置档列表 ==============================

@Composable
private fun DepotProfileListView(
    rows: List<DepotProfileRow>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onExport: (String) -> Unit,
    onClear: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.depot_inventory_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.depot_inventory_empty_profiles),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = MaaDesignTokens.Spacing.listHorizontal,
                        vertical = MaaDesignTokens.Spacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
                ) {
                    items(rows, key = { it.id }) { row ->
                        SwipeRevealProfileCard(
                            row = row,
                            onClick = { onOpen(row.id) },
                            onExport = { onExport(row.id) },
                            onClear = { onClear(row.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 左滑露出「导出 / 清空」的配置档卡片。
 *
 * 操作钮只铺在露出的那条窄带里，卡片用 surfaceVariant 也不会透出整块按钮。
 */
@Composable
private fun SwipeRevealProfileCard(
    row: DepotProfileRow,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidth = 144.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    // 拖动期间用 snap 直接跟手，松手才切成 tween 收敛：
    // 不在每个触摸事件里起一个协程去 snapTo 一个 Animatable
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val offsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = if (dragging) snap() else tween(180),
        label = "swipe-reveal-offset",
    )
    // 用卡片实测高度，保证按钮与卡片完全等高
    var cardHeightPx by remember { mutableIntStateOf(0) }
    val revealPx = (-offsetX).coerceIn(0f, actionWidthPx)
    val revealDp = with(density) { revealPx.toDp() }
    val cardHeightDp = with(density) { cardHeightPx.toDp() }
    val revealed = revealPx > 8f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaaDesignTokens.CornerRadius.card)),
    ) {
        if (cardHeightPx > 0 && revealPx > 0.5f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(cardHeightDp)
                    .width(revealDp)
                    .clip(RectangleShape),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(actionWidth),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(enabled = revealed, onClick = onExport),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.depot_inventory_export),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.error)
                            .clickable(enabled = revealed, onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.depot_inventory_delete),
                            tint = MaterialTheme.colorScheme.onError,
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { cardHeightPx = it.height }
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(actionWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            dragOffsetX = if (dragOffsetX < -actionWidthPx / 2f) {
                                -actionWidthPx
                            } else {
                                0f
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            // 取消也要收敛回去，否则卡片停在半开位置
                            dragOffsetX = if (dragOffsetX < -actionWidthPx / 2f) {
                                -actionWidthPx
                            } else {
                                0f
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetX = (dragOffsetX + dragAmount)
                                .coerceIn(-actionWidthPx, 0f)
                        },
                    )
                }
                .clickable {
                    if (dragOffsetX < -8f) {
                        dragging = false
                        dragOffsetX = 0f
                    } else {
                        onClick()
                    }
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(MaaDesignTokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
                ) {
                    Text(
                        text = profileNameUiText(row.name).asString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.isActive) {
                        Text(
                            text = stringResource(R.string.depot_inventory_profile_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = row.maintainSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.hasSynced && row.unmetCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (row.syncTimeMillis > 0L) {
                    Text(
                        text = stringResource(
                            R.string.depot_inventory_sync_time,
                            formatToolboxSyncTime(row.syncTimeMillis),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 卡片上的保持进度文案：没识别过 / 没配计划 / 全部已够 / 还差几项。
 *
 * 没有库存快照时先说「没数据」——那种情况下所有计划都会算成未集齐，
 * 直接报「N 项未集齐」会让人以为真缺这么多。
 */
@Composable
private fun DepotProfileRow.maintainSummary(): String = when {
    !hasSynced -> stringResource(R.string.depot_inventory_empty_profile)
    planCount == 0 -> stringResource(R.string.depot_inventory_maintain_none)
    unmetCount == 0 -> stringResource(R.string.depot_inventory_maintain_all_done)
    else -> stringResource(R.string.depot_inventory_maintain_unmet_count, unmetCount)
}

// ============================== 三级：配置档详情 ==============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepotProfileDetailView(
    row: DepotProfileRow?,
    profileName: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    viewModel: DepotInventoryViewModel,
) {
    val cells by viewModel.cells.collectAsStateWithLifecycle()
    val operBox by viewModel.operBoxSnapshot.collectAsStateWithLifecycle()
    val planContext by viewModel.planContext.collectAsStateWithLifecycle()
    // 没识别过仓库时当前库存是未知：格子、面板、刷取顺序都按「--」显示，别写 0
    val synced by viewModel.inventorySynced.collectAsStateWithLifecycle()
    // 存格子的 key 而不是物品 id：同一物品配了多条计划时会有多格，按 id 找只会拿到第一格
    var planSheetCellKey by remember { mutableStateOf<String?>(null) }

    // 标题栏常驻不隐去。头部按「滚动量」等比收起，不设阈值也不强制切换：手指划多少就收多少；
    // 配置名则由页面里的位置直接平移到标题栏里，全程可见 —— 不是淡出后再在栏里淡入。
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // 用 rememberSaveable：深色切换/改字号会重建 Activity（AndroidManifest 的 configChanges
    // 不含 uiMode/fontScale），用 remember 会留下「列表已经滚走、进度却是 0」的错位，
    // 头部于是卡在展开态：listAtTop=false 时 onPreScroll 直接返回 Zero，再也收不起来
    var scrolledPx by rememberSaveable { mutableStateOf(0f) }
    var overlayOrigin by remember { mutableStateOf(Offset.Unspecified) }
    var headerNameBounds by remember { mutableStateOf<Rect?>(null) }
    var barNameBounds by remember { mutableStateOf<Rect?>(null) }
    // 当前页签的列表是否停在顶部：只有停顶时手势才先收头部。
    // 不停顶还去收头部的话，列表自己滚 d 的同时头部又缩 d，内容会以两倍速度往上跑
    // 按页记录而不是共享一个值：pager 在落位动画期间就会组合目标页，那时 settledPage 还没翻，
    // 「只让当前页写」的门控会把到达页的首次上报整段丢掉（空页更是只发一次），
    // 于是换页后共享值会一直等于上一页的 —— 每页各自记，当前页的值直接派生出来
    val pageAtTop = remember { mutableStateMapOf<Int, Boolean>() }
    val currentPage = remember { mutableIntStateOf(0) }
    val listAtTop = pageAtTop[currentPage.intValue] ?: true
    // 进度必须跟「当前页签的列表在哪儿」保持一致，但**不能在列表回顶那一帧把进度清零**：
    // 那样下拉时头部会「啪」地弹开，而不是跟着手指标尺按 1:1 放下。
    // 两条不变量：
    //   ① 换页签 → 先按「该页当前是否停顶」重置：停顶就展开，不停顶就收满；
    //   ② 列表一旦离开顶部，进度只能是「完全收起」（列表滚动前头部已被 onPreScroll 收满）。
    val reportAtTop: (Int, Boolean) -> Unit = { page, atTop ->
        if (pageAtTop[page] != atTop) pageAtTop[page] = atTop
    }
    // 手势回调里要读「当前页现在是否停顶」的实时值：listAtTop 只是 composition 期的快照
    val atTopNow: () -> Boolean = { pageAtTop[currentPage.intValue] ?: true }
    // 横滑/切页的落位动画期间 currentPage 还没翻：这时按上一页的 atTop 判定会让第一下手势空转。
    // 由 InventoryDetailBody 上报「pager 正在动」，这期间头部不吃手势。
    val pageSwitching = remember { mutableStateOf(false) }
    // ① 换页签：按「该页当前是否停顶」重置一次。停顶就展开，不停顶就收满
    LaunchedEffect(currentPage.intValue) {
        scrolledPx = if (pageAtTop[currentPage.intValue] == false) headerHeightPx.toFloat() else 0f
    }
    // ② 头部自然高度变化（配置行/时间行/未集齐行出现、字号变化）时把进度钳回合法区间；
    // 列表已经离开顶部时还必须补到「完全收起」，否则会残留一条「半收」的窄条且再也收不掉。
    // 注意这里**没有**「列表回顶 → 进度清零」：那会在回顶那一帧把头部整块弹开，
    // 之后 onPreScroll 因 scrolledPx<=0 直接返回 Zero，跟手 1:1 下拉就废了。
    LaunchedEffect(listAtTop, headerHeightPx) {
        if (headerHeightPx <= 0) return@LaunchedEffect
        scrolledPx = if (listAtTop) {
            scrolledPx.coerceIn(0f, headerHeightPx.toFloat())
        } else {
            headerHeightPx.toFloat()
        }
    }
    val collapseProgress = if (headerHeightPx > 0) {
        (scrolledPx / headerHeightPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val scrollCollapseConnection = remember {
        object : NestedScrollConnection {
            // 在列表消费滚动之前先吃掉「收头部」那一段：收的时候列表一像素都不动，
            // 页面内容只按头部收缩的量移动一次（1:1），而不是「列表滚 d + 头部缩 d」的 2d。
            // 头部收完之后剩下的位移照常交给列表。
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (headerHeightPx <= 0) return Offset.Zero
                // 转页落位期间 currentPage 仍是上一页：这时的 atTop 判定没有意义，交给列表自己处理
                if (pageSwitching.value) return Offset.Zero
                val dy = available.y
                if (dy < 0f) {
                    // 手指上推（内容往上走）：列表还在顶部才有「收头部」的余量
                    if (!atTopNow()) return Offset.Zero
                    val room = headerHeightPx - scrolledPx
                    if (room <= 0f) return Offset.Zero
                    val used = min(-dy, room)
                    scrolledPx += used
                    return Offset(0f, -used)
                }
                if (dy > 0f) {
                    // 下拉：只有列表已经回顶，才轮到把头部放下来
                    if (!atTopNow() || scrolledPx <= 0f) return Offset.Zero
                    val used = min(dy, scrolledPx)
                    scrolledPx -= used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
        }
    }
    // 标题右侧显示这一档的识别时间；没识别过就是空串，不占位。
    // 格式与列表卡片、导出图统一走 formatToolboxSyncTime，避免同一份 syncTimeMillis 出现两种写法
    val syncTimeText = remember(row?.syncTimeMillis) {
        formatToolboxSyncTime(row?.syncTimeMillis ?: 0L)
    }
    // 收起态标题栏右侧带上「更新时间：」前缀：光一个日期读不出这是什么时候的数据
    val syncTimeLabel =
        if (syncTimeText.isEmpty()) "" else stringResource(R.string.depot_inventory_sync_time, syncTimeText)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 飞行文字用「根坐标」定位，所以先记下这层容器的原点
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() },
    ) {
        Scaffold(
            modifier = Modifier,
            topBar = {
                M3TopAppBar(
                    title = {
                        Box(Modifier.fillMaxWidth()) {
                            // 展开态的大标题：上划起步就淡出，把位置让给「正在飞过去的名字」
                            val introAlpha = (1f - collapseProgress / 0.2f).coerceIn(0f, 1f)
                            if (introAlpha > 0.01f) {
                                Text(
                                    text = stringResource(R.string.depot_inventory_detail_title),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.alpha(introAlpha),
                                )
                            }
                            // 名字与时间的「落点」：名字由外层那层飞行文字负责画，这里只量位置，
                            // 所以先按可见状态排版、再把字抹掉（alpha 0），尺寸和基线就都对得上
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profileName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { barNameBounds = it.boundsInRoot() }
                                        .alpha(if (collapseProgress >= 0.999f) 1f else 0f),
                                )
                                if (syncTimeLabel.isNotEmpty()) {
                                    Spacer(Modifier.width(MaaDesignTokens.Spacing.sm))
                                    Text(
                                        text = syncTimeLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.End,
                                        // 按比例分宽度：否则长文案（「更新时间：…」约 173dp）会把名字挤到几个字
                                        modifier = Modifier
                                            .weight(TITLE_TIME_WEIGHT)
                                            // 名字走完大半程再把时间显出来，视线才跟着名字走
                                            .alpha(((collapseProgress - 0.55f) / 0.45f).coerceIn(0f, 1f)),
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.accessibility_navigation),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onExport) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.depot_inventory_export),
                            )
                        }
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.depot_inventory_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    // 与列表页 / 仓库既有顶栏同一条基线：背景色容器 + primary 动作图标，免得进出详情页时顶栏换色。
                    // 这里没有 scrollBehavior，不会进 M3 的「已滚动」配色，所以不用传 scrolledContainerColor
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
            // 顶栏自带 windowInsets（收起后仍保留状态栏高度），所以内容区不需要再补 top：
            // 这里仍传全零，保持与改动前一致的内边距口径
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Column(
                modifier = Modifier
                    .nestedScroll(scrollCollapseConnection)
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // 头部随滚动等比收起：高度 = 自然高度 ×（1 - 进度），内容整体上移被裁掉 ——
                // 划多少收多少，没有「过了阈值就整块塌掉」那一步。
                // 配置名由外层那层飞行文字负责画，这里保留同名文字只为量出起点，所以抹成透明
                Column(
                    modifier = Modifier
                        .collapsingHeader({ collapseProgress }),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 这里量的是「自然高度」：外层 layout 只改绘制偏移，不改这个节点的测量结果
                            // 头部滑到标题栏底下之前先淡掉：Scaffold 的内容层画在 topBar 之上，
                            // 只靠裁切会让「库存保持 / 更新时间」压到标题栏文字上
                            .graphicsLayer { alpha = (1f - collapseProgress * 1.8f).coerceIn(0f, 1f) }
                            .onSizeChanged { headerHeightPx = it.height }
                            .padding(
                                horizontal = MaaDesignTokens.Spacing.md,
                                vertical = MaaDesignTokens.Spacing.xs,
                            ),
                        verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.xs),
                    ) {
                        Text(
                            text = profileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 这份文字只用来量起点（位置 + 宽度），真正画名字的是外面那层飞行文字
                            modifier = Modifier
                                // 只在「还没开始收」时记录起点：头部随滚动上移，若一直更新，起点会跟着漂
                                .onGloballyPositioned {
                                    if (collapseProgress < 0.002f) headerNameBounds = it.boundsInRoot()
                                }
                                .alpha(0f),
                        )
                        row?.let {
                            if (it.planCount > 0 || !it.hasSynced) {
                                Text(
                                    text = it.maintainSummary(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (it.hasSynced && it.unmetCount > 0) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            if (it.syncTimeMillis > 0L) {
                                Text(
                                    text = stringResource(
                                        R.string.depot_inventory_sync_time,
                                        formatToolboxSyncTime(it.syncTimeMillis),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                InventoryDetailBody(
                    cells = cells,
                    operBox = operBox,
                    synced = synced,
                    onCellClick = { planSheetCellKey = it.key },
                    // 头部收起只认「当前页签的列表停在顶部」这一段手势：真值由当页上报，
                    // 父层只接受 settled 那一页的上报，避免横滑取消后误信其他页的值
                    reportAtTop = reportAtTop,
                    currentPage = currentPage,
                    pageSwitching = pageSwitching,
                )
        }
    }

        // 飞行层：展开态头部里那个名字是「起点」，标题栏里那份是「终点」，
        // 这一层按滚动进度在两者之间插值 —— 名字是移动过去的，中途始终可见
        HeaderNameFlight(
            profileName = profileName,
            headerBounds = headerNameBounds,
            barBounds = barNameBounds,
            progress = collapseProgress,
            overlayOrigin = overlayOrigin,
        )
    }

    planSheetCellKey?.let { cellKey ->
        cells.firstOrNull { it.key == cellKey }?.let { cell ->
            DepotPlanEditorSheet(
                itemId = cell.id,
                itemName = cell.name,
                count = cell.count,
                existing = cell.plan?.plan,
                existingOutcome = cell.plan?.outcome,
                // 已有计划就跟着它所属节点的开关走，新计划才用「第一个库存保持节点」的状态
                context = cell.plan?.node ?: planContext,
                synced = synced,
                // 缺口按「该物品」的库存算：库存页里的格子已含双芯片折算，与任务面板同一口径
                countOf = { id -> cells.firstOrNull { it.id == id }?.count ?: cell.count },
                onDismiss = { planSheetCellKey = null },
                // 用点开时那一格上的计划当写回落点，不回查派生流
                onSave = { plan -> viewModel.savePlan(cell.plan, plan) },
                onRemove = { viewModel.removePlan(cell.plan) },
            )
        }
    }
}

/** Tab 行与 pager 必须上下排布：同处一个 Box 时 pager 会盖住 Tab，导致点不动。 */
@Composable
private fun InventoryDetailBody(
    cells: List<DepotInventoryCellUi>,
    operBox: OperBoxSnapshot,
    synced: Boolean,
    onCellClick: (DepotInventoryCellUi) -> Unit,
    reportAtTop: (Int, Boolean) -> Unit,
    currentPage: MutableState<Int>,
    pageSwitching: MutableState<Boolean>,
    iconLoader: ItemIconLoader = koinInject(),
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    // 把当前页签号回传给外层：头部收起逻辑要据此判断「哪一页的列表在顶部」，换页时也要归零进度
    // 用 settledPage：currentPage 在半途（50%）就翻转，来回滑会把进度反复清零，
    // settledPage 只在落位后变化，正好对应「换到另一页的列表」
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentPage.value = it }
    }
    // 落位动画期间（手势拖动 + animateScrollToPage）把「正在换页」报给头部：
    // 这期间 currentPage 还是上一页，手势若按上一页的 atTop 判定就会空转或反着动
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress || pagerState.currentPage != pagerState.settledPage }
            .collect { pageSwitching.value = it }
    }
    val scope = rememberCoroutineScope()
    // 放在 pager 外面：pager 会把滑走的页面回收掉，放里面一滑回来选择就没了
    var operBoxTab by remember { mutableIntStateOf(0) }
    // 同理，「库存为 0」那段的折叠状态也放外面；默认收起，几十个空格子不该占着屏
    var emptyCollapsed by rememberSaveable { mutableStateOf(true) }
    val pageLabels = listOf(
        stringResource(R.string.depot_inventory_tab_items),
        stringResource(R.string.depot_inventory_tab_operators),
    )
    Column(modifier = Modifier.fillMaxSize()) {
        DetailPageTabs(
            labels = pageLabels,
            selectedIndex = pagerState.currentPage,
            onSelected = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            userScrollEnabled = true,
        ) { page ->
            when (page) {
                0 -> DepotItemsPage(
                    cells = cells,
                    iconLoader = iconLoader,
                    synced = synced,
                    onCellClick = onCellClick,
                    emptyCollapsed = emptyCollapsed,
                    onToggleEmpty = { emptyCollapsed = !emptyCollapsed },
                    onAtTop = { reportAtTop(0, it) },
                )

                1 -> OperBoxPage(
                    snapshot = operBox,
                    selectedTab = operBoxTab,
                    onTabChange = { operBoxTab = it },
                    onAtTop = { reportAtTop(1, it) },
                )
            }
        }
    }
}

@Composable
private fun DetailPageTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaaDesignTokens.Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.xxl),
    ) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedIndex == index) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelected(index) }
                    .padding(vertical = MaaDesignTokens.Spacing.sm),
            )
        }
    }
}

/**
 * 库存格子网格。
 *
 * 分三段：有库存 → 未集齐 → 库存为 0。
 * 未集齐单独成组，免得缺货的东西埋在几十个已够的物品里找不到；
 * 库里一条记录都没有的材料也补齐格子（图标压淡），一眼看得出「这段都是没打过的」。
 *
 * 最后一段默认折叠：它通常占掉大半个网格，而「没打过的材料」本来就是拿来对账用的。
 * 标题上的数量一直显示，收起时也不会让人以为格子丢了。
 * 展开时这一段是从标题行底下长出来的（见 [RevealCell]），不是整片凭空出现。
 *
 * @param emptyCollapsed 「库存为 0」那段是否收起
 * @param onToggleEmpty 点那段的标题行时切折叠
 */
@Composable
private fun DepotItemsPage(
    cells: List<DepotInventoryCellUi>,
    iconLoader: ItemIconLoader,
    synced: Boolean,
    onCellClick: (DepotInventoryCellUi) -> Unit,
    emptyCollapsed: Boolean,
    onToggleEmpty: () -> Unit,
    onAtTop: (Boolean) -> Unit,
) {
    if (cells.isEmpty()) {
        // 空页也算「停在顶部」：不申报的话外层会保留上一页的 false，
        // 头部就永远收不起来（空页没有可滚内容，用户也没法把它拉回来）
        LaunchedEffect(Unit) { onAtTop(true) }
        DetailEmptyText(R.string.depot_inventory_empty_items)
        return
    }
    // 一次遍历分组，且只在 cells 真变了才重算（否则每次重组都白跑两遍 filter）
    val groups = remember(cells) { cells.groupForDisplay() }
    val gridState = rememberLazyGridState()
    // 头部收起只认「列表停在顶部」时的上推手势，所以要把滚动位置报上去
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }.collect { onAtTop(it) }
    }
    val scope = rememberCoroutineScope()
    // 收着的时候标题行就是列表的最后一行，通常正贴着屏幕下沿 ——
    // 这时展开出来的格子全在屏幕外，眼睛只能看到箭头翻了个面，像是没动效。
    // 下面留不出一整排的位置就先记下标题行的位置，展开后把它顶回列表头，
    // 让淡入发生在眼前；本来就留得下就不动，免得平白滚一下。
    val oneRow = with(LocalDensity.current) { 108.dp.roundToPx() }
    val toggleEmpty = {
        val header = gridState.layoutInfo.visibleItemsInfo
            .lastOrNull { it.key == EMPTY_BREAK_KEY }
        val room = header?.let {
            gridState.layoutInfo.viewportEndOffset - (it.offset.y + it.size.height)
        }
        val headerIndex = header?.index
        val expanding = emptyCollapsed
        onToggleEmpty()
        if (expanding && headerIndex != null && room != null && room < oneRow) {
            scope.launch { gridState.animateScrollToItem(headerIndex) }
        }
    }
    // 一行几格按可用宽度算，跟后台任务的格子同一条公式（见 depotItemColumns）。
    // 列数得先知道宽度，所以这里套一层 BoxWithConstraints
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = depotItemColumns(maxWidth - MaaDesignTokens.Spacing.md * 2)
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaaDesignTokens.Spacing.md),
            contentPadding = PaddingValues(top = 6.dp, bottom = MaaDesignTokens.Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(DepotCellGap),
        ) {
            items(groups.stocked, key = { "stocked-${it.key}" }) { cell ->
                InventoryItemCell(
                    cell = cell,
                    iconLoader = iconLoader,
                    synced = synced,
                    onClick = onCellClick,
                )
            }
            if (groups.unmet.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "unmet-break") {
                    DepotSectionBreak(
                        textRes = R.string.depot_inventory_section_unmet,
                        count = groups.unmet.size,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                items(groups.unmet, key = { "unmet-${it.key}" }) { cell ->
                    InventoryItemCell(
                        cell = cell,
                        iconLoader = iconLoader,
                        synced = synced,
                        onClick = onCellClick,
                    )
                }
            }
            if (groups.empty.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = EMPTY_BREAK_KEY) {
                    DepotSectionBreak(
                        textRes = R.string.depot_inventory_section_empty,
                        count = groups.empty.size,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        collapsed = emptyCollapsed,
                        onToggle = toggleEmpty,
                    )
                }
                // 收着的时候这些格子也留在列表里，只是高度为 0 ——
                // 这样展开、收起两边都能做「从标题行底下长出来」，
                // 而不是整片凭空出现、或者整片瞬间消失
                itemsIndexed(groups.empty, key = { _, it -> "empty-${it.key}" }) { index, cell ->
                    RevealCell(visible = !emptyCollapsed, index = index) {
                        InventoryItemCell(
                            cell = cell,
                            iconLoader = iconLoader,
                            synced = synced,
                            onClick = onCellClick,
                            dimmed = true,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「库存为 0」的一格：展开时从标题行底下长出来。
 *
 * 高度从 0 撑开、内容自上而下露出来（[MaaAnimatedVisibility] 就是全 App 那个展开 idiom），
 * 再按序错开几毫秒，整段看着是往外摊开的，而不是整片同时亮一下。
 *
 * 收起时格子仍留在列表里（高度 0），所以两个方向都有动效、也不会留下空档；
 * 展开之后再滚进来的格子是「第一次组合就可见」，直接到位，不会再弹一次。
 */
@Composable
private fun RevealCell(
    visible: Boolean,
    index: Int,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    // 初值取当前可见性：第一次组合就可见的格子（展开后滚动进来的）不该补一次动效
    var shown by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible && !shown && !reduceMotion) {
            delay((index * EMPTY_REVEAL_STEP_MS).coerceAtMost(EMPTY_REVEAL_MAX_MS))
        }
        shown = visible
    }
    MaaAnimatedVisibility(
        visible = shown,
        label = "depot-empty-cell",
        content = { content() },
    )
}

/**
 * 一格库存：网格里的 [DepotInventoryCellUi] 换成共用的格子模型。
 *
 * 格子的样子在 [DepotItemCell] 里，后台任务库存保持面板的「已有计划 / 暂无计划」两组用的是同一份。
 *
 * @param modifier 交给调用方挂 [RevealCell] 之类的条目级动效
 */
@Composable
private fun InventoryItemCell(
    cell: DepotInventoryCellUi,
    iconLoader: ItemIconLoader,
    synced: Boolean,
    onClick: (DepotInventoryCellUi) -> Unit,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    DepotItemCell(
        cell = DepotItemCellUi(
            key = cell.key,
            itemId = cell.id,
            name = cell.name,
            count = cell.count,
            target = cell.plan?.target,
        ),
        synced = synced,
        iconLoader = iconLoader,
        dimmed = dimmed,
        modifier = modifier,
        onClick = { onClick(cell) },
    )
}

@Composable
private fun OperBoxPage(
    snapshot: OperBoxSnapshot,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onAtTop: (Boolean) -> Unit,
) {
    val resourceDataManager: ResourceDataManager = koinInject()
    var detail by remember { mutableStateOf<OperBoxOperator?>(null) }
    val gridState = rememberLazyGridState()
    // 同「物品」页：把是否停在顶部报给外层，头部收起才不会被列表滚动叠加成 2 倍速
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }.collect { onAtTop(it) }
    }

    if (!snapshot.hasSynced) {
        OperBoxEmptyHint()
        return
    }

    // 与「任务 → 小工具 → 干员识别」用同一套展示：帕拉斯置顶 + 头像卡网格，点开看详情
    val operators = remember(snapshot.owned, snapshot.notOwned, selectedTab) {
        if (selectedTab == 0) {
            snapshot.owned.sortedByDescending { it.isPallas }
        } else {
            snapshot.notOwned
        }
    }

    detail?.let { oper ->
        OperBoxDetailDialog(
            oper = oper,
            profession = resourceDataManager.getCharacterById(oper.id)?.profession,
            onDismiss = { detail = null },
        )
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(72.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MaaDesignTokens.Spacing.md),
        contentPadding = PaddingValues(top = 6.dp, bottom = MaaDesignTokens.Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.lg),
                ) {
                    val labels = listOf(
                        stringResource(R.string.panel_operbox_tab_owned, snapshot.owned.size),
                        stringResource(R.string.panel_operbox_tab_not_owned, snapshot.notOwned.size),
                    )
                    labels.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedTab == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (selectedTab == index) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onTabChange(index) }
                                .padding(vertical = MaaDesignTokens.Spacing.xs),
                        )
                    }
                }
                if (snapshot.syncTimeMillis > 0L) {
                    Text(
                        text = stringResource(
                            R.string.panel_toolbox_last_sync,
                            formatToolboxSyncTime(snapshot.syncTimeMillis),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        items(operators, key = { it.id }) { oper ->
            OperBoxCard(
                oper = oper,
                profession = resourceDataManager.getCharacterById(oper.id)?.profession,
                onClick = { detail = oper },
            )
        }
    }
}

@Composable
private fun DetailEmptyText(textRes: Int) {
    Text(
        text = stringResource(textRes),
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaaDesignTokens.Spacing.xxl),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * 干员页的空状态。
 *
 * 工具箱那边是整页引导（大标题 + 「点击下方开始任务」），搬到设置页会指错按钮；这里只
 * 说清两件事：这个配置档还没有数据、去哪儿跑识别能补上，以及识别完之后这里能看到什么。
 */
@Composable
private fun OperBoxEmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaaDesignTokens.Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.depot_inventory_empty_operators),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.panel_operbox_hint_results),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ============================== 导出 ==============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepotInventoryExportBottomSheet(
    profileId: String,
    titleLabel: String,
    exportScope: CoroutineScope,
    exportingPng: MutableState<Boolean>,
    pngExportJob: MutableState<Job?>,
    pngExportToken: MutableState<Any?>,
    pngRenderBusy: MutableState<Boolean>,
    onDismiss: () -> Unit,
    viewModel: DepotInventoryViewModel,
    exporter: ToolboxFileExporter,
    exportLabels: OperBoxExportLabels,
    exportService: ToolboxExportService = koinInject(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val failedMsg = stringResource(R.string.toolbox_export_file_failed)
    val chooserTitle = stringResource(R.string.toolbox_export_chooser_title)
    // 图里的文案在这里解析好再传进去：ViewModel 不碰 Context
    val depotPngLabels = DepotPngLabels(
        itemsCountFormat = stringResource(R.string.depot_inventory_png_items),
        empty = stringResource(R.string.depot_inventory_png_empty),
    )
    val operBoxPngLabels = OperBoxPngLabels(
        summaryFormat = stringResource(R.string.depot_inventory_png_oper_summary),
        empty = stringResource(R.string.depot_inventory_png_empty),
        operMetaFormat = stringResource(R.string.depot_inventory_png_oper_meta),
        ownedHeaderFormat = stringResource(R.string.panel_operbox_tab_owned),
        notOwnedHeaderFormat = stringResource(R.string.panel_operbox_tab_not_owned),
    )
    var section by remember { mutableStateOf<ExportSection?>(null) }
    var hideProfileLabel by remember { mutableStateOf(false) }
    // 干员数据里「未拥有」占大多数，分享时经常只想给对方看已练的，默认仍全给
    var includeNotOwned by remember { mutableStateOf(true) }

    // 渲染是重活：一张图最多 MAX_EXPORT_PIXELS 个像素（≈48MB 位图）+ PNG 编码缓冲，
    // 连点会并发两张，低内存机直接 OOM，还会弹两个分享面板。
    // 这三个状态由页面持有（见 DepotInventoryView），面板重开也不会重置。
    // 可见态 = 「UI 认为在渲染」或「真的还有 job 没结束」：
    // 关面板会立刻清 exportingPng（消除假禁用），但被取消的 job 可能仍在编码，
    // 这时若只看 exportingPng，按钮会显示「可用」、点了却被 gate 静默拦掉（假可用）。
    // 只用来禁用两条位图入口：JSON/Markdown/CSV 不碰位图，渲染期间照常可用
    // （禁用它们只会变成「点了没反应、也没有任何提示」）。
    val rendering = exportingPng.value || pngRenderBusy.value

    // 渲染放在页面的 scope 上（不是 sheet 的）：关闭面板会显式 cancel 这个 job，
    // 但「取消」只能打断逐图标解码这类挂起点，正在 PNG 编码的那一帧仍会跑完，
    // 所以文案上不承诺「立刻停」；出图后直接进系统分享面板（想存文件在面板里选「文件」即可）
    val renderAndShare: (String, suspend () -> ByteArray?) -> Unit = render@{ prefix, render ->
        // 防并发判据必须是「上一个 job 是否真的结束」，不能是 exportingPng：
        // 关面板会为了消除假禁用立刻把它清零，而被取消的 job 可能仍卡在不可中断的 PNG 编码里，
        // 此时再放行就是两张 ≈12M 像素底图并发，低内存机直接 OOM
        if (pngRenderBusy.value) return@render
        val myToken = Any()
        pngExportToken.value = myToken
        exportingPng.value = true
        val job = runCatching {
            exportScope.launch {
            try {
                // 取消（比如转屏、关面板）不能被吞成「导出失败」的 Toast，否则协程取消语义就断了
                val bytes = try {
                    render()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    null
                }
                val intent = bytes?.let {
                    exportService.buildShareIntentBytes(prefix, it, ToolboxExportFileType.PNG)
                }
                if (intent != null) {
                    // 面板可能已被关掉、界面已离开：这种情况下别再弹 chooser
                    currentCoroutineContext().ensureActive()
                    // 后台 Activity 启动可能被 BAL 拦掉（ActivityNotFoundException / SecurityException）：
                    // 不包住就会崩在协程里，而且用户看不到任何反馈
                    val started = try {
                        context.startActivity(Intent.createChooser(intent, chooserTitle))
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Timber.w(e, "Start share chooser failed")
                        false
                    }
                    if (!started) Toast.makeText(context, failedMsg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, failedMsg, Toast.LENGTH_SHORT).show()
                }
                // 面板可能已经被关掉又重开：只在「仍是发起这次渲染的那个面板」时收起来
                if (pngExportToken.value === myToken) {
                    pngExportToken.value = null
                    onDismiss()
                }
            } finally {
                // 只有「仍是自己」时才解锁：旧 job 被取消后不能提前放开并发窗口，
                // 否则重开面板会在没有任何渲染的情况下把所有条目禁掉几秒
                if (pngExportToken.value === null || pngExportToken.value === myToken) {
                    exportingPng.value = false
                }
            }
        }
        }.getOrElse {
            // 极端情况下 `launch` 自己抛异常（dispatcher/Executor 已关闭）：
            // 没有 job 能完成 `invokeOnCompletion`，必须在这里把状态还原，否则按钮永久禁用
            Timber.w(it, "Launch export render failed")
            exportingPng.value = false
            pngExportToken.value = null
            return@render
        }
        pngExportJob.value = job
        // 先拿到 job 再置忙：`launch` 极端失败时不会留下一个永远关不上的并发窗口
        pngRenderBusy.value = true
        // launch 之后、body 进入之前就被取消（scope 已取消 / 调度前 cancel）时 try/finally 不会执行，
        // 这个回调是唯一能兜住「按钮永久禁用 / 并发窗口永久关不上」的地方。
        // 它一定在 body 真正结束后才跑，所以这里清的是「渲染真的结束了」这个事实。
        job.invokeOnCompletion {
            pngRenderBusy.value = false
            if (pngExportToken.value === myToken) {
                pngExportToken.value = null
                exportingPng.value = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            // 关面板即取消这次渲染：不然几秒后会在已经离开的界面上弹出分享面板
            pngExportJob.value?.cancel()
            pngExportToken.value = null
            // 被取消的 job 还可能卡在不可中断的 PNG 编码里（finally 尚未跑），
            // 这时立刻解锁，免得重开面板看到「正在生成图片」的假禁用态
            exportingPng.value = false
            onDismiss()
        },
        sheetState = sheetState,
        // 与库存保持面板同一个理由：默认的 surfaceContainerLow 是 M3 基线的紫调，跟页面底色对不上
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = MaaDesignTokens.Spacing.sm),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.depot_inventory_export_title, titleLabel),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaaDesignTokens.Spacing.lg,
                            vertical = MaaDesignTokens.Spacing.md,
                        ),
                )
                // 进了二级（仓库/干员）就只能整片关掉重来，给一条回一级的路
                if (section != null) {
                    IconButton(
                        onClick = { section = null },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = MaaDesignTokens.Spacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.accessibility_navigation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 一级 ↔ 二级横向推入推出，动效与页面内的 二级↔三级 一致；
            // 两级高度不同，交给 SizeTransform 收放，否则面板会在切换瞬间跳一下
            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    val forward = initialState == null
                    val spec = tween<IntOffset>(220, easing = MaaMotion.Emphasized)
                    val enter = slideInHorizontally(animationSpec = spec) {
                        if (forward) it / 5 else -it / 5
                    } + fadeIn(tween(200))
                    val exit = slideOutHorizontally(animationSpec = spec) {
                        if (forward) -it / 5 else it / 5
                    } + fadeOut(tween(160))
                    (enter togetherWith exit).using(SizeTransform())
                },
                label = "depot-export-section",
            ) { current ->
                Column {
                    if (current != null) {
                        ExportSwitchRow(
                            titleRes = R.string.depot_inventory_export_hide_profile,
                            hintRes = R.string.depot_inventory_export_hide_profile_hint,
                            checked = hideProfileLabel,
                            onCheckedChange = { hideProfileLabel = it },
                        )
                        if (current == ExportSection.OPERBOX) {
                            ExportSwitchRow(
                                titleRes = R.string.depot_inventory_export_include_not_owned,
                                hintRes = R.string.depot_inventory_export_include_not_owned_hint,
                                checked = includeNotOwned,
                                onCheckedChange = { includeNotOwned = it },
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                horizontal = MaaDesignTokens.Spacing.lg,
                                vertical = MaaDesignTokens.Spacing.xs,
                            ),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }

                    when (current) {
                        null -> {
                            SheetItem(
                                text = stringResource(R.string.depot_inventory_export_depot),
                                icon = Icons.Default.Inventory2,
                                onClick = { section = ExportSection.DEPOT },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = MaaDesignTokens.Spacing.lg),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            SheetItem(
                                text = stringResource(R.string.depot_inventory_export_operbox),
                                icon = Icons.Default.Person,
                                onClick = { section = ExportSection.OPERBOX },
                            )
                        }

                        ExportSection.DEPOT -> {
                            SheetItem(
                                text = stringResource(R.string.depot_inventory_export_arkplanner),
                                icon = Icons.Default.Share,
                                onClick = {
                                    exporter.export(
                                        "depot_arkplanner",
                                        viewModel.exportDepotArkPlanner(profileId),
                                        ToolboxExportFileType.JSON,
                                    )
                                    onDismiss()
                                },
                            )
                            SheetItem(
                                text = stringResource(R.string.depot_inventory_export_lolicon),
                                icon = Icons.Default.Share,
                                onClick = {
                                    exporter.export(
                                        "depot_lolicon",
                                        viewModel.exportDepotLolicon(profileId),
                                        ToolboxExportFileType.JSON,
                                    )
                                    onDismiss()
                                },
                            )
                            SheetItem(
                                text = if (rendering) {
                                    stringResource(R.string.depot_inventory_export_rendering)
                                } else {
                                    stringResource(R.string.depot_inventory_export_image)
                                },
                                icon = Icons.Default.Image,
                                enabled = !rendering,
                                onClick = {
                                    renderAndShare("depot") {
                                        viewModel.renderDepotPng(
                                            profileId = profileId,
                                            hideProfileLabel = hideProfileLabel,
                                            titleLabel = titleLabel,
                                            labels = depotPngLabels,
                                        )
                                    }
                                },
                            )
                        }

                        ExportSection.OPERBOX -> {
                            SheetItem(
                                text = "JSON",
                                icon = Icons.Default.Share,
                                onClick = {
                                    exporter.export(
                                        "operbox",
                                        viewModel.exportOperBoxJson(profileId, includeNotOwned),
                                        ToolboxExportFileType.JSON,
                                    )
                                    onDismiss()
                                },
                            )
                            SheetItem(
                                text = "Markdown",
                                icon = Icons.Default.Share,
                                onClick = {
                                    exporter.export(
                                        "operbox",
                                        viewModel.exportOperBoxMarkdown(profileId, exportLabels, includeNotOwned),
                                        ToolboxExportFileType.MARKDOWN,
                                    )
                                    onDismiss()
                                },
                            )
                            SheetItem(
                                text = "CSV",
                                icon = Icons.Default.Share,
                                onClick = {
                                    exporter.export(
                                        "operbox",
                                        viewModel.exportOperBoxCsv(profileId, exportLabels, includeNotOwned),
                                        ToolboxExportFileType.CSV,
                                    )
                                    onDismiss()
                                },
                            )
                            SheetItem(
                                text = if (rendering) {
                                    stringResource(R.string.depot_inventory_export_rendering)
                                } else {
                                    stringResource(R.string.depot_inventory_export_image)
                                },
                                icon = Icons.Default.Image,
                                enabled = !rendering,
                                onClick = {
                                    renderAndShare("operbox") {
                                        viewModel.renderOperBoxPng(
                                            profileId = profileId,
                                            hideProfileLabel = hideProfileLabel,
                                            titleLabel = titleLabel,
                                            labels = operBoxPngLabels,
                                            includeNotOwned = includeNotOwned,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(MaaDesignTokens.Spacing.sm))
        }
    }
}

private enum class ExportSection { DEPOT, OPERBOX }

/** 二级菜单共用的一行开关：副标题说清打开/关掉会发生什么。 */
@Composable
private fun ExportSwitchRow(
    titleRes: Int,
    hintRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaaDesignTokens.Spacing.lg,
                vertical = MaaDesignTokens.Spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = MaaDesignTokens.Spacing.md),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SheetItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/** 收起态标题栏里名字与「更新时间」的宽度比例：时间占 2 份，保证「更新时间：…」整句放得下，
 *  名字只在真的很长时才省略。 */
private const val TITLE_TIME_WEIGHT = 2f

/**
 * 头部随滚动「整体上移 + 裁切」，不是只缩高度：
 * 高度按 (1 - progress) 收缩、内容按 progress 往上平移同样的距离，
 * 于是里面的字是「往上走出去」的 —— 与 [androidx.compose.animation.shrinkVertically] 的压扁感完全不同。
 */
private fun Modifier.collapsingHeader(progress: () -> Float): Modifier =
    this
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
            )
            val collapsedPx = (placeable.height * progress()).roundToInt().coerceIn(0, placeable.height)
            layout(placeable.width, placeable.height - collapsedPx) {
                placeable.place(0, -collapsedPx)
            }
        }

/**
 * 让配置名从页面头部「飞」到标题栏：起止位置、可用宽度、字号都按滚动进度插值。
 * 中途不淡出、不换字，到终点才把手交给标题栏里那份真名字。
 */
@Composable
private fun HeaderNameFlight(
    profileName: String,
    headerBounds: Rect?,
    barBounds: Rect?,
    progress: Float,
    overlayOrigin: Offset,
) {
    val header = headerBounds ?: return
    val bar = barBounds ?: return
    if (overlayOrigin == Offset.Unspecified) return
    val p = progress.coerceIn(0f, 1f)
    // 收尾时交给标题栏里那份同名文字：两份字形宽度可能差一两像素，叠在一起会显得发虚
    if (p >= 0.995f) return
    val density = LocalDensity.current
    val widthDp = with(density) { lerp(header.width, bar.width, p).toDp() }
    val fontSize = with(density) { lerp(16f, 22f, p).sp }
    Text(
        text = profileName,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = fontSize),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(widthDp)
            .graphicsLayer {
                translationX = lerp(header.left, bar.left, p) - overlayOrigin.x
                translationY = lerp(header.center.y, bar.center.y, p) - overlayOrigin.y - size.height / 2f
            },
    )
}
