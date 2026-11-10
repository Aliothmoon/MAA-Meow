package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.DepotMaintainConfig
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.DepotPlanOutcome
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.depotPlanOutcome
import com.aliothmoon.maameow.data.model.nameUiText
import com.aliothmoon.maameow.data.model.toolbox.OperBoxExportFormatter
import com.aliothmoon.maameow.data.model.toolbox.OperBoxExportLabels
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.DepotSnapshot
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.repository.OperBoxSnapshot
import com.aliothmoon.maameow.data.repository.toArkPlannerJson
import com.aliothmoon.maameow.data.repository.toExportList
import com.aliothmoon.maameow.data.repository.toExportSections
import com.aliothmoon.maameow.data.repository.toLoliconJson
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.data.resource.ItemInfo
import com.aliothmoon.maameow.data.resource.OperAvatarLoader
import com.aliothmoon.maameow.data.resource.OperRoleIconLoader
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.presentation.components.RARE_SUMMON_GRADIENT_LIGHT
import com.aliothmoon.maameow.presentation.components.pallasCheerText
import com.aliothmoon.maameow.utils.i18n.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aliothmoon.maameow.utils.i18n.formatToolboxSyncTime
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 库存数据页（二级配置档列表 + 三级配置档详情）。
 *
 * 仓库/干员快照按「配置档」分片，一档一份，与 [com.aliothmoon.maameow.data.repository.ProfileShardStore]
 * 的键一致；列表页只读各档汇总，详情页才展开明细。
 *
 * 库存明细与「库存保持」计划合并成同一批格子（[cells]）：物品的当前库存就是保持计划的进度，
 * 分成「有库存 / 未集齐 / 库存为 0」三段展示，点格子可直接改该物品的目标库存。
 */
