package com.aliothmoon.maameow.presentation.view.panel.depot

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.DepotPlanOutcome
import com.aliothmoon.maameow.data.model.LogColorRole
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.data.resource.StageAliasMapper
import com.aliothmoon.maameow.presentation.components.ItemIcon
import com.aliothmoon.maameow.presentation.components.SectionHeader
import com.aliothmoon.maameow.presentation.viewmodel.DepotPlanContext
import com.aliothmoon.maameow.presentation.viewmodel.depotProgressText
import com.aliothmoon.maameow.theme.LocalReduceMotion
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.theme.MaaMotion
import com.aliothmoon.maameow.theme.OpaqueTheme
import com.aliothmoon.maameow.theme.themedColor
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 一条库存保持计划的详情设置面板。
 *
 * 库存数据页点一格、后台任务的库存保持清单点一行，弹出来的都是这一份 —— 字段、校验、
 * 保存/移除的节奏只有一处实现，两边不会各长各的。写回落到哪里由调用方在
 * [onSave] / [onRemove] 里决定（那边走 ViewModel 按节点+下标定位，
 * 这边直接改当前节点的计划列表）。
 *
 * 这里只有「这一条计划」的字段：作用于全部计划的执行类选项（任务行为 / 连战 / 药石）
 * 在库存保持面板顶部的「高级设置」里，不跟着计划走，所以面板拿不到整份 config。
 *
 * 面板的三种形态。声明顺序就是「越往后越深」，切换方向按它算：
 * 摘要卡 → 编辑表单是往前推，反着来（目前没有这条路）就往回推。
 */
private enum class DepotPlanSheetMode { UNSUPPORTED, SUMMARY, EDITOR }

