package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.config.MaaPathConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/**
 * see ItemListHelper
 */
class ItemHelper(
    private val pathConfig: MaaPathConfig
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _items = MutableStateFlow<Map<String, ItemInfo>>(emptyMap())
    private val _dropItems = MutableStateFlow<List<ItemInfo>>(emptyList())

    /**  (itemId -> ItemInfo) */
    val items: StateFlow<Map<String, ItemInfo>> = _items.asStateFlow()
    val dropItems: StateFlow<List<ItemInfo>> = _dropItems.asStateFlow()

    /**
     * 关卡不可掉落的材料排除列表
     * see FightSettingsUserControlModel._excludedValues
     */
    companion object {
        private const val INDEX_JSON = "item_index.json"
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

    fun load() {
        _items.value = doParseItemsJson()
        _dropItems.value = _items.value.values
            .filter { it.id.all { c -> c.isDigit() } }  // WPF: int.TryParse
            .filter { it.id !in excludedValues }
            .sortedBy { it.id }  // WPF: string.Compare Ordinal
    }

    fun getItemInfo(itemId: String): ItemInfo? {
        return _items.value[itemId]
    }

    private fun doParseItemsJson(): Map<String, ItemInfo> {
        // TODO 暂时只支持中文
        return try {
            val file = File(pathConfig.resourceDir, INDEX_JSON)
            if (!file.exists()) {
                Timber.w("item_index.json not found: ${file.absolutePath}")
                return emptyMap()
            }
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
            Timber.e(e, "Failed to load item_index.json")
            emptyMap()
        }
    }
}
