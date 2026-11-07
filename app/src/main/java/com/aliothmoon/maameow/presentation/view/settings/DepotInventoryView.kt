package com.aliothmoon.maameow.presentation.view.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.view.panel.ToolboxFileExporter
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotCellGap
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotItemCell
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotItemCellUi
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotPlanEditorSheet
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotPlanOrderColumn
import com.aliothmoon.maameow.presentation.view.panel.depot.DepotSectionBreak
import com.aliothmoon.maameow.presentation.view.panel.depot.depotItemColumns
import com.aliothmoon.maameow.presentation.view.panel.depot.toOrderEntry
import com.aliothmoon.maameow.presentation.view.panel.operbox.OperBoxCard
import com.aliothmoon.maameow.presentation.view.panel.operbox.OperBoxDetailDialog
import com.aliothmoon.maameow.presentation.view.panel.rememberOperBoxExportLabels
import com.aliothmoon.maameow.presentation.view.panel.rememberSafToolboxFileExporter
import com.aliothmoon.maameow.presentation.viewmodel.DepotInventoryCellUi
import com.aliothmoon.maameow.presentation.viewmodel.DepotInventoryViewModel
import com.aliothmoon.maameow.presentation.viewmodel.DepotFarmingOrderSection
import com.aliothmoon.maameow.presentation.viewmodel.DepotMaintainPlanUi
import com.aliothmoon.maameow.presentation.viewmodel.DepotPngLabels
import com.aliothmoon.maameow.presentation.viewmodel.DepotProfileRow
import com.aliothmoon.maameow.presentation.viewmodel.OperBoxPngLabels
import com.aliothmoon.maameow.presentation.viewmodel.depotCellKey
import com.aliothmoon.maameow.presentation.viewmodel.farmingOrderSections
import com.aliothmoon.maameow.presentation.viewmodel.groupForDisplay
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaAnimatedVisibility
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.theme.MaaMotion
import com.aliothmoon.maameow.theme.OpaqueTheme
import com.aliothmoon.maameow.utils.i18n.asString
import com.aliothmoon.maameow.utils.i18n.formatToolboxSyncTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** 「库存为 0」那段标题行的 key：展开时要按它把这一段顶回眼前 */
private const val EMPTY_BREAK_KEY = "empty-break"

/** 展开时逐格错开多少毫秒；同一排差几毫秒，看着还是一起长，排与排之间才拉得开 */
private const val EMPTY_REVEAL_STEP_MS = 6L

