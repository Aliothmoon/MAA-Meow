package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.config.MaaPathConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 上游 #18190 去掉了升变阿米娅的职业后缀，旧配置里存的带后缀名要能落回本体
 */
class ResourceDataManagerAmiyaTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun managerWith(battleData: String): ResourceDataManager {
        val resourceDir = tempFolder.newFolder("resource")
        File(resourceDir, "battle_data.json").writeText(battleData)
        val pathConfig = mockk<MaaPathConfig> {
            every { this@mockk.resourceDir } returns resourceDir.absolutePath
        }
        return ResourceDataManager(pathConfig).also { runBlocking { it.load() } }
    }

    private val battleData = """
        {
          "chars": {
            "char_002_amiya": { "name": "阿米娅", "name_en": "Amiya", "profession": "CASTER", "rarity": 5 },
            "char_1001_amiya2": { "name": "阿米娅", "name_en": "Amiya", "profession": "WARRIOR", "rarity": 5 },
            "char_197_poca": { "name": "早露", "name_en": "Rosa", "profession": "SNIPER", "rarity": 6 }
          }
        }
    """.trimIndent()

    @Test
    fun duplicateAmiyaNameResolvesToTheCasterOriginal() {
        val manager = managerWith(battleData)

        assertEquals("char_002_amiya", manager.getCharacterByNameOrAlias("阿米娅")?.id)
    }

    @Test
    fun legacyMorphSuffixFallsBackToTheBaseOperator() {
        val manager = managerWith(battleData)

        assertEquals("阿米娅", manager.getCharacterByNameOrAlias("阿米娅-WARRIOR")?.name)
        assertEquals("阿米娅", manager.getCharacterByNameOrAlias("阿米娅-MEDIC")?.name)
        assertEquals("Amiya", manager.getCharacterByNameOrAlias("Amiya-MEDIC")?.nameEn)
    }

    @Test
    fun anUnknownNameStaysUnknownEvenWithAMorphSuffix() {
        val manager = managerWith(battleData)

        assertNull(manager.getCharacterByNameOrAlias("不存在的干员-WARRIOR"))
        assertNull(manager.getCharacterByNameOrAlias("-WARRIOR"))
    }
}
