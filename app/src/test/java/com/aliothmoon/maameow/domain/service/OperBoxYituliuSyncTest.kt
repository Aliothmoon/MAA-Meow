package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.YituliuApiService
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.maa.callback.ToolboxResultCollector
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OperBoxYituliuSyncTest {

    private val api = mockk<YituliuApiService>()
    private val collector = mockk<ToolboxResultCollector>(relaxed = true)

    private val amiya = CharacterInfo(
        id = "char_002_amiya",
        name = "阿米娅",
        nameEn = "Amiya",
        profession = "CASTER",
        rarity = 5,
    )

    /** 转职形态：在 operators 里被当虚拟干员排除，但账号确实拥有 */
    private val amiyaGuard = CharacterInfo(
        id = "char_1001_amiya2",
        name = "阿米娅-WARRIOR",
        profession = "WARRIOR",
        rarity = 5,
    )

    private val token = CharacterInfo(
        id = "token_10012_rosmon_shield",
        name = "守护机器",
        profession = "TOKEN",
        rarity = 0,
    )

    /** [operators] 是本地资源里的全量干员表，不过滤虚拟干员 */
    private fun sync(
        apiToken: String = "t",
        language: String = "zh-cn",
        operators: Map<String, CharacterInfo> = mapOf(amiya.id to amiya),
    ): OperBoxYituliuSync {
        val resource = mockk<ResourceDataManager>(relaxed = true) {
            every { this@mockk.operators } returns MutableStateFlow(operators)
            every { getCharacterById(any()) } answers { operators[firstArg<String>()] }
            every {
                getLocalizedCharacterName(any<CharacterInfo>(), any<String>())
            } answers {
                val info = firstArg<CharacterInfo>()
                if (secondArg<String>() == "en-us") info.nameEn else info.name
            }
        }
        val settings = mockk<AppSettingsManager> {
            every { yituliuOpenApiToken } returns MutableStateFlow(apiToken)
            every { displayLanguage } returns language
        }
        val chainState = mockk<TaskChainState> {
            every { clientType } returns "Official"
        }
        return OperBoxYituliuSync(api, resource, collector, settings, chainState)
    }

    private fun operator(id: String) = YituliuApiService.Operator(
        id = id,
        level = 80,
        evolvePhase = 2,
        potentialRank = 3,
        skills = listOf(YituliuApiService.Skill("skchr_amiya_1", 3)),
        equips = listOf(YituliuApiService.Equip("uniequip_002_amiya", "X", 2)),
    )

    private fun failedWith(resId: Int) = OperBoxYituliuSync.Result.Failed(uiTextOf(resId))

    @Test
    fun blankTokenIsRejectedWithoutRequest() = runTest {
        assertEquals(failedWith(R.string.oper_box_yituliu_token_empty), sync(apiToken = "").sync())
        verify(exactly = 0) { collector.applyOperBoxResult(any()) }
    }

    @Test
    fun localDataFillsNameAndRarity() = runTest {
        coEvery { api.fetchOperators(any()) } returns
                YituliuApiService.Result.Success(listOf(operator(amiya.id)))
        val owned = slot<List<OperBoxOperator>>()

        assertEquals(OperBoxYituliuSync.Result.Success(synced = 1, skipped = 0), sync().sync())

        verify { collector.applyOperBoxResult(capture(owned)) }
        val oper = owned.captured.single()
        assertEquals("阿米娅", oper.name)
        assertEquals(5, oper.rarity)
        assertEquals(2, oper.elite)
        assertEquals(80, oper.level)
        assertEquals(3, oper.potential)
        assertEquals(listOf(3), oper.skills.map { it.level })
        assertEquals(listOf("X"), oper.equips.map { it.type })
    }

    @Test
    fun nameFollowsDisplayLanguage() = runTest {
        coEvery { api.fetchOperators(any()) } returns
                YituliuApiService.Result.Success(listOf(operator(amiya.id)))
        val owned = slot<List<OperBoxOperator>>()

        sync(language = "en-us").sync()

        verify { collector.applyOperBoxResult(capture(owned)) }
        assertEquals("Amiya", owned.captured.single().name)
    }

    @Test
    fun evolvedBranchesAreKept() = runTest {
        // 对齐上游：阿米娅转职形态不属于虚拟干员过滤的范围
        coEvery { api.fetchOperators(any()) } returns YituliuApiService.Result.Success(
            listOf(operator(amiya.id), operator(amiyaGuard.id)),
        )
        val owned = slot<List<OperBoxOperator>>()

        assertEquals(
            OperBoxYituliuSync.Result.Success(synced = 2, skipped = 0),
            sync(operators = mapOf(amiya.id to amiya, amiyaGuard.id to amiyaGuard)).sync(),
        )

        verify { collector.applyOperBoxResult(capture(owned)) }
        assertEquals(listOf(amiya.id, amiyaGuard.id), owned.captured.map { it.id })
    }

    @Test
    fun nonOperatorProfessionsAreSkipped() = runTest {
        coEvery { api.fetchOperators(any()) } returns YituliuApiService.Result.Success(
            listOf(operator(amiya.id), operator(token.id)),
        )
        val owned = slot<List<OperBoxOperator>>()

        assertEquals(
            OperBoxYituliuSync.Result.Success(synced = 1, skipped = 1),
            sync(operators = mapOf(amiya.id to amiya, token.id to token)).sync(),
        )

        verify { collector.applyOperBoxResult(capture(owned)) }
        assertEquals(listOf(amiya.id), owned.captured.map { it.id })
    }

    @Test
    fun unknownOperatorsAreSkippedAndCounted() = runTest {
        coEvery { api.fetchOperators(any()) } returns YituliuApiService.Result.Success(
            listOf(operator(amiya.id), operator("char_999_unknown")),
        )
        val owned = slot<List<OperBoxOperator>>()

        assertEquals(OperBoxYituliuSync.Result.Success(synced = 1, skipped = 1), sync().sync())

        verify { collector.applyOperBoxResult(capture(owned)) }
        assertEquals(listOf(amiya.id), owned.captured.map { it.id })
    }

    @Test
    fun emptyAccountAndOutdatedLocalTableAreToldApart() = runTest {
        coEvery { api.fetchOperators(any()) } returns YituliuApiService.Result.Success(emptyList())
        assertEquals(failedWith(R.string.oper_box_yituliu_no_data), sync().sync())

        coEvery { api.fetchOperators(any()) } returns
                YituliuApiService.Result.Success(listOf(operator("char_999_unknown")))
        assertEquals(failedWith(R.string.oper_box_yituliu_local_table_outdated), sync().sync())

        verify(exactly = 0) { collector.applyOperBoxResult(any()) }
    }

    @Test
    fun missingLocalTableIsReportedBeforeRequest() = runTest {
        assertEquals(
            failedWith(R.string.oper_box_yituliu_local_data_missing),
            sync(operators = emptyMap()).sync(),
        )
        verify(exactly = 0) { collector.applyOperBoxResult(any()) }
    }

    @Test
    fun apiFailuresKeepLocalData() = runTest {
        val cases = mapOf(
            YituliuApiService.Result.Failure.WriteOnly to R.string.oper_box_yituliu_write_only,
            YituliuApiService.Result.Failure.Invalid to R.string.oper_box_yituliu_invalid,
            YituliuApiService.Result.Failure.NetworkError to R.string.oper_box_yituliu_network_error,
        )
        cases.forEach { (apiResult, resId) ->
            coEvery { api.fetchOperators(any()) } returns apiResult
            assertEquals(failedWith(resId), sync().sync())
        }
        verify(exactly = 0) { collector.applyOperBoxResult(any()) }
    }
}
