package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.config.MaaPathConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 肉鸽推荐开局干员依赖干员索引，首次加载就要按客户端过滤并本地化
 */
class ResourceDataManagerRoguelikeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun manager(): ResourceDataManager {
        val resourceDir = tempFolder.newFolder("resource")
        File(resourceDir, "battle_data.json").writeText(
            """
            {
              "chars": {
                "char_001_alpha": { "name": "甲", "name_en": "Alpha", "profession": "CASTER", "rarity": 6 },
                "char_002_beta": { "name": "乙", "name_en": "Beta", "name_en_unavailable": true, "profession": "SNIPER", "rarity": 6 }
              }
            }
            """.trimIndent()
        )
        File(resourceDir, "roguelike/Sami/recruitment.json").apply {
            parentFile!!.mkdirs()
            writeText(
                """
                {"priority":[{"opers":[
                  {"name":"甲","is_start":true},
                  {"name":"乙","is_start":true},
                  {"name":"丙","is_start":false},
                  {"name":"未收录","is_start":true}
                ]}]}
                """.trimIndent()
            )
        }
        val pathConfig = mockk<MaaPathConfig> {
            every { this@mockk.resourceDir } returns resourceDir.absolutePath
        }
        return ResourceDataManager(pathConfig)
    }

    @Test
    fun globalClientFiltersUnavailableAndLocalizesOnFirstLoad() {
        val manager = manager()

        runBlocking { manager.load("YoStarEN", "en-us") }

        assertEquals(listOf("Alpha", "未收录"), manager.roguelikeCoreCharacters.value["Sami"])
    }

    @Test
    fun officialClientKeepsEveryStartingOperator() {
        val manager = manager()

        runBlocking { manager.load("Official", "zh-cn") }

        assertEquals(listOf("甲", "乙", "未收录"), manager.roguelikeCoreCharacters.value["Sami"])
    }
}
