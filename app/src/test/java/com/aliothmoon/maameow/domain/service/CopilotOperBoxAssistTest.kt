package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.toolbox.OperBoxEquip
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.model.toolbox.OperBoxSkill
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.repository.OperBoxSnapshot
import com.aliothmoon.maameow.utils.JsonUtils
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CopilotOperBoxAssistTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val trained = OperBoxOperator(
        id = "char_002_amiya", name = "阿米娅", rarity = 5, elite = 2, level = 80, potential = 6, own = true,
        mainSkillLevel = 7,
        skills = listOf(OperBoxSkill("skchr_amiya_1", 3)),
        equips = listOf(OperBoxEquip("uniequip_002_amiya", "X", 2)),
    )

    /** 本地识别结果没有练度字段 */
    private val recognized = OperBoxOperator(
        id = "char_124_kroos", name = "克洛丝", rarity = 2, elite = 1, level = 55, potential = 6, own = true,
    )

    private fun assist(
        yituliu: Boolean = true,
        enabled: Boolean = true,
        owned: List<OperBoxOperator> = listOf(trained),
        version: Int = OperBoxSnapshot.CURRENT_VERSION,
        root: File = tmp.root,
    ): CopilotOperBoxAssist {
        val settings = mockk<AppSettingsManager> {
            every { operBoxUseYituliuApi } returns MutableStateFlow(yituliu)
            every { operBoxAssistEnabled } returns MutableStateFlow(enabled)
        }
        val repo = mockk<OperBoxRepository> {
            every { snapshot } returns MutableStateFlow(
                OperBoxSnapshot(owned = owned, syncTimeMillis = 1L, version = version)
            )
        }
        val paths = mockk<MaaPathConfig> {
            every { rootDir } returns root.absolutePath
            every { toCorePath(any()) } answers { "core:" + firstArg<String>().removePrefix(root.absolutePath) }
        }
        return CopilotOperBoxAssist(settings, repo, paths)
    }

    private suspend fun CopilotOperBoxAssist.available() = state.first().available

    @Test
    fun availabilityNeedsYituliuSwitchAndTrainingData() = runTest {
        assertTrue(assist().available())
        assertFalse(assist(yituliu = false).available())
        assertFalse(assist(owned = listOf(recognized)).available())
    }

    @Test
    fun switchOffKeepsAvailabilityButIsNotActive() = runTest {
        val state = assist(enabled = false).state.first()
        assertTrue(state.available)
        assertFalse(state.active)
        assertTrue(assist().state.first().active)
        // 数据不可用时保留用户开关，但不生效
        assertFalse(assist(yituliu = false).state.first().active)
    }

    @Test
    fun snapshotFromBeforePromotedFormsWereKept_needsResync() = runTest {
        assertFalse(assist(version = 0).available())
    }

    @Test
    fun coreDataUsesFieldNamesReadByOperBoxDataConfig() {
        val json = JsonUtils.common.parseToJsonElement(
            CopilotOperBoxAssist.encodeCoreData(listOf(trained, recognized))
        ).jsonObject
        val opers = json.getValue("own_opers").jsonArray.map { it.jsonObject }

        val amiya = opers.first()
        assertEquals("char_002_amiya", amiya.getValue("id").jsonPrimitive.content)
        assertEquals(true, amiya.getValue("own").jsonPrimitive.content.toBoolean())
        assertEquals(7, amiya.getValue("mainSkillLevel").jsonPrimitive.int)
        assertEquals(3, amiya.getValue("skills").jsonArray.single().jsonObject.getValue("level").jsonPrimitive.int)
        assertEquals("X", amiya.getValue("equips").jsonArray.single().jsonObject.getValue("type").jsonPrimitive.content)
        // 没练度的不写空字段，core 按缺省 0 处理
        assertEquals(setOf("id", "name", "rarity", "elite", "level", "potential", "own"), opers[1].keys)
    }

    @Test
    fun writeCoreDataReturnsCorePathOfWrittenFile() = runTest {
        val path = assist().writeCoreData()

        assertEquals("core:/operbox/OperBoxData.json".replace('/', File.separatorChar), path)
        val written = File(tmp.root, "operbox/OperBoxData.json").readText()
        assertEquals(CopilotOperBoxAssist.encodeCoreData(listOf(trained)), written)
    }
}
