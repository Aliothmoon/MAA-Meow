package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.domain.models.GameMetadataKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 干员表与地图只随资源版本重解，切语言或客户端只重做投影
 */
class ResourceDataManagerLoadTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val resourceDir by lazy { tempFolder.newFolder("resource") }

    private val manager by lazy {
        ResourceDataManager(mockk<MaaPathConfig> {
            every { this@mockk.resourceDir } returns this@ResourceDataManagerLoadTest.resourceDir.absolutePath
        })
    }

    private fun writeBattleData(vararg names: Pair<String, String>) {
        val chars = names.joinToString(",") { (id, name) ->
            """"$id": { "name": "$name", "name_en": "${name}_en", "profession": "CASTER", "rarity": 6 }"""
        }
        File(resourceDir, "battle_data.json").writeText("""{ "chars": { $chars } }""")
    }

    @Test
    fun languageSwitchReusesParsedBattleData() = runBlocking {
        writeBattleData("char_001_alpha" to "甲")
        manager.load("Official", "zh-cn", revision = 0)

        // 同版本内文件不会变，这里改它只为看出有没有重读
        writeBattleData("char_002_beta" to "乙")
        manager.load("Official", "en-us", revision = 0)

        val snapshot = manager.snapshot.value
        assertEquals(GameMetadataKey("Official", "en-us", 0), snapshot.key)
        assertEquals(setOf("char_001_alpha"), snapshot.characters.keys)
        assertEquals(setOf("甲_en", "甲"), snapshot.characterNames)
    }

    @Test
    fun revisionChangeReparses() = runBlocking {
        writeBattleData("char_001_alpha" to "甲")
        manager.load("Official", "zh-cn", revision = 0)

        writeBattleData("char_002_beta" to "乙")
        manager.load("Official", "zh-cn", revision = 1)

        assertEquals(setOf("char_002_beta"), manager.characters.value.keys)
        assertEquals("乙", manager.getCharacterByNameOrAlias("乙")?.name)
    }

    @Test
    fun missingBattleDataFailsLoadAndIsRetried() = runBlocking {
        val error = runCatching { manager.load("Official", "zh-cn", revision = 0) }.exceptionOrNull()
        assertTrue("干员表读空必须让本次加载失败，否则同一 key 不再重试", error is IllegalStateException)
        assertEquals(emptyMap<String, CharacterInfo>(), manager.characters.value)

        writeBattleData("char_001_alpha" to "甲")
        manager.load("Official", "zh-cn", revision = 0)

        assertEquals(setOf("char_001_alpha"), manager.characters.value.keys)
    }
}