/** 错开的上限：再往下的格子都在屏幕外，没必要排到几百毫秒之后 */
private const val EMPTY_REVEAL_MAX_MS = 150L

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
                            DateFormat.getDateTimeInstance()
                                .format(Date(row.syncTimeMillis)),
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
    val plans by viewModel.maintainPlans.collectAsStateWithLifecycle()
    val operBox by viewModel.operBoxSnapshot.collectAsStateWithLifecycle()
    val planContext by viewModel.planContext.collectAsStateWithLifecycle()
    // 没识别过仓库时当前库存是未知：格子、面板、刷取顺序都按「--」显示，别写 0
    val synced by viewModel.inventorySynced.collectAsStateWithLifecycle()
    // 存格子的 key 而不是物品 id：同一物品配了多条计划时会有多格，按 id 找只会拿到第一格
    var planSheetCellKey by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.depot_inventory_detail_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
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
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                DateFormat.getDateTimeInstance()
                                    .format(Date(it.syncTimeMillis)),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            InventoryDetailBody(
                cells = cells,
                plans = plans,
                operBox = operBox,
                synced = synced,
                onCellClick = { planSheetCellKey = it.key },
                // 刷取顺序页点一行也回到同一格的面板：那里只有计划，得按格子 key 找回它那一格
                onPlanClick = { plan ->
                    val key = depotCellKey(plan.itemId, plan.nodeId, plan.planIndex)
                    if (cells.any { it.key == key }) planSheetCellKey = key
                },
                onReorderPlans = viewModel::setPlanOrder,
            )
        }
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
                // 这一页的物品由点开的格子定死，不给换
                allowItemPick = false,
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
    plans: List<DepotMaintainPlanUi>,
    operBox: OperBoxSnapshot,
    synced: Boolean,
    onCellClick: (DepotInventoryCellUi) -> Unit,
    onPlanClick: (DepotMaintainPlanUi) -> Unit,
    onReorderPlans: (String, List<Int>) -> Unit,
    iconLoader: ItemIconLoader = koinInject(),
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    // 放在 pager 外面：pager 会把滑走的页面回收掉，放里面一滑回来选择就没了
    var operBoxTab by remember { mutableIntStateOf(0) }
    // 同理，「库存为 0」那段的折叠状态也放外面；默认收起，几十个空格子不该占着屏
    var emptyCollapsed by rememberSaveable { mutableStateOf(true) }
    val pageLabels = listOf(
        stringResource(R.string.depot_inventory_tab_items),
        stringResource(R.string.depot_inventory_tab_operators),
        stringResource(R.string.depot_inventory_tab_order),
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
                )

                1 -> OperBoxPage(
                    snapshot = operBox,
                    selectedTab = operBoxTab,
                    onTabChange = { operBoxTab = it },
                )

                else -> DepotFarmingOrderPage(
                    plans = plans,
                    iconLoader = iconLoader,
                    synced = synced,
                    onPlanClick = onPlanClick,
                    onReorder = onReorderPlans,
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
) {
    if (cells.isEmpty()) {
        DetailEmptyText(R.string.depot_inventory_empty_items)
        return
    }
    // 一次遍历分组，且只在 cells 真变了才重算（否则每次重组都白跑两遍 filter）
    val groups = remember(cells) { cells.groupForDisplay() }
    val gridState = rememberLazyGridState()
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
) {
    val resourceDataManager: ResourceDataManager = koinInject()
    var detail by remember { mutableStateOf<OperBoxOperator?>(null) }

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

// ============================== 三级：刷取顺序 ==============================

/**
 * 刷取顺序：把库存保持计划按要刷的先后排开，拖右侧手柄换位。
 *
 * 顺序就是执行顺序 —— 链上节点顺序 × 节点内计划顺序，和运行日志逐条跑下去的顺序一致。
 * 行号是**这一页可见计划**的第几条（未选物品的计划不在这页，所以节点里有那种计划时，
 * 行号会比运行日志里的 `#N` 小；那种计划本来也跑不了）。
 * 已集齐的照样列在这里、也参与排序（以后抬高目标就按这个位置来），但执行时会被跳过，
 * 所以标绿；未集齐的才是真要去刷的，用正常前景色。
 *
 * @param onReorder 松手后写回：节点 ID + 该段计划在节点 `plans` 里的新下标顺序
 */
@Composable
private fun DepotFarmingOrderPage(
    plans: List<DepotMaintainPlanUi>,
    iconLoader: ItemIconLoader,
    synced: Boolean,
    onPlanClick: (DepotMaintainPlanUi) -> Unit,
    onReorder: (String, List<Int>) -> Unit,
) {
    if (plans.isEmpty()) {
        DetailEmptyText(R.string.depot_inventory_order_empty)
        return
    }
    // 只有落手（onSettle）那一次才改这份；拖动期间由库自己拿 offset 摆位
    var sections by remember(plans) { mutableStateOf(plans.farmingOrderSections()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = MaaDesignTokens.Spacing.md,
                vertical = MaaDesignTokens.Spacing.xs,
            ),
        verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.depot_inventory_order_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sections.forEach { section ->
            // 节点 ID 单独取出来：落手回调要按它回查当时的 sections，不能闭包住组合时那一份
            val nodeId = section.nodeId
            FarmingOrderSectionHeader(section)
            // 一段一个列表：跨段拖动会把计划挪进另一个库存保持节点、连带换掉那个节点的
            // 药/石/连战设置，不是这一页该做的事；分开之后跨段在结构上就拖不过去。
            //
            // 行的样子、进度口径、去向文案、拖动行为都在 DepotPlanOrderList 里，
            // 后台任务的库存保持计划概览用的是同一份，两边不会各长各的。
            val entries = remember(section.rows) { section.rows.map { it.toOrderEntry() } }
            DepotPlanOrderColumn(
                entries = entries,
                synced = synced,
                iconLoader = iconLoader,
                onClick = { index -> section.rows.getOrNull(index)?.let { onPlanClick(it.plan) } },
                onMove = { from, to ->
                    val current = sections.firstOrNull { it.nodeId == nodeId }
                        ?: return@DepotPlanOrderColumn
                    val rows = current.rows.toMutableList().also { it.add(to, it.removeAt(from)) }
                    // 先换本地这份：库已经把它摆到位了，数据不跟着换就会停在拖过去的样子。
                    // 同时写回磁盘，等 plans 重新发一遍再对齐
                    sections = sections.map { if (it.nodeId == nodeId) it.copy(rows = rows) else it }
                    onReorder(nodeId, rows.map { it.plan.planIndex })
                },
            )
        }
    }
}

/** 一段的标题：节点名，没启用的再补一句「不会执行」。 */
@Composable
private fun FarmingOrderSectionHeader(section: DepotFarmingOrderSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaaDesignTokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = section.nodeName.asString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (!section.nodeEnabled) {
            Text(
                text = stringResource(R.string.depot_inventory_plan_node_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
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
    onDismiss: () -> Unit,
    viewModel: DepotInventoryViewModel,
    exporter: ToolboxFileExporter,
    exportLabels: OperBoxExportLabels,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val failedMsg = stringResource(R.string.toolbox_export_file_failed)
    // 图里的文案在这里解析好再传进去：ViewModel 不碰 Context
    val depotPngLabels = DepotPngLabels(
        itemsCountFormat = stringResource(R.string.depot_inventory_png_items),
        empty = stringResource(R.string.depot_inventory_png_empty),
    )
    val operBoxPngLabels = OperBoxPngLabels(
        summaryFormat = stringResource(R.string.depot_inventory_png_oper_summary),
        empty = stringResource(R.string.depot_inventory_png_empty),
        operMetaFormat = stringResource(R.string.depot_inventory_png_oper_meta),
    )
    var section by remember { mutableStateOf<ExportSection?>(null) }
    var hideProfileLabel by remember { mutableStateOf(false) }

    // 渲染放在页面的 scope 上：面板一收就取消的话，几秒的渲染会白跑一趟还没有任何提示
    val renderAndExport: (String, suspend () -> ByteArray?) -> Unit = { prefix, render ->
        exportScope.launch {
            val bytes = runCatching { render() }.getOrNull()
            if (bytes != null) {
                exporter.exportBytes(prefix, bytes, ToolboxExportFileType.PNG)
            } else {
                Toast.makeText(context, failedMsg, Toast.LENGTH_SHORT).show()
            }
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                        HideProfileLabelRow(
                            checked = hideProfileLabel,
                            onCheckedChange = { hideProfileLabel = it },
                        )
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
                                text = stringResource(R.string.depot_inventory_export_image),
                                icon = Icons.Default.Image,
                                onClick = {
                                    renderAndExport("depot") {
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
                                        viewModel.exportOperBoxJson(profileId),
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
                                        viewModel.exportOperBoxMarkdown(profileId, exportLabels),
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
                                        viewModel.exportOperBoxCsv(profileId, exportLabels),
                                        ToolboxExportFileType.CSV,
                                    )
                                    onDismiss()
                                },
                            )
                            SheetItem(
                                text = stringResource(R.string.depot_inventory_export_image),
                                icon = Icons.Default.Image,
                                onClick = {
                                    renderAndExport("operbox") {
                                        viewModel.renderOperBoxPng(
                                            profileId = profileId,
                                            hideProfileLabel = hideProfileLabel,
                                            titleLabel = titleLabel,
                                            labels = operBoxPngLabels,
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

/** 二级菜单共用的一行：PNG 图头要不要写配置档名。 */
@Composable
private fun HideProfileLabelRow(
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
                text = stringResource(R.string.depot_inventory_export_hide_profile),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.depot_inventory_export_hide_profile_hint),
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
) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
