package com.aliothmoon.maameow.data.preferences

import com.aliothmoon.maameow.data.model.ReclamationConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.utils.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** 上游 v6.21.0-beta.1 生息演算迁到工具箱，旧任务链节点移出并交出设置 */
class ReclamationMigrationTest {

    private fun node(config: TaskParamProvider, order: Int) =
        TaskChainNode(name = "n$order", order = order, config = config)

    @Test
    fun noReclamation_keepsProfilesUntouched() {
        val profiles = listOf(TaskProfile(id = "a", name = "a", chain = listOf(node(WakeUpConfig(), 0))))

        val (migrated, legacy) = profiles.extractReclamation("a")

        assertSame(profiles, migrated)
        assertNull(legacy)
    }

    @Test
    fun activeProfileSuppliesSettings_andAllNodesAreRemoved() {
        val inactive = ReclamationConfig(theme = "RelaunchAnchor", mode = ReclamationConfig.MODE_RA4)
        val active = ReclamationConfig(toolToCraft = "荧光棒")
        val profiles = listOf(
            TaskProfile(id = "a", name = "a", chain = listOf(node(inactive, 0), node(WakeUpConfig(), 1))),
            TaskProfile(id = "b", name = "b", chain = listOf(node(WakeUpConfig(), 0), node(active, 1))),
        )

        val (migrated, legacy) = profiles.extractReclamation("b")

        assertEquals(active, legacy)
        migrated.forEach { profile ->
            assertEquals(listOf(0), profile.chain.map { it.order })
            assertEquals(listOf(WakeUpConfig::class), profile.chain.map { it.config::class })
        }
    }

    @Test
    fun legacyChainJson_stillDecodes() {
        val json = JsonUtils.common
        val raw = json.encodeToString(
            listOf(TaskProfile(id = "a", name = "a", chain = listOf(node(ReclamationConfig(), 0))))
        )

        val (migrated, legacy) = json.decodeFromString<List<TaskProfile>>(raw).extractReclamation(null)

        assertEquals(ReclamationConfig(), legacy)
        assertEquals(emptyList<TaskChainNode>(), migrated.single().chain)
    }
}