/**
 * @param itemId 要配的物品；空串 = 老配置里「添加计划」留下的空行。面板里不提供物品选择，
 *   这种行只能删掉（它本来也跑不起来，dropId 空会被执行侧拒掉）
 * @param itemName 物品名，调用方解析好（认不出来的按「未选择」兜底）
 * @param count 该物品当前的库存数量
 * @param existing 这个物品已有的计划；null = 还没有，先出一张摘要卡再进表单
 * @param existingOutcome 已有计划在本次运行里的去向，用来提示「配了也跑不起来」；null = 没有计划
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DepotPlanEditorSheet(
    itemId: String,
    itemName: String,
    count: Int,
    /**
     * 按物品 id 取当前库存：老计划里的 dropId 与点开的那一行不一定相同（历史数据、导入的配置），
     * 「现在有多少」要按计划自己的 dropId 取；默认仍返回 [count]，兼容只传数量的调用方。
     */
    countOf: (String) -> Int = { count },
    existing: DepotMaintainPlan?,
    existingOutcome: DepotPlanOutcome?,
    context: DepotPlanContext,
    synced: Boolean,
    showMedicine: Boolean = false,
    showStone: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (DepotMaintainPlan) -> Unit,
    onRemove: () -> Unit,
    itemHelper: ItemHelper = koinInject(),
    activityManager: ActivityManager = koinInject(),
    iconLoader: ItemIconLoader = koinInject(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // 关卡表与材料表都是全 App 同一份，别再各写一遍过滤逻辑（见 DepotPlanFields）
    val stageGroups = rememberDepotStageGroups(activityManager)
    val stageCodes = remember(stageGroups) { stageGroups.allStageCodes() }
    val itemIds = rememberDepotItemIds(itemHelper)
    // 名字映射用全表：老计划里可能存着已经不在识别列表里的 id
    val itemIndex by itemHelper.items.collectAsStateWithLifecycle()
    val itemNameMap = remember(itemIndex) { itemIndex.mapValues { it.value.name } }
    // 已经有计划的物品永远可编辑：老数据里可能留着识别集合之外的物品（比如赤金），
    // 只按列表放行的话那条计划在这里既改不了也删不掉
    val maintainable = existing != null || itemId in itemIds

    // 没计划的物品不直接铺表单：先出一张只讲「是什么、现在有多少」的摘要卡。
    // 直接进表单会凭空算出一个「144 / 144 · 已够」的计划，
    // 看着像已经配好了，其实什么都没配。
    var creating by remember(itemId) { mutableStateOf(false) }
    val mode = when {
        !maintainable -> DepotPlanSheetMode.UNSUPPORTED
        existing != null || creating -> DepotPlanSheetMode.EDITOR
        else -> DepotPlanSheetMode.SUMMARY
    }
    val reduceMotion = LocalReduceMotion.current

    var draft by remember(itemId, existing) {
        mutableStateOf(
            existing ?: DepotMaintainPlan(
                dropId = itemId,
                // 默认保到现在的数量：不抬高目标就不会凭空多刷
                dropCount = count.coerceAtLeast(1),
            ),
        )
    }
    // 当前草稿所指物品的库存（面板里换过物品之后，不能再拿点开那一格的数）
    val currentCount = countOf(draft.dropId)

    // 关卡列表里选「自定义」才放出输入框；已有计划用的是列表外的代码时默认就是它。
    // 不再有任务级的「手动输入关卡名」开关：手动输入是「这一条计划」的编辑动作，
    // 入口就在表单里的「自定义」按钮上，全局开关带不来任何额外能力。
    // 只按 itemId 初始化：这是用户意图，不能被关卡表的热更新重置掉。
    var customStage by remember(itemId) {
        mutableStateOf(existing?.stage?.let { it.isNotBlank() && it !in stageCodes } ?: false)
    }

    // 数字框被清空时（中间态）不要静默沿用上一个值，直接禁掉保存
    var targetBlank by remember(itemId) { mutableStateOf(false) }

    // 面板收起来之后才落库：先滑下去再改数据，动画期间不会看到标题/按钮跳变
    var closing by remember { mutableStateOf(false) }
    val closeWithAnimation: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            scope.launch {
                try {
                    sheetState.hide()
                } finally {
                    onDismiss()
                    action()
                }
            }
        }
    }

    // 目标库存跟着草稿实时算，改数字时右上的「已够/缺多少」立刻跟着变
    val draftTarget = draft.dropCount.coerceIn(1, MAX_DEPOT_TARGET_INVENTORY)
    val short = draftTarget > currentCount
    // 放开物品选择时草稿里的物品会被换掉，抬头得跟着它走；定死的那几处就是点开时那一个
    val headerItemId = draft.dropId.ifBlank { itemId }
    val headerName = itemNameMap[headerItemId] ?: itemName

    // 玻璃背景下面板会透出底下的内容，这里换回不透明配色
    OpaqueTheme {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            // M3 的面板默认取 surfaceContainerLow，而本 App 的配色只定义了 surface 一族，
            // 那一档会落回 M3 基线的紫调中性色，和页面底色（background）不是一个颜色。
            // 面板要跟页面同色，就直接取 background。
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            // 摘要卡 → 编辑表单是同一张面板换形态：横向推入推出，高度交给 SizeTransform 收放，
            // 参数与导出面板的 一级↔二级 完全一致；系统关掉动画时直接换
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    if (reduceMotion) {
                        (EnterTransition.None togetherWith ExitTransition.None).using(null)
                    } else {
                        val forward = targetState.ordinal > initialState.ordinal
                        val spec = tween<IntOffset>(MaaMotion.Medium, easing = MaaMotion.Emphasized)
                        // 推 1/3 屏：面板里换的是「一整屏设置」，只挪一点点会被当成直接换掉了
                        val enter = slideInHorizontally(animationSpec = spec) {
                            if (forward) it / 3 else -it / 3
                        } + fadeIn(tween(durationMillis = MaaMotion.Fast, easing = MaaMotion.Linear))
                        val exit = slideOutHorizontally(animationSpec = spec) {
                            if (forward) -it / 3 else it / 3
                        } + fadeOut(tween(durationMillis = MaaMotion.Fast, easing = MaaMotion.Linear))
                        (enter togetherWith exit).using(SizeTransform())
                    }
                },
                label = "depot-plan-mode",
                modifier = Modifier.fillMaxWidth(),
            ) { current ->
                val editing = current == DepotPlanSheetMode.EDITOR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(
                            start = MaaDesignTokens.Spacing.lg,
                            end = MaaDesignTokens.Spacing.lg,
                            bottom = MaaDesignTokens.Spacing.lg,
                        ),
                    verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.md),
                    ) {
                        ItemIcon(
                            itemId = headerItemId,
                            modifier = Modifier.size(40.dp),
                            contentDescription = headerName,
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
                                text = if (editing) {
                                    // 与格子、与后台任务的计划概览同一个顺序、同一份实现
                                    depotProgressText(currentCount, draftTarget, synced)
                                } else {
                                    stringResource(
                                        R.string.depot_inventory_plan_current,
                                        if (synced) "$currentCount" else "--",
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 已够/缺多少贴在面板最右，两行高的大字，绿=够 红=缺。
                        // 只在编辑态出现：没有计划时谈「够不够」是空话
                        if (editing) {
                            Text(
                                text = if (short) {
                                    stringResource(
                                        R.string.depot_inventory_maintain_need,
                                        draftTarget - currentCount,
                                    )
                                } else {
                                    stringResource(R.string.depot_inventory_maintain_enough)
                                },
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (short) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    LogColorRole.SUCCESS.themedColor()
                                },
                                textAlign = TextAlign.End,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                // 无约束的话 10 位缺口会先把左边的物品名挤没
                                modifier = Modifier.widthIn(max = 132.dp),
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (headerItemId in itemHelper.currencyItemIds) {
                        Text(
                            text = stringResource(R.string.depot_plan_currency_inventory_tip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (current == DepotPlanSheetMode.UNSUPPORTED) {
                        Text(
                            text = stringResource(R.string.depot_inventory_plan_unsupported),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (current == DepotPlanSheetMode.SUMMARY) {
                        // 摘要卡到此为止：设置留到下一屏，按钮是卡片唯一的出口
                        Button(
                            onClick = { creating = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(stringResource(R.string.depot_inventory_plan_new))
                        }
                    } else {
                        SectionHeader(
                            title = if (existing == null) {
                                stringResource(R.string.depot_inventory_plan_new)
                            } else {
                                stringResource(R.string.depot_inventory_maintain_title)
                            },
                        )

                        // 字段本体与任务配置页共用一份，见 DepotPlanFields
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
                            // 物品种类由入口定死（点开的格子 / 那一行），表单里不再出选择器
                            // 药/石是任务级选项：任务配置页逐条给开关，库存数据页不给
                            showMedicine = showMedicine,
                            showStone = showStone,
                            onTargetBlankChange = { targetBlank = it },
                        )

                        if (existing != null && existingOutcome != null) {
                            PlanOutcomeHint(existingOutcome, existing.needAgainst(currentCount), existing.stage)
                        }

                        // 手输的关卡码可能写错或今天没开，提前说一声，别等跑起来才发现被跳过。
                        // 已有计划的 StageClosed 已经由上面的 PlanOutcomeHint 说过一次，这里不重复
                        if (draft.stage.isNotBlank() &&
                            existingOutcome != DepotPlanOutcome.StageClosed &&
                            !activityManager.isStageOpen(draft.stage)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.depot_inventory_maintain_stage_closed,
                                    draft.stage,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        if (!context.nodeEnabled) {
                            Text(
                                text = stringResource(R.string.depot_inventory_plan_node_disabled),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.sm),
                        ) {
                            if (existing != null) {
                                OutlinedButton(
                                    onClick = { closeWithAnimation(onRemove) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Text(stringResource(R.string.depot_inventory_plan_remove))
                                }
                            }
                            Button(
                                onClick = {
                                    closeWithAnimation {
                                        onSave(
                                            draft.copy(
                                                dropCount = draftTarget,
                                                // 点「保存」时输入框可能还带着焦点，别名在这里兜一次
                                                stage = StageAliasMapper
                                                    .mapToStageCode(draft.stage, stageCodes),
                                            ),
                                        )
                                    }
                                },
                                // 目标框空着就别让保存，免得把上一次的数字当成新目标写进去
                                enabled = !targetBlank,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text(stringResource(R.string.depot_inventory_plan_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 已保存计划里那些「配了也跑不起来」的原因，与运行日志同一套口径。 */
@Composable
private fun PlanOutcomeHint(outcome: DepotPlanOutcome, need: Int, stage: String) {
    // 能跑的计划，缺口由 DepotPlanFields 按草稿实时算
    if (outcome == DepotPlanOutcome.Enough || outcome == DepotPlanOutcome.Runnable) return
    Text(
        text = depotPlanOutcomeLabel(outcome = outcome, need = need, stage = stage),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/** 还差多少才到目标；目标为 0 或已超目标时算 0，别报负数 */
private fun DepotMaintainPlan.needAgainst(current: Int): Int = (dropCount - current).coerceAtLeast(0)
