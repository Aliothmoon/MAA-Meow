package com.aliothmoon.maameow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

/** 上游 v6.21.0-beta.1 库存保持「芯片组计入双芯片折算」，只认八种芯片组 */
class DualChipConversionTest {

    private val depot = mapOf(
        "3212" to 5, "3213" to 3,
        "3282" to 1, "3283" to 4,
        "30013" to 10, "30014" to 7,
        "32001" to 2,
    )
    private val countOf: (String) -> Int = { depot[it] ?: 0 }

    @Test
    fun chipPack_countsDualChipsTwice() {
        assertEquals(5 + 3 * 2, DualChipConversion.count("3212", enabled = true, countOf))
        assertEquals(1 + 4 * 2, DualChipConversion.count("3282", enabled = true, countOf))
    }

    @Test
    fun disabled_keepsRawCount() {
        assertEquals(5, DualChipConversion.count("3212", enabled = false, countOf))
    }

    @Test
    fun otherCraftMaterials_areNotConverted() {
        // 上游按配方反查会把提纯源岩算进固源岩组、把双芯片算进芯片助剂
        assertEquals(10, DualChipConversion.count("30013", enabled = true, countOf))
        assertEquals(2, DualChipConversion.count("32001", enabled = true, countOf))
        assertEquals(0, DualChipConversion.extra("3213", countOf))
    }
}
