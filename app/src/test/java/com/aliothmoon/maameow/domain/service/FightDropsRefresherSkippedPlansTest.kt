package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ItemInfo
import com.aliothmoon.maameow.domain.models.SkippedDepotPlans
import com.aliothmoon.maameow.domain.models.StaleSkippedPlan
import com.aliothmoon.maameow.maa.task.TaskSlot
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 上游 70d1f06bb8：仓库识别后复查预检时跳过的库存保持计划 */
class FightDropsRefresherSkippedPlansTest {

    private val inventory = mutableMapOf<String, Int>()
    private val depotRepository: DepotRepository = mockk {
        every { countOf(any()) } answers { inventory[firstArg()] ?: 0 }
    }
    private val itemHelper: ItemHelper = mockk {
        every { getItemInfo(any()) } answers {
            if (firstArg<String>() == ROCK) ItemInfo(id = ROCK, name = "源岩") else null
        }
    }
    private val refresher = FightDropsRefresher(depotRepository, itemHelper, mockk(), mockk())

    @Before
    fun setUp() {
        inventory[ROCK] = 50
        inventory[DEVICE] = 50
    }

    private fun skipped(firstOnly: Boolean = false) = SkippedDepotPlans(
        taskName = "材料补货",
        plans = listOf(
            SkippedDepotPlans.Plan(no = 1, dropId = ROCK, dropCount = 40),
            SkippedDepotPlans.Plan(no = 3, dropId = DEVICE, dropCount = 40),
        ),
        firstOnly = firstOnly,
    )

    /** 主链任务入队时才 bind，模拟一轮真正开跑 */
    private fun startRun() = refresher.bind(TaskSlot("n1", 0), taskId = 1)

    @Test
    fun flippedPlans_areReportedOnce() {
        refresher.stageSkipped(skipped())
        startRun()
        inventory[ROCK] = 30
        inventory[DEVICE] = 10

        assertEquals(
            listOf(
                StaleSkippedPlan("材料补货", 1, "源岩", 30, 40),
                StaleSkippedPlan("材料补货", 3, DEVICE, 10, 40),
            ),
            refresher.reviewSkippedPlans(),
        )
        // 同一轮后面再识别一次不重复提示
        assertEquals(emptyList<StaleSkippedPlan>(), refresher.reviewSkippedPlans())
    }

    @Test
    fun stillEnough_isNotReported() {
        refresher.stageSkipped(skipped())
        startRun()

        assertEquals(emptyList<StaleSkippedPlan>(), refresher.reviewSkippedPlans())
    }

    @Test
    fun firstOnlyMode_reportsFirstFlipOnly() {
        refresher.stageSkipped(skipped(firstOnly = true))
        startRun()
        inventory[ROCK] = 0
        inventory[DEVICE] = 0

        assertEquals(listOf(1), refresher.reviewSkippedPlans().map { it.no })
    }

    @Test
    fun firstOnlyMode_ignoresFlipsBehindTheChosenPlan() {
        refresher.stageSkipped(skipped(firstOnly = true).copy(chosen = SkippedDepotPlans.Plan(2, OTHER, 100)))
        startRun()
        inventory[DEVICE] = 0 // #3 排在主计划 #2 之后，本就留到下次运行

        assertEquals(emptyList<StaleSkippedPlan>(), refresher.reviewSkippedPlans())
    }

    @Test
    fun firstOnlyMode_reportsFlipAheadOfTheChosenPlan() {
        refresher.stageSkipped(skipped(firstOnly = true).copy(chosen = SkippedDepotPlans.Plan(2, OTHER, 100)))
        startRun()
        inventory[ROCK] = 0
        inventory[DEVICE] = 0

        assertEquals(listOf(1), refresher.reviewSkippedPlans().map { it.no })
    }

    // ---- 按达成率排序 ----

    private fun sorted(
        vararg plans: SkippedDepotPlans.Plan,
        firstOnly: Boolean = false,
        chosen: SkippedDepotPlans.Plan? = null,
    ) = SkippedDepotPlans(
        taskName = "材料补货",
        plans = plans.toList(),
        firstOnly = firstOnly,
        sortByProgress = true,
        chosen = chosen,
    )

    /** 缓存 A=10/100 被选为主计划、B=100/100 跳过；识别后 B=0，按真实数据 B 应先执行 */
    @Test
    fun sorted_reportsFlipThatWouldHaveRunBeforeTheChosenPlan() {
        inventory[OTHER] = 10
        refresher.stageSkipped(
            sorted(SkippedDepotPlans.Plan(2, ROCK, 100), firstOnly = true, chosen = SkippedDepotPlans.Plan(1, OTHER, 100))
        )
        startRun()
        inventory[ROCK] = 0

        assertEquals(listOf(2), refresher.reviewSkippedPlans().map { it.no })
    }

    /** B 翻到 50/100，仍高于主计划 A 的 10/100，即便数据准确也轮不到它 */
    @Test
    fun sorted_ignoresFlipThatStillRanksBehindTheChosenPlan() {
        inventory[OTHER] = 10
        refresher.stageSkipped(
            sorted(SkippedDepotPlans.Plan(2, ROCK, 100), firstOnly = true, chosen = SkippedDepotPlans.Plan(1, OTHER, 100))
        )
        startRun()
        inventory[ROCK] = 50

        assertEquals(emptyList<StaleSkippedPlan>(), refresher.reviewSkippedPlans())
    }

    /** 登记时 A=100/100 排在 B=200/100 前；识别后 A=90、B=10，按最新达成率应先提示 B */
    @Test
    fun sorted_firstOnly_picksLowestLatestProgress() {
        refresher.stageSkipped(
            sorted(SkippedDepotPlans.Plan(1, ROCK, 100), SkippedDepotPlans.Plan(2, DEVICE, 100), firstOnly = true)
        )
        startRun()
        inventory[ROCK] = 90
        inventory[DEVICE] = 10

        assertEquals(listOf(2), refresher.reviewSkippedPlans().map { it.no })
    }

    @Test
    fun sorted_allPlans_reportInLatestProgressOrder_tiesByNumber() {
        refresher.stageSkipped(
            sorted(
                SkippedDepotPlans.Plan(1, ROCK, 100),
                SkippedDepotPlans.Plan(2, DEVICE, 100),
                SkippedDepotPlans.Plan(3, OTHER, 100),
            )
        )
        startRun()
        inventory[ROCK] = 90
        inventory[DEVICE] = 10
        inventory[OTHER] = 90

        assertEquals(listOf(2, 1, 3), refresher.reviewSkippedPlans().map { it.no })
    }

    @Test
    fun analysedButNotStarted_isNotReviewed() {
        // 只分析没开跑，期间工具箱的仓库识别不该带出提示
        refresher.stageSkipped(skipped())
        inventory[ROCK] = 0

        assertEquals(emptyList<StaleSkippedPlan>(), refresher.reviewSkippedPlans())
    }

    @Test
    fun clear_dropsStagedPlans() {
        refresher.stageSkipped(skipped())
        startRun()
        refresher.clear()
        startRun()
        inventory[ROCK] = 0

        assertEquals(emptyList<StaleSkippedPlan>(), refresher.reviewSkippedPlans())
    }

    private companion object {
        const val ROCK = "30011"
        const val DEVICE = "30061"
        const val OTHER = "30012"
    }
}
