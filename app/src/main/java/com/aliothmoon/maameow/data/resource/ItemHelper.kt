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

    /** (itemId -> ItemInfo) */
    val items: StateFlow<Map<String, ItemInfo>> = _items.asStateFlow()

    /** 关卡可掉落物品，对齐 WPF FightSettingsUserControlModel._excludedValues */
    val dropItems: StateFlow<List<ItemInfo>> = _dropItems.asStateFlow()

    /**
     * 关卡不可掉落的材料排除列表
     * see FightSettingsUserControlModel._excludedValues
     */
    companion object {
        private const val INDEX_JSON = "item_index.json"

        /** 没有独立资源档的客户端，只有根目录那一份索引 */
        const val DEFAULT_CLIENT = "Official"

        /** 有 global 资源档的客户端，从 [ResourceDataManager] 的目录表反推，不另抄名单 */
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
     * 读取物品索引：根目录与客户端两份取并集，同 ID 以客户端为准，缺的退回根目录中文名
     *
     * 解析不出来时保留上一份数据，不清空成 ID
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
