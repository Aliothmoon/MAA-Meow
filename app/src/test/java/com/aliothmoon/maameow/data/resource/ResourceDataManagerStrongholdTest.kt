package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.config.MaaPathConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 卫戍协议刷角标说明里的推荐干员，对齐 WPF GetRegulationBadgeTip */
class ResourceDataManagerStrongholdTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun manager(purchase: String?): ResourceDataManager {
        val resourceDir = tempFolder.newFolder("resource")
        File(resourceDir, "battle_data.json").writeText(
            """
            {
              "chars": {
                "char_001_alpha": { "name": "甲", "name_en": "Alpha", "profession": "CASTER", "rarity": 6 },
                "char_002_beta": { "name": "乙", "name_en": "Beta", "profession": "SNIPER", "rarity": 6 }
              }
            }
            """.trimIndent()
        )
        purchase?.let {
            File(resourceDir, "stronghold_protocol/regulation/purchase.json").apply {
                parentFile!!.mkdirs()
                writeText(it)
            }
        }
        val pathConfig = mockk<MaaPathConfig> {
            every { this@mockk.resourceDir } returns resourceDir.absolutePath
        }
        return ResourceDataManager(pathConfig)
    }

    @Test
    fun priorityKeepsOrder_localizes_andSkipsUnknownIds() {
        val manager = manager(
            """
            {"priority":[
              {"id":"char_002_beta"},
              {"id":"char_999_missing","squad":true},
              {"id":"char_001_alpha","squad":true}
            ]}
            """.trimIndent()
        )

        runBlocking { manager.load("Official", "en-us") }

        assertEquals(listOf("Alpha"), manager.strongholdPriority.value.squad)
        assertEquals(listOf("Beta", "Alpha"), manager.strongholdPriority.value.shop)
    }

    @Test
    fun missingFile_isEmpty() {
        val manager = manager(purchase = null)

        runBlocking { manager.load("Official", "zh-cn") }

        assertTrue(manager.strongholdPriority.value.shop.isEmpty())
    }
}
