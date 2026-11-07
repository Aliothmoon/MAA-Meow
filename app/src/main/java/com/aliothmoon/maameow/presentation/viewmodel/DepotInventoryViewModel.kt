package com.aliothmoon.maameow.presentation.viewmodel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.aliothmoon.maameow.data.repository.toLoliconJson
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import com.aliothmoon.maameow.data.resource.ItemInfo
import com.aliothmoon.maameow.utils.i18n.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date
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
    private val activityManager: ActivityManager,
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
     * 库存格子 = 仓库识别认得出的全部物品 ∪ 仓库现有物品 ∪ 保持计划里的物品。
     *
     * 识别集合取 [ItemHelper.depotItems]（索引里 `classifyType == "MATERIAL"`），
     * 与 Core 建模板缓存用的那份同源；**不能取 [ItemHelper.dropItems]**——那是关卡掉落列表，
     * 会把声望（博士经验）、龙门币这类根本进不了仓库的东西画成一格。
     *
     * 库里一条记录都没有的材料也要出格子（图标压淡），否则「这个材料我到底有没有」
     * 只能靠数格子猜；计划里的物品即便识别不到（= 0）也要出现，否则「缺多少」
     * 在最需要看的时候反而没有格子。
     */
    val cells: StateFlow<List<DepotInventoryCellUi>> = combine(
        depotSnapshot,
        itemHelper.items,
        itemHelper.depotItems,
        maintainPlans,
    ) { snap, itemMap, depotItems, plans ->
        buildInventoryCells(snap, itemMap, depotItems.map { it.id }, plans)
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

    fun exportOperBoxList(profileId: String): List<OperBoxOperator> =
        (operBoxRepository.snapshots.value[profileId] ?: OperBoxSnapshot()).toExportList()

    fun exportOperBoxJson(profileId: String): String =
        OperBoxExportFormatter.toJson(exportOperBoxList(profileId))

    fun exportOperBoxMarkdown(profileId: String, labels: OperBoxExportLabels): String =
        OperBoxExportFormatter.toMarkdown(exportOperBoxList(profileId), labels)

    fun exportOperBoxCsv(profileId: String, labels: OperBoxExportLabels): String =
        OperBoxExportFormatter.toCsv(exportOperBoxList(profileId), labels)

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
        val itemList = snap.toItemUiList(itemHelper.items.value)
        val header = buildList {
            if (!hideProfileLabel && titleLabel.isNotBlank()) add(titleLabel)
            if (snap.syncTimeMillis > 0L) {
                add(DateFormat.getDateTimeInstance().format(Date(snap.syncTimeMillis)))
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
        canvas.drawColor(Color.WHITE) // 导出底必须不透明白，黑底图标抠透后透出这里
        // 之后一律按设计尺寸绘制，超预算时整体缩小（文字、图标一起缩）
        canvas.scale(scale, scale)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 26f
        }
        var y = pad + 34f
        header.forEachIndexed { i, line ->
            canvas.drawText(line, pad.toFloat(), y, if (i == 0) titlePaint else subPaint)
            y += headerLineH
        }

        val cellBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F7F5F2") }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        // 图标底板与占位色在循环外建一次，别按格子数 new
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
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
                canvas.drawRoundRect(rect, 12f, 12f, cellBg)

                val icon = runCatching { itemIconLoader.loadRawAndroidBitmap(item.id) }.getOrNull()
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
                    // 先铺不透明白底，再画黑转透明后的图标，透出白底不发黑
                    canvas.drawRoundRect(dst, 8f, 8f, plate)
                    canvas.drawBitmap(icon, null, dst, iconPaint)
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

        compressPng(bitmap)
    }

    /**
     * 干员图导出 owned + notOwned 两段，与 JSON / Markdown / CSV 的口径一致；
     * 只画 owned 会让人以为未拥有的干员不在数据里。
     */
    suspend fun renderOperBoxPng(
        profileId: String,
        hideProfileLabel: Boolean = false,
        titleLabel: String = "",
        labels: OperBoxPngLabels,
    ): ByteArray? = withContext(Dispatchers.Default) {
        val snap = operBoxRepository.snapshots.value[profileId] ?: OperBoxSnapshot()
        val opers = snap.toExportList()

        val pad = 24
        val rowH = 64
        val gap = 8
        val headerLineH = 40
        val headerLines = buildList {
            if (!hideProfileLabel && titleLabel.isNotBlank()) add(titleLabel)
            if (snap.syncTimeMillis > 0L) {
                add(DateFormat.getDateTimeInstance().format(Date(snap.syncTimeMillis)))
            }
            add(labels.summaryFormat.format(snap.owned.size, snap.notOwned.size))
        }
        val headerH = pad + headerLines.size * headerLineH + 8
        val width = 900
        val height = headerH + pad + max(1, opers.size) * (rowH + gap) + pad

        val scale = exportScale(width, height)
        val bitmap = Bitmap.createBitmap(
            (width * scale).roundToInt(),
            (height * scale).roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 26f
        }
        var y = pad + 34f
        headerLines.forEachIndexed { i, line ->
            canvas.drawText(line, pad.toFloat(), y, if (i == 0) titlePaint else subPaint)
            y += headerLineH
        }

        val rowBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F3F1ED") }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 24f
            textAlign = Paint.Align.RIGHT
        }
        val rarityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        if (opers.isEmpty()) {
            canvas.drawText(labels.empty, pad.toFloat(), headerH + 48f, namePaint)
        } else {
            opers.forEachIndexed { index, op ->
                val top = headerH + index * (rowH + gap)
                val rect = RectF(
                    pad.toFloat(),
                    top.toFloat(),
                    (width - pad).toFloat(),
                    (top + rowH).toFloat(),
                )
                canvas.drawRoundRect(rect, 10f, 10f, rowBg)

                rarityPaint.color = rarityColor(op.rarity)
                canvas.drawText("${op.rarity}★", (pad + 16).toFloat(), top + 40f, rarityPaint)
                canvas.drawText(op.name, pad + 80f, top + 40f, namePaint)
                if (op.own) {
                    val meta = labels.operMetaFormat.format(op.elite, op.level, op.potential)
                    canvas.drawText(meta, (width - pad - 16).toFloat(), top + 40f, metaPaint)
                }
            }
        }

        compressPng(bitmap)
    }

    private fun rarityColor(rarity: Int): Int = when (rarity) {
        6 -> Color.parseColor("#FF6B35")
        5 -> Color.parseColor("#FFD700")
        4 -> Color.parseColor("#9C7CFF")
        3 -> Color.parseColor("#4FC3F7")
        2 -> Color.parseColor("#A5D6A7")
        else -> Color.GRAY
    }

    private fun compressPng(bitmap: Bitmap): ByteArray? {
        return try {
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) null else out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
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
)

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
): List<DepotInventoryCellUi> {
    val plansByItem = plans.groupBy { it.itemId }
    val ids = LinkedHashSet<String>(depotItemIds.size + snapshot.items.size).apply {
        addAll(depotItemIds)
        addAll(snapshot.items.keys)
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
