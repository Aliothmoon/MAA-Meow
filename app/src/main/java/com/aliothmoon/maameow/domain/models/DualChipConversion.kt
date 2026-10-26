package com.aliothmoon.maameow.domain.models

/**
 * 库存保持「芯片组计入双芯片折算」：每个双芯片消耗 2 个同职业芯片组
 * 上游反查全部合成配方，会把普通材料的上级合成品也算进来，这里只认八种芯片组
 */
object DualChipConversion {
    private const val PACKS_PER_DUAL_CHIP = 2

    /** 芯片组 → 同职业双芯片：3212 先锋芯片组 → 3213 先锋双芯片，依次到特种 3282 → 3283 */
    private val DUAL_CHIP_BY_PACK = (1..8).associate { "32${it}2" to "32${it}3" }

    /** 开启折算时计入双芯片后的数量，其余物品原样 */
    fun count(dropId: String, enabled: Boolean, countOf: (String) -> Int): Int =
        countOf(dropId) + if (enabled) extra(dropId, countOf) else 0

    fun extra(dropId: String, countOf: (String) -> Int): Int =
        DUAL_CHIP_BY_PACK[dropId]?.let { countOf(it) * PACKS_PER_DUAL_CHIP } ?: 0
}
