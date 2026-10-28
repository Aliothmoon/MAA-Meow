package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperProgressConfigTest {
    private val chen = CharacterInfo(id = "char_010_chen", name = "陈", profession = "WARRIOR", rarity = 6)
    private val plan = OperProgressPlan.forOperator(chen).copy(id = "chen")
    private fun context(logs: PreflightLogSink = CollectingPreflightLogSink()): TaskParamContext {
        val manager = mockk<ResourceDataManager> {
            every { operators } returns MutableStateFlow(mapOf(chen.id to chen))
        }
        return testTaskParamContext(resourceDataManager = manager, logSink = logs)
    }

    @Test
    fun serializesOnlyCoreFields_andOmitsUnsetTargets() {
        val configured = plan.copy(elite = 0, skillLevel = 0, skillMastery = listOf(0, 0, 3),
            training = OperProgressTraining(3, 2))
        val task = OperProgressConfig(listOf(configured)).toTaskParams(context()).single()
        assertEquals("OperProgress", task.type.value)
        assertEquals("{\"plans\":[{\"role\":\"Warrior\",\"name\":\"陈\",\"skill_mastery\":[0,0,3]}]}", task.params)
    }

    @Test
    fun omitsAllZeroMastery() {
        val task = OperProgressConfig(listOf(plan.copy(skillMastery = listOf(0, 0, 0)))).toTaskParams(context()).single()
        val json = Json.parseToJsonElement(task.params).jsonObject["plans"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("role", "name", "elite", "skill_level"), json.keys)
    }

    @Test
    fun emptyPlansSkipWithAnExplanation() {
        val logs = CollectingPreflightLogSink()
        assertTrue(OperProgressConfig().toTaskParams(context(logs)).isEmpty())
        assertEquals(R.string.oper_progress_empty, (logs.entries.single().first as com.aliothmoon.maameow.utils.i18n.UiText.Resource).resId)
    }

    @Test
    fun invalidAndDuplicatePlansAreNotSubmitted() {
        val invalid = listOf(
            plan.copy(elite = 3), plan.copy(skillLevel = 1),
            plan.copy(skillMastery = listOf(3, 3)), plan.copy(skillMastery = listOf(3, 3, 4)),
            plan.copy(skillLevel = 6), plan.copy(name = "不存在"), plan.copy(role = "Caster"),
        )
        invalid.forEach { assertTrue(OperProgressConfig(listOf(it)).toTaskParams(context()).isEmpty()) }
        assertTrue(OperProgressConfig(listOf(plan, plan.copy(id = "duplicate"))).toTaskParams(context()).isEmpty())
    }

    @Test
    fun profileRoundTripPreservesOrderTargetsAndTraining() {
        val second = plan.copy(id = "other", name = "另一个", training = OperProgressTraining(2, 1))
        val profile = TaskProfile(name = "培养", chain = listOf(TaskChainNode(name = "计划", config = OperProgressConfig(listOf(second, plan)))))
        assertEquals(profile, Json.decodeFromString<TaskProfile>(Json.encodeToString(profile)))
        assertEquals(OperProgressConfig(), Json.decodeFromString<OperProgressConfig>("{}"))
        assertFalse(TaskTypeInfo.OPER_PROGRESS.inDefaultChain)
    }

    @Test
    fun targetsFollowAvailablePromotionAndSkills() {
        val three = chen.copy(rarity = 3)
        val four = chen.copy(rarity = 4)
        assertEquals(1, OperProgressPlan.forOperator(three).elite)
        assertEquals(listOf(0, 0, 0), OperProgressPlan.forOperator(three).skillMastery)
        assertEquals(listOf(3, 3, 0), OperProgressPlan.forOperator(four).skillMastery)
        assertTrue(OperProgressPlan.forOperator(three).isValidFor(three))
        assertFalse(plan.isValidFor(three))
    }

    @Test
    fun lowerBaseSkillClearsMastery_andMasteryRaisesAnExistingSkillTarget() {
        val lower = plan.withSkillLevel(6)
        assertEquals(listOf(0, 0, 0), lower.skillMastery)
        assertEquals(7, lower.withMastery(2, 2).skillLevel)
        assertEquals(0, lower.copy(skillLevel = 0).withMastery(2, 2).skillLevel)
    }

    @Test
    fun partialCompletionRetainsOtherTargetsAndStableId() {
        val result = OperProgressResult("陈", "Warrior", elite = 2, skillLevel = 7)
        val updated = OperProgressConfig(listOf(plan)).applyResults(listOf(result), removeFinished = true).plans.single()
        assertEquals("chen", updated.id)
        assertEquals(0, updated.elite)
        assertEquals(0, updated.skillLevel)
        assertEquals(listOf(3, 3, 3), updated.skillMastery)
    }

    @Test
    fun startingFinalMasteryKeepsTargetUntilActuallyConfirmed() {
        val configured = plan.copy(elite = 0, skillLevel = 0, skillMastery = listOf(0, 0, 3))
        val result = OperProgressResult("陈", "Warrior", skillMastery = listOf(0, 0, 3), trainingStarted = true)
        val started = OperProgressConfig(listOf(configured)).applyResults(listOf(result), removeFinished = true)
        assertEquals(listOf(0, 0, 3), started.plans.single().skillMastery)
        assertEquals(OperProgressTraining(3, 3), started.plans.single().training)
        val done = started.applyResults(listOf(result.copy(trainingStarted = false)), removeFinished = true)
        assertTrue(done.plans.isEmpty())
    }

    @Test
    fun reportedMasteryClearsOnlyReachedSkills() {
        val updated = OperProgressConfig(listOf(plan)).applyResults(listOf(
            OperProgressResult("陈", "Warrior", skillMastery = listOf(3, 2, 0)),
        )).plans.single()
        assertEquals(listOf(0, 3, 3), updated.skillMastery)
    }

    @Test
    fun confirmingTrainingClearsItsStatusWhileOtherTargetsRemain() {
        val config = OperProgressConfig(listOf(plan.copy(training = OperProgressTraining(1, 2))))
        val updated = config.applyResults(listOf(
            OperProgressResult("陈", "Warrior", skillMastery = listOf(2, 0, 0)),
        )).plans.single()
        assertEquals(listOf(3, 3, 3), updated.skillMastery)
        assertEquals(null, updated.training)
        val unrelated = config.applyResults(listOf(
            OperProgressResult("陈", "Warrior", skillMastery = listOf(0, 3, 0)),
        )).plans.single()
        assertEquals(OperProgressTraining(1, 2), unrelated.training)
    }

    @Test
    fun callbacksDoNotTouchOtherNamesOrRoles() {
        val config = OperProgressConfig(listOf(plan))
        assertEquals(config, config.applyResults(listOf(
            OperProgressResult("陈", "Caster", elite = 2),
            OperProgressResult("其他干员", "Warrior", skillLevel = 7),
        )))
    }

    @Test
    fun corePlanOrderMatchesSavedCardOrder() {
        val other = chen.copy(id = "char_other", name = "另一名")
        val manager = mockk<ResourceDataManager> {
            every { operators } returns MutableStateFlow(mapOf(chen.id to chen, other.id to other))
        }
        val config = OperProgressConfig(listOf(OperProgressPlan.forOperator(other), plan))
        val json = Json.parseToJsonElement(config.toTaskParams(testTaskParamContext(resourceDataManager = manager)).single().params).jsonObject
        assertEquals(listOf("另一名", "陈"), json["plans"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content })
    }

    @Test
    fun emptyOperatorTable_leavesValidationToCore() {
        // 冷启动时干员表可能还没装载，不能因此整条任务不下发
        val manager = mockk<ResourceDataManager> {
            every { operators } returns MutableStateFlow(emptyMap())
        }
        val ctx = testTaskParamContext(resourceDataManager = manager)
        assertEquals(1, OperProgressConfig(listOf(plan)).toTaskParams(ctx).size)
    }
}
