package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File

/**
 * see ItemListHelper
 */
class ItemHelper(
    private val pathConfig: MaaPathConfig
) {
    private val json = JsonUtils.common

    private val _items = MutableStateFlow<Map<String, ItemInfo>>(emptyMap())
    private val _dropItems = MutableStateFlow<List<ItemInfo>>(emptyList())
    private val _depotItems = MutableStateFlow<List<ItemInfo>>(emptyList())
    private val _depotTargetItems = MutableStateFlow<List<ItemInfo>>(emptyList())

    /**  (itemId -> ItemInfo) */
    val items: StateFlow<Map<String, ItemInfo>> = _items.asStateFlow()

    /**
     * 关卡掉落可选的物品，按 ID 排。
     *
     * 这是**战斗**那边的口径（对齐 WPF `FightSettingsUserControlModel._excludedValues`），
     * 里面有声望（博士经验）、龙门币这类永远不进仓库的东西，
     * 别拿它当仓库物品列表——见 [depotItems]。
     */
    val dropItems: StateFlow<List<ItemInfo>> = _dropItems.asStateFlow()

    /**
     * 仓库识别认得出的物品，= 索引里 `classifyType == "MATERIAL"` 的全部条目，按 `sortId` 排。
     *
     * 严格对齐 MaaCore：`ItemConfig::parse` 只把 MATERIAL 收进有序表，
     * `DepotImageAnalyzer::prepare_cached_templates` 正是拿这份表建模板缓存的，
     * 所以「仓库里能识别出来的」就等于这一份——声望（NONE）、龙门币 / 合成玉（NORMAL）
     * 一个都不在里面。库存页、库存保持的物品列表都用它。
     */
    val depotItems: StateFlow<List<ItemInfo>> = _depotItems.asStateFlow()

    /**
     * 可以作为「库存保持」刷取目标的物品 = [depotItems] + [DEPOT_EXTRA_ITEM_IDS]。
     *
     * 仓库识别只覆盖 MATERIAL，龙门币 / 采购凭证这类货币永远识别不到，
     * 但它们的掉落会随战斗结算进仓库快照（见 `SubTaskHandler.handleStageDrops`），
     * 所以照样能当目标刷：当前数量只按累计掉落算，没有识别基线。
     *
     * 家具零件（`3401`）**不在**这里：它不是关卡掉落，没有可刷的关卡，
     * 放进来只会得到一格永远刷不满的数字。
     *
     * 声望（`5001`，classifyType=NONE）这类「每场必掉、但不是可刷资源」的条目
     * 不在这里，库存页与物品选择器都不该出现。
     */
    val depotTargetItems: StateFlow<List<ItemInfo>> = _depotTargetItems.asStateFlow()

    /** 该物品能否作为库存保持的刷取目标 */
    fun isDepotTargetItem(itemId: String): Boolean =
        _depotTargetItems.value.any { it.id == itemId } ||
            // 索引未载入时退化成「只要不是明确的杂项就放行」，避免掉落事件被整批吞掉
            (_depotTargetItems.value.isEmpty() && itemId !in NON_TARGET_ITEM_IDS)

    /** 明确不该出现在库存页 / 不写进仓库的条目（声望这类每场必掉的杂项） */
    val nonTargetItemIds: Set<String> get() = NON_TARGET_ITEM_IDS

    /**
     * 仓库识别覆盖不到、只能靠战斗结算累积库存的物品（龙门币 / 采购凭证）。
     *
     * 界面上要据此提示「识别不会刷新它们的数字」，否则用户会以为识别漏了。
     */
    val currencyItemIds: Set<String> get() = DEPOT_EXTRA_ITEM_IDS.toSet()

    /**
     * 关卡不可掉落的材料排除列表
     * see FightSettingsUserControlModel._excludedValues
     */
    companion object {
        private const val INDEX_JSON = "item_index.json"

        /**
         * 仓库识别之外的刷取目标：龙门币、采购凭证。
         *
         * 这两样在仓库界面里不在可识别的材料网格内（classifyType=NORMAL），
         * 但关卡掉落会带它们，所以能按「累计掉落」当目标刷。
         * 家具零件（3401）不在其中：它只能靠基建/活动产出，没有可刷关卡。
         */
        private val DEPOT_EXTRA_ITEM_IDS = listOf("4001", "4006")

        /**
         * 明确不作为刷取目标的条目：声望（博士经验）每场必掉且无法消耗，
         * 进库存页只会变成一堆无人关心的数字。
         */
        private val NON_TARGET_ITEM_IDS = setOf("5001")

        /** 没有独立资源档的客户端，只有根目录那一份索引 */
        const val DEFAULT_CLIENT = "Official"

        /** 索引里材料条目的 `classifyType`，仓库识别只认这一类 */
        private const val MATERIAL_CLASSIFY = "MATERIAL"

        /**
         * 有 global 资源档的客户端。
         *
         * 从 [ResourceDataManager] 的资源目录表反推，别再抄一份名单：
         * 那边加了新服而这里忘了加，物品名会静默退回中文。
         */
        private val GLOBAL_CLIENTS: Set<String> = ResourceDataManager.CLIENT_LANGUAGE_MAPPER
            .filterValues { language ->
                ResourceDataManager.CLIENT_DIRECTORY_MAPPER[language].orEmpty().isNotEmpty()
            }
            .keys

        private val excludedValues = setOf(
            "3213", "3223", "3233", "3243", // 双芯片
            "3253", "3263", "3273", "3283", // 双芯片
            "7001", "7002", "7003", "7004", // 许可
            "4004", "4005",                 // 凭证
            "3105", "3131", "3132", "3133", // 龙骨/加固建材
            "6001",                         // 演习券
            "3141", "4002",                 // 家具零件/源石
            "32001",                        // 芯片助剂
            "30115",                        // 聚合剂
            "30125",                        // 双极纳米片
            "30135",                        // D32钢
            "30145",                        // 晶体电子单元
            "30155",                        // 烧结核凝晶
            "30165",                        // 重相位对映体
        )
    }

    /**
     * 读取物品索引。
     *
     * 两份索引取并集，同 ID 以客户端那份为准：根目录那份条目更全（新素材先上国服），
     * 客户端那份语言对。所以选了国际服之后，国际服有的 ID 用国际服的名字，
     * 国际服还没有的 ID 退回根目录那份的中文名——总比显示成一串数字强。
     *
     * 解析不出来时**保留上一份可用数据**——直接清空会让界面整局把所有物品
     * 显示成 ID，比留着旧名字更糟。
     *
     * @return 是否拿到了非空索引
     */
    fun load(clientType: String = DEFAULT_CLIENT): Boolean {
        val parsed = doParseItemsJson(clientType)
        if (parsed.isEmpty()) {
            if (_items.value.isEmpty()) {
                Timber.w("物品索引为空（客户端 %s），物品名将回退为 ID", clientType)
            }
            return false
        }
        if (parsed != _items.value) {
            _items.value = parsed
            _dropItems.value = parsed.values
                .filter { it.id.all { c -> c.isDigit() } }  // WPF: int.TryParse
                .filter { it.id !in excludedValues }
                .sortedBy { it.id }  // WPF: string.Compare Ordinal
            // Core 那边 sortId 相同就是 `std::ranges::sort` 的不稳定序，这里补个 id 兜底
            val materials = parsed.values
                .filter { it.classifyType == MATERIAL_CLASSIFY }
                .sortedWith(compareBy({ it.sortId }, { it.id }))
            _depotItems.value = materials
            // 排序跟仓库材料同一条：先 sortId 再 id，货币会落在它们自己的位置（家具零件在最后）
            _depotTargetItems.value = (materials + DEPOT_EXTRA_ITEM_IDS.mapNotNull { parsed[it] })
                .filterNot { it.id in NON_TARGET_ITEM_IDS }
                .sortedWith(compareBy({ it.sortId }, { it.id }))
        }
        return true
    }

    fun getItemInfo(itemId: String): ItemInfo? {
        return _items.value[itemId]
    }

    /** 先根目录、后客户端资源档；后读的覆盖先读的 */
    private fun indexFiles(clientType: String): List<File> = buildList {
        add(File(pathConfig.resourceDir, INDEX_JSON))
        if (clientType in GLOBAL_CLIENTS) {
            add(File(pathConfig.globalResourceDir(clientType), INDEX_JSON))
        }
    }

    /** 缺文件的那层直接跳过，不影响另一层 */
    private fun doParseItemsJson(clientType: String): Map<String, ItemInfo> =
        indexFiles(clientType).fold(emptyMap<String, ItemInfo>()) { acc, file ->
            if (file.isFile) acc + parseItemsJson(file) else acc
        }

    private fun parseItemsJson(file: File): Map<String, ItemInfo> {
        // TODO 暂时只支持中文
        return try {
            val entries: Map<String, ItemJsonEntry> = json.decodeFromString(file.readText())
            entries.mapValues { (id, entry) ->
                ItemInfo(
                    id = id,
                    name = entry.name,
                    icon = entry.icon,
                    sortId = entry.sortId,
                    classifyType = entry.classifyType ?: ""
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "解析物品索引失败: %s", file.absolutePath)
            emptyMap()
        }
    }
}
