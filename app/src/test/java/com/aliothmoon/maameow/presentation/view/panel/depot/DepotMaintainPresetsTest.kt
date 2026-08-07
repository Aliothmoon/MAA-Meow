package com.aliothmoon.maameow.presentation.view.panel.depot

import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DepotMaintainPresetsTest {

    @Test
    fun chip1_matchesUpstreamOrderAndTargets() {
        assertPreset(
            preset = DepotMaintainPreset.CHIP_1,
            expected = listOf(
                Triple("PR-A-1", "3261", 20),
                Triple("PR-A-1", "3231", 20),
                Triple("PR-B-1", "3251", 20),
                Triple("PR-B-1", "3241", 20),
                Triple("PR-C-1", "3211", 20),
                Triple("PR-C-1", "3271", 20),
                Triple("PR-D-1", "3221", 20),
                Triple("PR-D-1", "3281", 20),
            ),
        )
    }

    @Test
    fun chip2_matchesUpstreamOrderAndTargets() {
        assertPreset(
            preset = DepotMaintainPreset.CHIP_2,
            expected = listOf(
                Triple("PR-A-2", "3262", 20),
                Triple("PR-A-2", "3232", 20),
                Triple("PR-B-2", "3252", 20),
                Triple("PR-B-2", "3242", 20),
                Triple("PR-C-2", "3212", 20),
                Triple("PR-C-2", "3272", 20),
                Triple("PR-D-2", "3222", 20),
                Triple("PR-D-2", "3282", 20),
            ),
        )
    }

    @Test
    fun resourcePresets_matchUpstreamTargets() {
        assertPreset(
            DepotMaintainPreset.LMD,
            listOf(Triple("CE-6", "4001", 2_000_000)),
        )
        assertPreset(
            DepotMaintainPreset.PURCHASE_CERTIFICATE,
            listOf(Triple("AP-5", "4006", 5_000)),
        )
        assertPreset(
            DepotMaintainPreset.SKILL_SUMMARY_3,
            listOf(Triple("CA-5", "3303", 200)),
        )
    }

    @Test
    fun presetPlans_leaveConsumablesDisabled() {
        DepotMaintainPreset.entries.flatMap { it.plans }.forEach { plan ->
            assertFalse(plan.useMedicine)
            assertEquals(0, plan.medicineCount)
            assertFalse(plan.useStone)
            assertEquals(0, plan.stoneCount)
        }
    }

    @Test
    fun append_keepsExistingPlansAndAddsPresetEveryTime() {
        val existing = DepotMaintainPlan(stage = "1-7", dropId = "30011", dropCount = 100)

        val once = appendDepotMaintainPreset(listOf(existing), DepotMaintainPreset.LMD)
        val twice = appendDepotMaintainPreset(once, DepotMaintainPreset.LMD)

        assertEquals(existing, once.first())
        assertEquals(
            listOf(
                Triple("1-7", "30011", 100),
                Triple("CE-6", "4001", 2_000_000),
                Triple("CE-6", "4001", 2_000_000),
            ),
            twice.map { Triple(it.stage, it.dropId, it.dropCount) },
        )
    }

    private fun assertPreset(
        preset: DepotMaintainPreset,
        expected: List<Triple<String, String, Int>>,
    ) {
        assertEquals(
            expected,
            preset.plans.map { Triple(it.stage, it.dropId, it.dropCount) },
        )
    }
}