class DepotInventoryViewModel(
    private val depotRepository: DepotRepository,
    private val operBoxRepository: OperBoxRepository,
    private val taskChainState: TaskChainState,
    private val itemHelper: ItemHelper,
    private val itemIconLoader: ItemIconLoader,
    private val operAvatarLoader: OperAvatarLoader,
    private val activityManager: ActivityManager,
    private val operRoleIconLoader: OperRoleIconLoader,
    private val resourceDataManager: ResourceDataManager,
    private val appContext: Context,
) : ViewModel() {

    val profiles: StateFlow<List<TaskProfile>> = taskChainState.profiles

    /** "" = 停在列表页；非空 = 已进入某档详情 */
    private val manualSelection = MutableStateFlow("")

    val selectedProfileId: StateFlow<String> = manualSelection.asStateFlow()

    val selectedProfile: StateFlow<TaskProfile?> =
        combine(taskChainState.profiles, selectedProfileId) { list, id ->
            list.firstOrNull { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // 选中的配置档被删掉时退回列表页：留在详情页会是一个空网格，
        // 点保存也只会走到「配置档不存在」那条静默放弃的分支
        viewModelScope.launch {
            taskChainState.profiles.collect { list ->
                val current = manualSelection.value
                if (current.isNotEmpty() && list.none { it.id == current }) {
                    manualSelection.value = ""
                }
            }
        }
    }

    /** 进入三级详情；只改本页选中项，不动全局活跃档，避免「看一眼」产生副作用。 */
    fun selectProfile(profileId: String) {
        manualSelection.value = profileId
    }

    fun clearSelection() {
        manualSelection.value = ""
    }

    // ========== 二级：配置档列表 ==========

    /** 每个配置档一张卡片：可抽次数、更新时间、库存保持进度。 */
    val profileRows: StateFlow<List<DepotProfileRow>> = combine(
        taskChainState.profiles,
        taskChainState.profileId,
        depotRepository.snapshots,
        operBoxRepository.snapshots,
    ) { profileList, activeId, depotMap, operMap ->
        profileList.map { profile ->
            val snapshot = depotMap[profile.id] ?: DepotSnapshot()
            val operBox = operMap[profile.id] ?: OperBoxSnapshot()
            val plans = profile.chain.depotPlans()
            DepotProfileRow(
                id = profile.id,
                name = profile.name,
                isActive = profile.id == activeId,
                hasSynced = snapshot.syncTimeMillis > 0L || operBox.hasSynced,
                syncTimeMillis = snapshot.syncTimeMillis,
                planCount = plans.size,
                unmetCount = plans.count { it.unmetIn(snapshot) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ========== 三级：配置档详情 ==========

    val depotSnapshot: StateFlow<DepotSnapshot> =
        combine(depotRepository.snapshots, selectedProfileId) { map, id ->
            map[id] ?: DepotSnapshot()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DepotSnapshot())

    /** 该档是否做过仓库识别：没识别过时「当前库存」是未知，不是 0（见 [depotProgressText]）。 */
    val inventorySynced: StateFlow<Boolean> = depotSnapshot
        .map { it.syncTimeMillis > 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val operBoxSnapshot: StateFlow<OperBoxSnapshot> =
        combine(operBoxRepository.snapshots, selectedProfileId) { map, id ->
            map[id] ?: OperBoxSnapshot()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OperBoxSnapshot())

    /**
     * 所选配置档任务链里所有「库存保持」计划。
     *
     * 判定复用 [depotPlanOutcome]，与执行侧同源；未启用的节点也收进来，
     * 否则用户在这一页看不到、也改不了自己配过但被停用的计划。
     */
    val maintainPlans: StateFlow<List<DepotMaintainPlanUi>> =
        combine(selectedProfile, depotSnapshot, itemHelper.items) { profile, snap, itemMap ->
            profile?.chain.orEmpty().flatMap { node ->
                val config = node.config as? DepotMaintainConfig ?: return@flatMap emptyList()
                config.plans.mapIndexed { index, plan ->
                    plan.toUi(node, index, snap, itemMap) {
                        activityManager.isStageOpen(it)
                    }
                }
                    // 没选物品的计划连物品都算不上，配置页已经在报「未选物品」，
                    // 别在库存网格里留一个没有图标没有名字的空格子
                    .filter { it.itemId.isNotBlank() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 库存格子 = 可刷取目标（识别得出的材料 + 龙门币/采购凭证/家具零件）∪ 快照里的其它物品
     * ∪ 保持计划里的物品，但**排除**明确的杂项（声望这类每场必掉的博士经验）。
     *
     * 目标集合取 [ItemHelper.depotTargetItems]：仓库识别本身只覆盖 MATERIAL，
     * 三样货币靠战斗结算的累计掉落进快照，所以也要出格子；
     * **不能取 [ItemHelper.dropItems]**——那是关卡掉落列表，会把声望（博士经验）
     * 这类根本不该出现在库存页的条目画成一格。
     *
     * 快照里 Core 额外上报的条目（合成玉、寻访凭证）仍然保留：它们确实是库存，
     * 只是不能当刷取目标，点开面板会给出「不能作为刷取目标」的说明。
     * 历史遗留的杂项（早期版本累计下来的声望）不再出格子，数据本身不删，
     * 清空库存时一起清掉。
     *
     * 库里一条记录都没有的材料也要出格子（图标压淡），否则「这个材料我到底有没有」
     * 只能靠数格子猜；计划里的物品即便识别不到（= 0）也要出现，否则「缺多少」
     * 在最需要看的时候反而没有格子。
     */
    val cells: StateFlow<List<DepotInventoryCellUi>> = combine(
        depotSnapshot,
        itemHelper.items,
        itemHelper.depotTargetItems,
        maintainPlans,
    ) { snap, itemMap, depotTargets, plans ->
        buildInventoryCells(
            snapshot = snap,
            itemMap = itemMap,
            depotItemIds = depotTargets.map { it.id },
            plans = plans,
            excludedItemIds = itemHelper.nonTargetItemIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 新建计划时要知道的所属节点状态（只用来提示任务没启用、以及显不显示药/石）。 */
    val planContext: StateFlow<DepotPlanContext> = selectedProfile
        .map { profile -> profile?.chain?.firstDepotNode()?.toPlanContext() ?: DepotPlanContext() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DepotPlanContext())

    /**
     * 库存数据页可以直接改的那份「库存保持」配置（开关 + 计划列表）。
     *
     * 与后台任务里那个节点共用同一份数据，这边改了那边立刻跟着变，不是副本。
     * 该档还没有库存保持节点时给一份默认配置，改动写回时才由 [TaskChainState] 建节点。
     */
    val maintainConfig: StateFlow<DepotMaintainConfigUi> = selectedProfile
        .map { profile -> profile?.chain?.depotMaintainConfigUi() ?: DepotMaintainConfigUi() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DepotMaintainConfigUi())

    /**
     * 写入一条库存保持计划：该物品已有计划就地覆盖，没有则追加。
     * 配置档里还没有库存保持节点时由 [TaskChainState] 就地新建。
     *
     * [existing] 由调用方（面板）直接给出，就是用户点开时看到的那条计划，
     * 不再回查 [cells]——那是个 `WhileSubscribed` 的派生流，拿它的 `value`
     * 当写回定位依据会在没订阅时读到空值。
     */
    fun savePlan(existing: DepotMaintainPlanUi?, plan: DepotMaintainPlan) {
        val profileId = selectedProfileId.value
        if (profileId.isEmpty()) return
        viewModelScope.launch {
            taskChainState.updateDepotMaintainPlans(profileId, existing?.nodeId) { plans ->
                val index = existing?.planIndex ?: -1
                if (index in plans.indices) {
                    plans.toMutableList().also { it[index] = plan }
                } else {
                    plans + plan
                }
            }
        }
    }

    /** 移除该物品的保持计划；没有计划时什么都不做。 */
    fun removePlan(existing: DepotMaintainPlanUi?) {
        val profileId = selectedProfileId.value
        if (profileId.isEmpty() || existing == null) return
        viewModelScope.launch {
            taskChainState.updateDepotMaintainPlans(profileId, existing.nodeId) { plans ->
                plans.filterIndexed { index, _ -> index != existing.planIndex }
            }
        }
    }

    /**
     * 刷取顺序页拖动之后写回一段计划的新顺序。
     *
     * [order] 是这一段可见计划在该节点 `plans` 里的下标，按新的显示顺序给出；
     * 未选物品的占位计划不出现在那一页，重排后留在原位（见 [applyPlanOrder]）。
     *
     * 写回的就是执行顺序：执行侧按链上节点顺序、节点内 `plans` 的下标依次跑。
     */
    fun setPlanOrder(nodeId: String, order: List<Int>) {
        val profileId = selectedProfileId.value
        if (profileId.isEmpty() || nodeId.isEmpty() || order.isEmpty()) return
        viewModelScope.launch {
            taskChainState.updateDepotMaintainPlans(profileId, nodeId) { plans ->
                applyPlanOrder(plans, order)
            }
        }
    }

    /**
     * 写回整份库存保持配置（计划面板里的「常规设置 / 高级设置」）。
     *
     * [nodeId] 由界面给出，就是它显示那份配置时所属的节点；空串表示该档还没有节点，
     * 交给 [TaskChainState] 新建一个。不在这里回查「第一个节点」——面板显示的是哪个节点，
     * 改动就必须落到哪个节点上，否则改的是甲、生效的是乙，而且没有任何提示。
     */
    fun updateMaintainConfig(nodeId: String, config: DepotMaintainConfig) {
        val profileId = selectedProfileId.value
        if (profileId.isEmpty()) return
        viewModelScope.launch {
            taskChainState.updateDepotMaintainConfig(profileId, nodeId.ifEmpty { null }) { config }
        }
    }

    /** 清空指定配置档的仓库与干员数据（非活跃档也能清）。 */
    fun clearProfile(profileId: String) {
        if (profileId.isEmpty()) return
        // 配置档已经删掉时别再写分片：会把 profileDeleted 清过的 key 又建回来
        if (taskChainState.profiles.value.none { it.id == profileId }) return
        depotRepository.clear(profileId)
        operBoxRepository.clear(profileId)
    }

    // ========== 导出 ==========
    //
    // 每个导出入口都显式带 profileId：列表页可以直接导出某一档，
    // 那时本页并没有「选中」那一档，按 selectedProfileId 取数据会取到空。

    fun exportDepotArkPlanner(profileId: String): String =
        snapshotOf(profileId).toArkPlannerJson(itemHelper.items.value)

    fun exportDepotLolicon(profileId: String): String =
        snapshotOf(profileId).toLoliconJson(itemHelper.items.value)

    fun exportOperBoxList(profileId: String, includeNotOwned: Boolean = true): List<OperBoxOperator> =
        (operBoxRepository.snapshots.value[profileId] ?: OperBoxSnapshot())
            .toExportList(includeNotOwned)

    fun exportOperBoxJson(profileId: String, includeNotOwned: Boolean = true): String =
        OperBoxExportFormatter.toJson(exportOperBoxList(profileId, includeNotOwned))

    fun exportOperBoxMarkdown(
        profileId: String,
        labels: OperBoxExportLabels,
        includeNotOwned: Boolean = true,
    ): String = OperBoxExportFormatter.toMarkdown(exportOperBoxList(profileId, includeNotOwned), labels)

    fun exportOperBoxCsv(
        profileId: String,
        labels: OperBoxExportLabels,
        includeNotOwned: Boolean = true,
    ): String = OperBoxExportFormatter.toCsv(exportOperBoxList(profileId, includeNotOwned), labels)

    private fun snapshotOf(profileId: String): DepotSnapshot =
        depotRepository.snapshots.value[profileId] ?: DepotSnapshot()

    /**
     * @param hideProfileLabel 为 true 时图头不写配置档名，只留时间与数据
     * @param titleLabel 图头标题（通常为配置档名）
     * @param labels 图里的文案，由界面用 stringResource 解析后传进来（ViewModel 不碰 Context）
     */
    suspend fun renderDepotPng(
        profileId: String,
        hideProfileLabel: Boolean = false,
        titleLabel: String = "",
        labels: DepotPngLabels,
    ): ByteArray? = withContext(Dispatchers.Default) {
        val snap = snapshotOf(profileId)
        // 没识别过仓库时导出的只会是「（空）」图，别让用户把它当成数据分享出去
        if (snap.syncTimeMillis <= 0L) return@withContext null
        val itemList = snap.toItemUiList(itemHelper.items.value)
        // 收掉档名之后第一行就变成时间了，但它不该被当成大标题画
        val hasTitle = !hideProfileLabel && titleLabel.isNotBlank()
        val header = buildList {
            if (hasTitle) add(titleLabel)
            if (snap.syncTimeMillis > 0L) {
                add(formatToolboxSyncTime(snap.syncTimeMillis))
            }
            add(labels.itemsCountFormat.format(itemList.size))
        }

        val columns = 4
        val cellW = 180
        val cellH = 170
        val gap = 12
        val pad = 24
        val headerLineH = 40
        val headerH = pad + header.size * headerLineH + 12
        val rows = if (itemList.isEmpty()) 1 else ceil(itemList.size / columns.toFloat()).toInt()
        val width = pad * 2 + columns * cellW + (columns - 1) * gap
        val height = headerH + pad + rows * cellH + max(0, rows - 1) * gap + pad

        val scale = exportScale(width, height)
        val bitmap = Bitmap.createBitmap(
            (width * scale).roundToInt(),
            (height * scale).roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        // 底色用界面的背景色；图标模板图的黑底在 ItemIconLoader 里抠成透明，透出这一层
        canvas.drawColor(EXPORT_BG)
        // 之后一律按设计尺寸绘制，超预算时整体缩小（文字、图标一起缩）
        canvas.scale(scale, scale)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ON_SURFACE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ON_SURFACE_VARIANT
            textSize = 26f
        }
        var y = pad + 34f
        header.forEachIndexed { i, line ->
            canvas.drawText(line, pad.toFloat(), y, if (hasTitle && i == 0) titlePaint else subPaint)
            y += headerLineH
        }

        val cellBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EXPORT_CARD }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ON_SURFACE_VARIANT
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ON_SURFACE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
        val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D0D0D0")
        }

        if (itemList.isEmpty()) {
            canvas.drawText(labels.empty, width / 2f, headerH + 80f, namePaint)
        } else {
            itemList.forEachIndexed { index, item ->
                // 纯 CPU 绘制不天然响应取消：每个格子查一次，关面板/bat 取消后尽快停
                currentCoroutineContext().ensureActive()
                val col = index % columns
                val row = index / columns
                val left = pad + col * (cellW + gap)
                val top = headerH + row * (cellH + gap)
                val rect = RectF(
                    left.toFloat(),
                    top.toFloat(),
                    (left + cellW).toFloat(),
                    (top + cellH).toFloat(),
                )
                canvas.drawRoundRect(rect, 16f, 16f, cellBg)

                val icon = ignoreFailure { itemIconLoader.loadRawAndroidBitmap(item.id) }
                val iconSize = 88
                val iconLeft = left + (cellW - iconSize) / 2
                val iconTop = top + 14
                val dst = RectF(
                    iconLeft.toFloat(),
                    iconTop.toFloat(),
                    (iconLeft + iconSize).toFloat(),
                    (iconTop + iconSize).toFloat(),
                )
                if (icon != null && !icon.isRecycled) {
                    // 先把图标压到卡片色上再贴：图标 PNG 的黑底已抠成透明，
                    // 直接画到画布上会在位图上留下 alpha=0 的洞，深色相册/深色聊天
                    // 背景里就透出一圈黑方块；先合成成不透明的小图，到哪都一样
                    val iconOnCard = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
                    val iconCanvas = Canvas(iconOnCard)
                    iconCanvas.drawColor(EXPORT_CARD)
                    iconCanvas.drawBitmap(
                        icon,
                        null,
                        RectF(0f, 0f, iconSize.toFloat(), iconSize.toFloat()),
                        iconPaint,
                    )
                    canvas.drawBitmap(iconOnCard, null, dst, null)
                    iconOnCard.recycle()
                    icon.recycle()
                } else {
                    canvas.drawRoundRect(dst, 8f, 8f, placeholder)
                }

                val cx = left + cellW / 2f
                val name = item.name.let { if (it.length > 8) it.take(7) + "…" else it }
                canvas.drawText(name, cx, (top + iconSize + 40).toFloat(), namePaint)
                canvas.drawText("${item.count}", cx, (top + iconSize + 72).toFloat(), countPaint)
            }
        }

        // 底图是不透明 ARGB，直接声明无 alpha 即可让 PNG 编码带上不透明标记，
        // 不用再整帧拷一层背景（12M 像素上限下那一下要多吃 48MB）
        bitmap.setHasAlpha(false)
        compressPng(bitmap)
    }

    /**
     * 干员图导出 owned + notOwned 两段，与 JSON / Markdown / CSV 共用
     * [com.aliothmoon.maameow.data.repository.toExportSections] 的守卫与顺序（帕拉斯置顶）；
     * [includeNotOwned] 为 false 时只画已拥有的，此时图头也不再写「未拥有 N」。
     */
    suspend fun renderOperBoxPng(
        profileId: String,
        hideProfileLabel: Boolean = false,
        titleLabel: String = "",
        labels: OperBoxPngLabels,
        includeNotOwned: Boolean = true,
    ): ByteArray? = withContext(Dispatchers.Default) {
        val snap = operBoxRepository.snapshots.value[profileId] ?: OperBoxSnapshot()
        // 没识别过就不该导出一张空图让人去分享
        if (!snap.hasSynced) return@withContext null
        // 与 JSON / Markdown / CSV 共用同一份导出清单：同样「没识别过就是空表」，同样帕拉斯置顶
        val (owned, notOwned) = snap.toExportSections(includeNotOwned)
        // 收掉档名之后第一行就变成时间了，但它不该被当成大标题画
        val hasTitle = !hideProfileLabel && titleLabel.isNotBlank()

        val pad = 24
        val gap = 12
        val columns = 6
        val cellW = 132
        val cellH = cellW + 44
        val sectionHeaderH = 40
        val headerLineH = 40

        val headerLines = buildList {
            if (hasTitle) add(titleLabel)
            if (snap.syncTimeMillis > 0L) {
                add(formatToolboxSyncTime(snap.syncTimeMillis))
            }
            // 只导出已拥有时不写「未拥有 N」，免得和下面的分段标题互相打架
            if (includeNotOwned) add(labels.summaryFormat.format(snap.owned.size, snap.notOwned.size))
        }

        val sections = buildList {
            if (owned.isNotEmpty()) add(labels.ownedHeaderFormat.format(owned.size) to owned)
            if (notOwned.isNotEmpty()) add(labels.notOwnedHeaderFormat.format(notOwned.size) to notOwned)
        }

        // 行与行之间才有 gap，最后一行下面不留：否则底部会多出一条固定的空白
        fun rowsOf(count: Int): Int = ceil(count / columns.toDouble()).toInt()
        fun gridHeight(count: Int): Int =
            if (count == 0) 0 else sectionHeaderH + rowsOf(count) * cellH + (rowsOf(count) - 1) * gap

        val width = 900
        val headerH = pad + headerLines.size * headerLineH + 8
        val emptyStateH = if (sections.isEmpty()) headerLineH else 0
        val height = headerH + emptyStateH + pad + sections.sumOf { (_, list) -> gridHeight(list.size) } + pad

        val scale = exportScale(width, height)
        val bitmap = Bitmap.createBitmap(
            (width * scale).roundToInt(),
            (height * scale).roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(EXPORT_BG)
        canvas.scale(scale, scale)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ON_SURFACE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ON_SURFACE_VARIANT
            textSize = 26f
        }
        var y = pad + 34f
        headerLines.forEachIndexed { i, line ->
            canvas.drawText(line, pad.toFloat(), y, if (hasTitle && i == 0) titlePaint else subPaint)
            y += headerLineH
        }

        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ON_SURFACE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ON_SURFACE
            textSize = 21f
            textAlign = Paint.Align.CENTER
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_ON_SURFACE_VARIANT
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }
        // 卡片底：与界面一致，已拥有按稀有度上色（45% 透明），未拥有统一抹灰
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f, 0f, 1f, 0xCC000000.toInt())
        }
        val levelBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAA1F1F1F.toInt()
            style = Paint.Style.FILL
        }
        val levelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            // 与其它徽章同字号：界面里的 BadgeText 都是 8sp
            textSize = 14.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // 卡片上各种小徽章：深色底 + 白字，和界面里的 BadgeBackground 一致
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BADGE_BG
            style = Paint.Style.FILL
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        val masteryOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MASTERY_ON
            style = Paint.Style.FILL
        }
        val masteryOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MASTERY_OFF
            style = Paint.Style.FILL
        }
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EXPORT_CARD }
        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        val dimFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        val clipPath = Path()
        // 帕拉斯的头像位用应用图标（对齐界面 AppIconAvatar），只解一次
        val appIconBitmap: Bitmap? = ignoreFailure {
            appContext.packageManager.getApplicationIcon(appContext.applicationInfo).toBitmap(96, 96)
        }

        if (sections.isEmpty()) {
            // namePaint 是 CENTER 对齐：x 必须给画布中线，给 pad 会让文字跑出左边框
            canvas.drawText(labels.empty, width / 2f, headerH + headerLineH * 0.7f, namePaint)
        } else {
            var sectionTop = headerH
            sections.forEach { (header, list) ->
                canvas.drawText(header, pad.toFloat(), sectionTop + 26f, sectionPaint)
                val top = sectionTop + sectionHeaderH
                list.forEachIndexed { index, op ->
                    currentCoroutineContext().ensureActive()
                    val col = index % columns
                    val row = index / columns
                    val left = pad + col * (cellW + gap)
                    val cellTop = top + row * (cellH + gap)
                    // 帕拉斯用应用图标当头像（未拥有也照用，和界面 OperCardAvatar 一致，只是会去色）；
                    // 其余走 LRU 复用 + 只解码不入缓存，别把界面缓存的头像挤掉
                    val avatar = if (op.isPallas) {
                        appIconBitmap
                    } else {
                        ignoreFailure {
                            (operAvatarLoader.peek(op.id) ?: operAvatarLoader.loadTransient(op.id))
                                ?.asAndroidBitmap()
                        }
                    }

                    // 卡面与界面一致：帕拉斯是稀有成就渐变，已拥有按稀有度上色（45% 透明），未拥有统一抹灰
                    val cardRect = RectF(
                        left.toFloat(),
                        cellTop.toFloat(),
                        (left + cellW).toFloat(),
                        (cellTop + cellW).toFloat(),
                    )
                    cardPaint.shader = null
                    if (op.isPallas) {
                        cardPaint.shader = LinearGradient(
                            cardRect.left,
                            cardRect.top,
                            cardRect.right,
                            cardRect.bottom,
                            RARE_SUMMON_GRADIENT_LIGHT.toIntArray(),
                            null,
                            Shader.TileMode.CLAMP,
                        )
                    } else {
                        cardPaint.color = if (op.own) {
                            withAlpha(rarityColor(op.rarity), 0.45f)
                        } else {
                            NOT_OWNED_CARD
                        }
                    }
                    canvas.drawRoundRect(cardRect, 6f, 6f, cardPaint)
                    cardPaint.shader = null

                    // 头像占卡面 80%，对齐 OperBoxCard 的 fillMaxSize(0.8f)
                    val side = cellW * 0.8f
                    val avatarLeft = left + (cellW - side) / 2f
                    val avatarTop = cellTop + (cellW - side) / 2f
                    val dst = RectF(avatarLeft, avatarTop, avatarLeft + side, avatarTop + side)
                    if (avatar != null && !avatar.isRecycled) {
                        clipPath.reset()
                        clipPath.addRoundRect(dst, 4f, 4f, Path.Direction.CW)
                        canvas.save()
                        canvas.clipPath(clipPath)
                        avatarPaint.colorFilter = if (op.own) null else dimFilter
                        canvas.drawBitmap(avatar, null, dst, avatarPaint)
                        canvas.restore()
                    } else {
                        canvas.drawRoundRect(dst, 4f, 4f, placeholderPaint)
                    }

                    // 顶部居中的稀有度星：白字带阴影，压在浅色卡面上也看得清
                    // 帕拉斯与界面一致：已拥有时用六个随机表情代替星级
                    val pallasCheer = op.isPallas && op.own
                    starPaint.textSize = when {
                        pallasCheer -> 12f
                        op.rarity >= 5 -> 10f
                        else -> 11f
                    }
                    val starText = if (pallasCheer) pallasCheerText() else "★".repeat(op.rarity)
                    if (starText.isNotEmpty()) {
                        canvas.drawText(starText, left + cellW / 2f, cellTop + 16f, starPaint)
                    }

                    // 左上角职业图标：深色圆角底 + 1 单位内缩，对齐 OperBoxCard 的 12dp 徽章
                    val profession = ignoreFailure {
                        resourceDataManager.getCharacterById(op.id)?.profession
                    }
                    OperRoleIconLoader.templateId(profession)?.let { roleId ->
                        val role = ignoreFailure {
                            operRoleIconLoader.load(roleId)?.asAndroidBitmap()
                        }
                        if (role != null && !role.isRecycled) {
                            canvas.drawRoundRect(
                                RectF(left + 5f, cellTop + 5f, left + 30f, cellTop + 30f), 4f, 4f, badgePaint,
                            )
                            canvas.drawBitmap(
                                role, null, RectF(left + 7f, cellTop + 7f, left + 28f, cellTop + 28f), iconPaint,
                            )
                        }
                    }

                    if (op.own) {
                        // 左下角竖排：潜能 / 精英 / 等级，对齐界面里三个小徽章
                        var badgeBottom = cellTop + cellW - 5.5f
                        val lv = "Lv.${op.level}"
                        val lvWidth = levelTextPaint.measureText(lv) + 8f
                        canvas.drawRoundRect(
                            RectF(left + 5.5f, badgeBottom - 18f, left + 5.5f + lvWidth, badgeBottom),
                            3f, 3f, levelBadgePaint,
                        )
                        canvas.drawText(lv, left + 9.5f, badgeBottom - 5f, levelTextPaint)
                        badgeBottom -= 20f
                        badgeBottom = drawBadgeDrawable(
                            res = eliteRes(op.elite),
                            left = left + 5.5f,
                            bottom = badgeBottom,
                            size = 22f,
                            badgePaint = badgePaint,
                            iconPaint = iconPaint,
                            canvas = canvas,
                        )
                        drawBadgeDrawable(
                            res = potentialRes(op.potential),
                            left = left + 5.5f,
                            bottom = badgeBottom - 2f,
                            size = 22f,
                            badgePaint = badgePaint,
                            iconPaint = iconPaint,
                            canvas = canvas,
                        )

                        // 右下角：专精三圆 / RANK 贴卡底，模组依次叠在上面
                        // 对齐 OperBoxCard.TrainingBadges —— 没有一图流数据（mainSkillLevel 为空）就整块不画
                        val rank = op.mainSkillLevel
                        if (rank != null) {
                            var rightBottom = cellTop + cellW - 5.5f
                            val mastery = op.skills.map { it.level }
                            rightBottom = if (mastery.any { it >= 1 }) {
                                drawMasteryRow(
                                    canvas, mastery, left + cellW - 5.5f, rightBottom,
                                    badgePaint, masteryOnPaint, masteryOffPaint,
                                )
                            } else {
                                drawBadgeText(
                                    canvas, "RANK $rank", left + cellW - 5.5f, rightBottom,
                                    badgePaint, badgeTextPaint,
                                )
                            }
                            val mods = op.equips.filter { it.level > 0 }
                                .map { "${MOD_TYPE_LABELS[it.type] ?: it.type}${it.level}" }
                            for (text in mods.asReversed()) {
                                rightBottom = drawBadgeText(
                                    canvas, text, left + cellW - 5.5f, rightBottom - 2f,
                                    badgePaint, badgeTextPaint,
                                )
                            }
                        }
                    }

                    val name = op.name.let { if (it.length > 7) it.take(6) + "…" else it }
                    namePaint.fitText(name, cellW - 4f, base = 20f)
                    canvas.drawText(name, left + cellW / 2f, cellTop + cellW + 19f, namePaint)
                    if (op.own) {
                        val meta = labels.operMetaFormat.format(op.elite, op.level, op.potential)
                        metaPaint.fitText(meta, cellW - 4f, base = 15f)
                        canvas.drawText(meta, left + cellW / 2f, cellTop + cellW + 38f, metaPaint)
                    }
                }
                sectionTop = top + gridHeight(list.size)
            }
        }

        // 底图是不透明 ARGB，直接声明无 alpha 即可让 PNG 编码带上不透明标记，
        // 不用再整帧拷一层背景（12M 像素上限下那一下要多吃 48MB）
        bitmap.setHasAlpha(false)
        compressPng(bitmap)
    }

    private fun rarityColor(rarity: Int): Int = when (rarity) {
        6 -> Color.parseColor("#FF6B35")
        5 -> Color.parseColor("#FFD700")
        4 -> Color.parseColor("#9C7CFF")
        3 -> Color.parseColor("#4FC3F7")
        2 -> Color.parseColor("#A5D6A7")
        // 认不出的稀有度按界面兜底：用次要文字色而不是中性灰，深色相册里不会看成脏块
        else -> EXPORT_ON_SURFACE_VARIANT
    }

    /** 卡片底色：与界面一致，已拥有是稀有度色 45% 透明，未拥有是灰 */
    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha * 255f).roundToInt().coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun compressPng(bitmap: Bitmap): ByteArray? {
        return try {
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) null else out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 卡片上带深色底的图标徽章（潜能 / 精英），返回画完后的上边界。
     * 直接按 bounds 画 PNG，不额外建位图——这些图标是 30×30 的 nodpi 小图。
     */
    private fun drawBadgeDrawable(
        canvas: Canvas,
        res: Int?,
        left: Float,
        bottom: Float,
        size: Float,
        badgePaint: Paint,
        iconPaint: Paint,
    ): Float {
        if (res == null) return bottom
        val drawable = ignoreFailure { ContextCompat.getDrawable(appContext, res) }
            ?: return bottom
        val top = bottom - size
        canvas.drawRoundRect(RectF(left, top, left + size, bottom), 3f, 3f, badgePaint)
        val inset = 2f
        drawable.setBounds(
            (left + inset).toInt(),
            (top + inset).toInt(),
            (left + size - inset).toInt(),
            (bottom - inset).toInt(),
        )
        drawable.draw(canvas)
        return top
    }
}

/** 导出图里的文案；ViewModel 不碰 Context，由界面用 stringResource 解析后传进来。 */
data class DepotPngLabels(
    /** 例：「12 项」 */
    val itemsCountFormat: String,
    val empty: String,
)

data class OperBoxPngLabels(
    /** 例：「已拥有 120 · 未拥有 230」 */
    val summaryFormat: String,
    val empty: String,
    /** 例：「E2 Lv90 P6」 */
    val operMetaFormat: String,
    /** 例：「已拥有 (%1$d)」 */
    val ownedHeaderFormat: String,
    /** 例：「未拥有 (%1$d)」 */
    val notOwnedHeaderFormat: String,
)

// 导出图配色取自 theme/Theme.kt 的浅色方案：分享出去的图和 app 里看到的一致
private const val EXPORT_BG = 0xFFF5F2ED.toInt()
private const val EXPORT_CARD = 0xFFE8E4DE.toInt()
private const val EXPORT_ON_SURFACE = 0xFF1C1B18.toInt()
private const val EXPORT_ON_SURFACE_VARIANT = 0xFF8A8580.toInt()

/** 未拥有干员的卡面：对齐 OperBoxCard 的 0x99808080 */
private const val NOT_OWNED_CARD = 0x99808080.toInt()

/**
 * 渲染期的容错：单张图标/单次查询失败不该让整张图挂掉（缺一个图就画占位块），
 * 但协程取消必须继续往上抛 —— `runCatching` 会把 CancellationException 一起吞掉。
 */
private inline fun <T> ignoreFailure(block: () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

/** 卡片上徽章的深色底，对齐 OperBoxCard 的 BadgeBackground（#AA1F1F1F） */
private const val BADGE_BG = 0xAA1F1F1F.toInt()

/** 专精三圆：亮的取主题 primary，灭的取 outlineVariant */
private const val MASTERY_ON = 0xFF2B6BCA.toInt()
private const val MASTERY_OFF = 0xFFE8E4DE.toInt()

/** 模组分支显示名，对齐 OperBoxCard.MOD_TYPE_DISPLAY */
private val MOD_TYPE_LABELS = mapOf("A" to "α", "B" to "β", "X" to "χ", "Y" to "γ", "D" to "Δ")

/** 精英图标：0/1/2 各一张，其它情况不画 */
private fun eliteRes(elite: Int): Int? = when (elite) {
    0 -> R.drawable.ic_oper_elite_0
    1 -> R.drawable.ic_oper_elite_1
    2 -> R.drawable.ic_oper_elite_2
    else -> null
}

/** 潜能图标：超出 1~6 的范围按 1 潜显示，同 OperBoxCard.potentialIcon */
private fun potentialRes(potential: Int): Int = when (potential) {
    2 -> R.drawable.ic_oper_potential_2
    3 -> R.drawable.ic_oper_potential_3
    4 -> R.drawable.ic_oper_potential_4
    5 -> R.drawable.ic_oper_potential_5
    6 -> R.drawable.ic_oper_potential_6
    else -> R.drawable.ic_oper_potential_1
}

/** 右下角文字徽章（模组 / RANK），返回画完后的上边界，便于继续往上堆 */
private fun drawBadgeText(
    canvas: Canvas,
    text: String,
    right: Float,
    bottom: Float,
    badgePaint: Paint,
    textPaint: Paint,
): Float {
    val width = textPaint.measureText(text) + 8f
    val top = bottom - 18f
    canvas.drawRoundRect(RectF(right - width, top, right, bottom), 3f, 3f, badgePaint)
    canvas.drawText(text, right - width + 4f, bottom - 5f, textPaint)
    return top
}

/** 专精三圆：专 1 亮上圆、专 2 加亮右下、专 3 全亮，同 MasteryBadge；返回画完后的上边界 */
private fun drawMasteryRow(
    canvas: Canvas,
    levels: List<Int>,
    right: Float,
    bottom: Float,
    badgePaint: Paint,
    onPaint: Paint,
    offPaint: Paint,
): Float {
    if (levels.isEmpty()) return bottom
    val badgeSize = 16f
    val gap = 1f
    val width = levels.size * badgeSize + (levels.size - 1) * gap + 2f
    val top = bottom - badgeSize - 2f
    canvas.drawRoundRect(RectF(right - width, top, right, bottom), 3f, 3f, badgePaint)
    var x = right - width + 1f
    levels.forEach { level ->
        val radius = badgeSize / 5f
        val cyTop = top + 1f + radius
        val cyBottom = top + 1f + badgeSize - 1f - radius
        // 上圆居中（MasteryBadge 里是 size.width / 2f），不是贴左边
        canvas.drawCircle(x + badgeSize / 2f, cyTop, radius, if (level >= 1) onPaint else offPaint)
        canvas.drawCircle(x + badgeSize - radius, cyBottom, radius, if (level >= 2) onPaint else offPaint)
        canvas.drawCircle(x + radius, cyBottom, radius, if (level >= 3) onPaint else offPaint)
        x += badgeSize + gap
    }
    return top
}

/**
 * 单元格只有 [cellW] 宽，名字和「精英/等级/潜能」按可用宽度逐级缩字号：
 * 宁可小一点，也不要和隔壁格子的文字粘在一起（中文标签比英文长一倍）。
 */
private fun Paint.fitText(text: String, maxWidth: Float, base: Float, min: Float = 10f) {
    textSize = base
    var size = base
    while (size > min && measureText(text) > maxWidth) {
        size -= 0.5f
        textSize = size
    }
}

/**
 * 导出位图的整体缩放系数。
 *
 * 长图的高度完全由数据量决定（干员按行铺、仓库按 4 列铺），
 * 一个满仓库能算出上万像素高、上百 MB 的 ARGB_8888，低内存机型直接 OOM。
 * 超预算就等比缩小：内容一个不少，只是整体变小。
 */
private fun exportScale(width: Int, height: Int): Float {
    val pixels = width.toLong() * height.toLong()
    if (pixels <= MAX_EXPORT_PIXELS) return 1f
    return sqrt(MAX_EXPORT_PIXELS.toDouble() / pixels.toDouble()).toFloat()
}

/** 约 48 MB 的 ARGB_8888，给压缩输出和图标解码留够余量。 */
private const val MAX_EXPORT_PIXELS = 12_000_000

data class DepotInventoryItemUi(
    val id: String,
    val name: String,
    val count: Int,
    val sortId: Int,
)

/** 二级列表的一张配置档卡片。 */
data class DepotProfileRow(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val hasSynced: Boolean,
    val syncTimeMillis: Long,
    val planCount: Int,
    val unmetCount: Int,
)

/** 一条「库存保持」计划在所属配置档下的进度。 */
data class DepotMaintainPlanUi(
    val nodeId: String,
    /** 所属节点名：一个配置档有多个库存保持节点时，刷取顺序页靠它分段 */
    val nodeName: UiText,
    val node: DepotPlanContext,
    /** 在该节点 plans 里的下标，0 起；写回时按它定位 */
    val planIndex: Int,
    val plan: DepotMaintainPlan,
    val itemId: String,
    val itemName: String,
    val current: Int,
    val target: Int,
    val need: Int,
    val outcome: DepotPlanOutcome,
) {
    /** 未集齐：有目标且库存还不够。目标为 0 的计划不算缺货。 */
    val unmet: Boolean get() = target > 0 && current < target
}

/** 三级库存网格的一格：库存数量 + 该物品的保持计划（没有则为 null）。 */
data class DepotInventoryCellUi(
    /** 同一物品配了多条计划时会有多格，用节点 + 下标区分 */
    val key: String,
    val id: String,
    val name: String,
    val count: Int,
    val sortId: Int,
    val plan: DepotMaintainPlanUi?,
) {
    val unmet: Boolean get() = plan?.unmet == true
}

/** 新建计划时要带上的节点状态：节点没启用时面板要提示「保存了也不会跑」 */
data class DepotPlanContext(
    val nodeEnabled: Boolean = true,
)

/**
 * 库存数据页要编辑的那份库存保持配置，外加它属于哪个节点。
 *
 * [nodeId] 为空串 = 该档还没有库存保持节点，界面拿到的是默认配置；
 * 改动写回时由 [TaskChainState] 就地建节点，所以它不能当真实节点 ID 用。
 */
data class DepotMaintainConfigUi(
    val nodeId: String = "",
    val config: DepotMaintainConfig = DepotMaintainConfig(),
)

/** 网格的三段：有库存 → 未集齐 → 库存为 0（最后一段画得很淡） */
data class DepotCellGroups(
    val stocked: List<DepotInventoryCellUi>,
    val unmet: List<DepotInventoryCellUi>,
    val empty: List<DepotInventoryCellUi>,
)

internal fun List<DepotInventoryCellUi>.groupForDisplay(): DepotCellGroups {
    val (unmet, rest) = partition { it.unmet }
    val (stocked, empty) = rest.partition { it.count > 0 }
    return DepotCellGroups(stocked, unmet, empty)
}

/**
 * 网格内容 = 仓库识别认得出的全部物品 ∪ 快照里的物品 ∪ 计划里的物品。
 *
 * 快照那一支可能超出识别集合：Core 除了 MATERIAL 也会报合成玉、寻访凭证这类资源。
 * 报什么就显示什么，这里不再按物品种类过滤。
 *
 * 同一物品配了多条计划时一条一格：执行侧会挨个跑，格子少一条就会和二级页的
 * 「N 项未集齐」对不上，也会有一条计划在这一页改不到。
 */
internal fun buildInventoryCells(
    snapshot: DepotSnapshot,
    itemMap: Map<String, ItemInfo>,
    depotItemIds: List<String>,
    plans: List<DepotMaintainPlanUi>,
    /**
     * 明确不该出格子的条目（声望这类每场必掉的杂项）。
     *
     * 只过滤「没有计划」的格子：老计划里真存着这类 id 时仍要能看见、能删掉。
     */
    excludedItemIds: Set<String> = emptySet(),
): List<DepotInventoryCellUi> {
    val plansByItem = plans.groupBy { it.itemId }
    val ids = LinkedHashSet<String>(depotItemIds.size + snapshot.items.size).apply {
        addAll(depotItemIds)
        addAll(snapshot.items.keys.filterNot { it in excludedItemIds })
        addAll(plansByItem.keys)
    }
    return ids.asSequence()
        .flatMap { id ->
            val info = itemMap[id]
            val count = snapshot.items[id] ?: 0
            val itemPlans = plansByItem[id].orEmpty()
            if (itemPlans.isEmpty()) {
                sequenceOf(toCell(key = id, id = id, info = info, count = count, plan = null))
            } else {
                itemPlans.asSequence().map { plan ->
                    toCell(
                        key = depotCellKey(id, plan.nodeId, plan.planIndex),
                        id = id,
                        info = info,
                        count = count,
                        plan = plan,
                    )
                }
            }
        }
        .sortedWith(compareBy({ it.sortId }, { it.id }, { it.plan?.planIndex ?: -1 }))
        .toList()
}

private fun toCell(
    key: String,
    id: String,
    info: ItemInfo?,
    count: Int,
    plan: DepotMaintainPlanUi?,
) = DepotInventoryCellUi(
    key = key,
    id = id,
    name = info?.name ?: id,
    count = count,
    sortId = info?.sortId ?: Int.MAX_VALUE,
    plan = plan,
)

/**
 * 格子 key：同一物品配了多条计划时会有多格，用节点 + 下标区分。
 *
 * 刷取顺序页点一行要回到同一格的面板，得按同一个口径拼 key —— 两边都走这里，
 * 免得格式一改就只在一边生效。
 */
internal fun depotCellKey(itemId: String, nodeId: String, planIndex: Int): String =
    "$itemId#$nodeId#$planIndex"

internal fun DepotMaintainPlan.toUi(
    node: TaskChainNode,
    index: Int,
    snap: DepotSnapshot,
    itemMap: Map<String, ItemInfo>,
    isStageOpen: (String) -> Boolean,
): DepotMaintainPlanUi {
    val current = snap.items[dropId] ?: 0
    return DepotMaintainPlanUi(
        nodeId = node.id,
        nodeName = node.nameUiText(),
        node = node.toPlanContext(),
        planIndex = index,
        plan = this,
        itemId = dropId,
        itemName = itemMap[dropId]?.name ?: dropId,
        current = current,
        target = dropCount,
        need = (dropCount - current).coerceAtLeast(0),
        outcome = depotPlanOutcome(this, current, isStageOpen),
    )
}

internal fun TaskChainNode.toPlanContext(): DepotPlanContext =
    DepotPlanContext(nodeEnabled = enabled)

internal fun DepotMaintainPlan.unmetIn(snap: DepotSnapshot): Boolean {
    val current = snap.items[dropId] ?: 0
    return dropCount > 0 && current < dropCount
}

/** 一条链上所有库存保持计划，保持节点顺序；没选物品的计划不计入（网格里也没有它）。 */
internal fun List<TaskChainNode>.depotPlans(): List<DepotMaintainPlan> =
    flatMap { node -> (node.config as? DepotMaintainConfig)?.plans.orEmpty() }
        .filter { it.dropId.isNotBlank() }

/** 写回计划时的落点：启用节点优先，与 [TaskChainState.updateDepotMaintainPlans] 的选取一致。 */
internal fun List<TaskChainNode>.firstDepotNode(): TaskChainNode? =
    firstOrNull { it.config is DepotMaintainConfig && it.enabled }
        ?: firstOrNull { it.config is DepotMaintainConfig }

/**
 * 库存数据页要编辑的配置 + 它的落点节点，落点与 [firstDepotNode] 一致。
 *
 * 落点必须跟着界面走：一档可能挂了好几个库存保持节点，改错了不会报错，只会安静地不生效。
 */
internal fun List<TaskChainNode>.depotMaintainConfigUi(): DepotMaintainConfigUi =
    firstDepotNode()?.let { node ->
        DepotMaintainConfigUi(
            nodeId = node.id,
            config = node.config as? DepotMaintainConfig ?: DepotMaintainConfig(),
        )
    } ?: DepotMaintainConfigUi()

/**
 * 「当前 / 目标」，库存数据页与后台任务的库存保持概览共用这一份，两边永远同一口径。
 *
 * 没识别过仓库时当前值写「--」而不是 0：那时候缺多少算不准，写 0 会看着像真的一件都没有。
 */
internal fun depotProgressText(current: Int, target: Int, synced: Boolean): String =
    "${if (synced) current.toString() else "--"} / $target"

internal fun DepotSnapshot.toItemUiList(itemMap: Map<String, ItemInfo>): List<DepotInventoryItemUi> =
    items.asSequence()
        .map { (id, count) ->
            val info = itemMap[id]
            DepotInventoryItemUi(
                id = id,
                name = info?.name ?: id,
                count = count,
                sortId = info?.sortId ?: Int.MAX_VALUE,
            )
        }
        .sortedWith(compareBy<DepotInventoryItemUi> { it.sortId }.thenBy { it.id })
        .toList()

// ========== 刷取顺序 ==========

/**
 * 刷取顺序页的一行：一条计划 + 它在这一页的第几位。
 *
 * 序号只数这一页可见的计划；未选物品的计划不在这页，所以节点里有那种计划时，
 * 序号会比运行日志里的 `#N` 小 —— 那种计划执行侧会按「未选物品」跳过，本来也不刷。
 */
data class DepotFarmingOrderRow(
    val plan: DepotMaintainPlanUi,
    /** 段内序号，1 起 */
    val no: Int,
)

/** 刷取顺序页的一段：一个库存保持节点。节点在链上的顺序就是它执行的前后顺序。 */
data class DepotFarmingOrderSection(
    val nodeId: String,
    val nodeName: UiText,
    val nodeEnabled: Boolean,
    val rows: List<DepotFarmingOrderRow>,
)

/**
 * 按节点分段，段内保持计划原本的顺序。
 *
 * 执行顺序就是「链上节点顺序 × 节点内计划顺序」，所以这里不能重排，照搬即可。
 * 未启用的节点照样列出（否则那些计划在这一页既看不到也排不了），由界面标注「不会执行」。
 */
internal fun List<DepotMaintainPlanUi>.farmingOrderSections(): List<DepotFarmingOrderSection> =
    groupBy { it.nodeId }.map { (nodeId, nodePlans) ->
        DepotFarmingOrderSection(
            nodeId = nodeId,
            nodeName = nodePlans.first().nodeName,
            nodeEnabled = nodePlans.first().node.nodeEnabled,
            rows = nodePlans.mapIndexed { index, plan -> DepotFarmingOrderRow(plan, index + 1) },
        )
    }

/**
 * 把 [plans] 里 [order] 这些位置上的计划按新顺序重排，其余位置原地不动。
 *
 * [order] 是这一段可见计划在 `plans` 里的下标，**按新的显示顺序**给出，
 * 所以它必须是这些下标的排列（界面给的就是那一页每行的 `planIndex`）。
 *
 * 落点取 `order.sorted()`：可见计划原本就是按下标升序排的（见 [farmingOrderSections]），
 * 排完序就是它们各自占着的那些位置 —— 用 `order` 自己当落点等于拿排列去置换排列，
 * 结果是原地打转（把 c 拖到最前，写回去还是 a b c）。
 *
 * 未选物品的占位计划不出现在刷取顺序页，所以也不能按可见下标裁剪重排 ——
 * 那会把占位挤走、让它后面的下标全部错位，用户没碰过的计划也跟着变。
 *
 * 下标重复、越界，或与当前长度对不上（期间被别处改过）时整笔放弃，
 * 宁可不生效也不能把计划写到别的下标上。
 */
internal fun applyPlanOrder(
    plans: List<DepotMaintainPlan>,
    order: List<Int>,
): List<DepotMaintainPlan> {
    if (order.size != order.distinct().size) return plans
    if (order.any { it !in plans.indices }) return plans
    val moved = order.map { plans[it] }
    return plans.toMutableList().also { out ->
        order.sorted().forEachIndexed { index, slot -> out[slot] = moved[index] }
    }
}
