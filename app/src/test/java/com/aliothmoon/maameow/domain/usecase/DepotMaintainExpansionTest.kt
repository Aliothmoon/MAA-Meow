package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.CollectingPreflightLogSink
import com.aliothmoon.maameow.data.model.DepotMaintainConfig
import com.aliothmoon.maameow.data.model.DepotMaintainPlan
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.model.testTaskParamContext
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ItemInfo
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 库存保持任务的展开契约。
 *
 * 对应上游 DepotMaintainTaskUserControlModel.ISerialize.Serialize，
 * 覆盖跳过条件、逐条计划的校验分支、缺口计算与日志序号。
 */
class DepotMaintainExpansionTest {

    private val depotRepository: DepotRepository = mockk()
    private val activityManager: ActivityManager = mockk()
    private val itemHelper: ItemHelper = mockk()

    private data class Expansion(
        val params: List<MaaTaskParams>,
        val logs: List<Pair<UiText, LogLevel>>,
    )

    private fun DepotMaintainConfig.expand(
        inventory: Map<String, Int> = emptyMap(),
        activityOpen: Boolean = false,
        resourceCollectionOpen: Boolean = false,
        openStages: Set<String> = setOf(STAGE),
        clientType: String = "Official",
    ): Expansion {
        every { depotRepository.countOf(any()) } answers { inventory[firstArg()] ?: 0 }
        every { activityManager.isActivityOpen() } returns activityOpen
        every { activityManager.isResourceCollectionOpen() } returns resourceCollectionOpen
        every { activityManager.isStageOpen(any()) } answers { firstArg<String>() in openStages }
        every { itemHelper.getItemInfo(any()) } answers {
            if (firstArg<String>() == ITEM) ItemInfo(id = ITEM, name = "源岩") else null
        }
        val sink = CollectingPreflightLogSink()
        val context = testTaskParamContext(
            clientType = clientType,
            activityManager = activityManager,
            depotRepository = depotRepository,
            itemHelper = itemHelper,
            logSink = sink,
        )
        return Expansion(toTaskParams(context), sink.entries)
    }

    private fun plan(
        stage: String = STAGE,
        dropId: String = ITEM,
        dropCount: Int = 100,
        useMedicine: Boolean = false,
        medicineCount: Int = 0,
        useStone: Boolean = false,
        stoneCount: Int = 0,
    ) = DepotMaintainPlan(stage, dropId, dropCount, useMedicine, medicineCount, useStone, stoneCount)

    private fun config(
        vararg plans: DepotMaintainPlan,
        updateDepot: Boolean = false,
        customStageCode: Boolean = false,
        useAutoSeries: Boolean = false,
        skipDuringActivity: Boolean = false,
        skipDuringResourceCollection: Boolean = false,
        useMedicine: Boolean = true,
        useStone: Boolean = true,
        useExpiringMedicine: Boolean = false,
    ) = DepotMaintainConfig(
        updateDepot = updateDepot,
        customStageCode = customStageCode,
        useAutoSeries = useAutoSeries,
        skipDuringActivity = skipDuringActivity,
        skipDuringResourceCollection = skipDuringResourceCollection,
        useMedicine = useMedicine,
        useStone = useStone,
        useExpiringMedicine = useExpiringMedicine,
        plans = plans.toList(),
    )

    /** 分段行只是排版，逐条计划的断言一律先把它滤掉 */
    private fun List<Pair<UiText, LogLevel>>.planLogs(): List<Pair<UiText, LogLevel>> =
        filterNot { (it.first as? UiText.Resource)?.resId == R.string.runlog_log_section }

    private fun List<Pair<UiText, LogLevel>>.resIds(): List<Int> =
        planLogs().map { (it.first as UiText.Resource).resId }

    private fun logArgsOf(
        logs: List<Pair<UiText, LogLevel>>,
        resId: Int,
    ): List<Any?> = logs.map { it.first }
        .filterIsInstance<UiText.Resource>()
        .first { it.resId == resId }
        .args

    private fun MaaTaskParams.json() = Json.parseToJsonElement(params).jsonObject

    // --- 跳过条件 ---

    @Test
    fun skipDuringActivity_whenActivityOpen_producesNothing() {
        val result = config(plan(), skipDuringActivity = true, updateDepot = true)
            .expand(activityOpen = true)
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_skipped_activity), result.logs.resIds())
    }

    @Test
    fun skipDuringActivity_whenNoActivity_expandsNormally() {
        val result = config(plan(), skipDuringActivity = true)
            .expand(activityOpen = false)
        assertEquals(1, result.params.size)
        assertEquals(MaaTaskType.FIGHT, result.params[0].type)
        assertEquals(listOf(R.string.runlog_depot_plan_inventory_insufficient), result.logs.resIds())
    }

    /** 开关关闭时，活动开放也不得跳过（锁住条件的另一端） */
    @Test
    fun skipSwitchesOff_ignoreOpenActivityAndResourceCollection() {
        val result = config(plan(), skipDuringActivity = false, skipDuringResourceCollection = false)
            .expand(activityOpen = true, resourceCollectionOpen = true)
        assertEquals(1, result.params.size)
        assertEquals(listOf(R.string.runlog_depot_plan_inventory_insufficient), result.logs.resIds())
    }

    /** 两个条件同时成立时，活动跳过优先（对齐上游的判断顺序） */
    @Test
    fun bothSkipConditionsMet_reportsActivityFirst() {
        val result = config(plan(), skipDuringActivity = true, skipDuringResourceCollection = true)
            .expand(activityOpen = true, resourceCollectionOpen = true)
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_skipped_activity), result.logs.resIds())
    }

    @Test
    fun skipDuringResourceCollection_whenOpen_producesNothing() {
        val result = config(plan(), skipDuringResourceCollection = true)
            .expand(resourceCollectionOpen = true)
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_skipped_resource), result.logs.resIds())
    }

    /** 活动跳过只看 SideStory，资源收集开放不应触发它，否则两个开关就分不开了 */
    @Test
    fun skipDuringActivity_isNotTriggeredByResourceCollection() {
        val result = config(plan(), skipDuringActivity = true)
            .expand(activityOpen = false, resourceCollectionOpen = true)
        assertEquals(1, result.params.size)
        assertEquals(listOf(R.string.runlog_depot_plan_inventory_insufficient), result.logs.resIds())
    }

    // --- Depot 前置任务 ---

    @Test
    fun updateDepot_prependsDepotTask() {
        val result = config(plan(), updateDepot = true).expand()
        assertEquals(2, result.params.size)
        assertEquals(MaaTaskType.DEPOT, result.params[0].type)
        assertEquals(MaaTaskType.FIGHT, result.params[1].type)
    }

    @Test
    fun updateDepotDisabled_producesOnlyFight() {
        val result = config(plan(), updateDepot = false).expand()
        assertEquals(1, result.params.size)
        assertEquals(MaaTaskType.FIGHT, result.params[0].type)
    }

    /**
     * 全部计划无效时仍保留 Depot 识别 —— 对齐上游
     * （上游判定条件 taskIds.Any(id > 0) 对 Depot 的 id 同样成立）。
     */
    @Test
    fun allPlansInvalid_stillKeepsDepotTask() {
        val result = config(plan(dropId = ""), plan(dropCount = 0), updateDepot = true).expand()
        assertEquals(1, result.params.size)
        assertEquals(MaaTaskType.DEPOT, result.params[0].type)
        assertEquals(
            listOf(R.string.runlog_depot_plan_invalid_drop, R.string.runlog_depot_plan_zero_count),
            result.logs.resIds(),
        )
    }

    // --- 逐条计划的校验分支 ---

    @Test
    fun blankDropId_isRejected() {
        val result = config(plan(dropId = "")).expand()
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_plan_invalid_drop), result.logs.resIds())
        assertEquals(listOf<Any?>(1), logArgsOf(result.logs, R.string.runlog_depot_plan_invalid_drop))
        assertEquals(LogLevel.ERROR, result.logs.planLogs()[0].second)
    }

    @Test
    fun nonPositiveDropCount_isRejected() {
        val result = config(plan(dropCount = 0)).expand()
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_plan_zero_count), result.logs.resIds())
    }

    @Test
    fun blankStage_isRejected() {
        val result = config(plan(stage = "")).expand()
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_plan_no_stage), result.logs.resIds())
    }

    /** 关卡未开放：对齐上游，不算 ERROR（上游 AddLog 未传色，默认 Trace），且日志带关卡代码 */
    @Test
    fun closedStage_isRejectedWithoutErrorLevel() {
        val result = config(plan(stage = "CE-6")).expand(openStages = emptySet())
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_plan_stage_not_open), result.logs.resIds())
        assertEquals(LogLevel.TRACE, result.logs.planLogs()[0].second)
        assertEquals(
            listOf<Any?>(1, "CE-6"),
            logArgsOf(result.logs, R.string.runlog_depot_plan_stage_not_open),
        )
    }

    /**
     * 手动关卡代码模式**不**豁免开放检查 —— 上游 GetFightStage 无条件走 IsStageOpen，
     * customStageCode 只改变 UI 控件形态。放行会让 MaaCore 导航失败并中断整条任务链。
     */
    @Test
    fun closedStage_isStillRejectedWhenCustomStageCode() {
        val result = config(plan(stage = "XX-9"), customStageCode = true)
            .expand(openStages = emptySet())
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_plan_stage_not_open), result.logs.resIds())
    }

    /** 空串检查先于开放检查：手动模式留空仍报「未指定关卡」而非原样透传 */
    @Test
    fun blankStage_isRejectedEvenWhenCustomStageCode() {
        val result = config(plan(stage = ""), customStageCode = true).expand()
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_plan_no_stage), result.logs.resIds())
    }

    // --- 缺口计算 ---

    @Test
    fun sufficientInventory_skipsPlan() {
        val result = config(plan(dropCount = 100)).expand(inventory = mapOf(ITEM to 100))
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_plan_inventory_enough), result.logs.resIds())
        // 第 1 个占位符是 %1$s，刷理智/任务链复用同一条时传的是任务名
        assertEquals(
            listOf<Any?>("#1", "源岩", 100, 100),
            logArgsOf(result.logs, R.string.runlog_depot_plan_inventory_enough),
        )
    }

    @Test
    fun partialInventory_dropsHoldRemainingDeficit() {
        val result = config(plan(dropCount = 100)).expand(inventory = mapOf(ITEM to 30))
        val drops = result.params[0].json()["drops"]!!.jsonObject
        assertEquals(70, drops[ITEM]!!.jsonPrimitive.content.toInt())
    }

    /** 物品名查不到时日志回退成材料 ID，不能显示 null */
    @Test
    fun sufficientInventory_fallsBackToItemIdWhenNameUnknown() {
        val result = config(plan(dropId = "99999", dropCount = 1))
            .expand(inventory = mapOf("99999" to 5))
        assertEquals(
            listOf<Any?>("#1", "99999", 5, 1),
            logArgsOf(result.logs, R.string.runlog_depot_plan_inventory_enough),
        )
    }

    @Test
    fun fightJson_carriesMaxTimesAndConsumables() {
        val result = config(
            plan(useMedicine = true, medicineCount = 5, useStone = true, stoneCount = 2)
        ).expand()
        val json = result.params[0].json()
        assertEquals(STAGE, json["stage"]!!.jsonPrimitive.content)
        assertEquals(Int.MAX_VALUE, json["times"]!!.jsonPrimitive.content.toInt())
        assertEquals(5, json["medicine"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, json["stone"]!!.jsonPrimitive.content.toInt())
        assertEquals(listOf(R.string.runlog_depot_plan_inventory_insufficient), result.logs.resIds())
    }

    /** 对齐 WPF：默认 series=1；UseAutoSeries 勾选时 series=0（AUTO） */
    @Test
    fun fightJson_seriesDefaultsToOne_andAutoSeriesEmitsZero() {
        val defaultSeries = config(plan()).expand()
        assertEquals(1, defaultSeries.params[0].json()["series"]!!.jsonPrimitive.content.toInt())

        val auto = config(plan(), useAutoSeries = true).expand()
        assertEquals(0, auto.params[0].json()["series"]!!.jsonPrimitive.content.toInt())
    }

    /**
     * 对齐 WPF 6.16：库存已够时先跳过，不再判断关卡是否开放。
     * 避免关卡关闭时把「库存已够」误报成「关卡未开放」。
     */
    @Test
    fun sufficientInventory_skipsBeforeStageOpenCheck() {
        val result = config(plan(dropCount = 100, stage = "XX-9"))
            .expand(inventory = mapOf(ITEM to 100), openStages = emptySet())
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_plan_inventory_enough), result.logs.resIds())
    }

    /** 空关卡同样排在库存检查之后：已达标就报「库存已足」，不报 ERROR 级的「未指定关卡」 */
    @Test
    fun sufficientInventory_skipsBeforeBlankStageCheck() {
        val result = config(plan(dropCount = 100, stage = ""))
            .expand(inventory = mapOf(ITEM to 100))
        assertTrue(result.params.isEmpty())
        assertEquals(listOf(R.string.runlog_depot_plan_inventory_enough), result.logs.resIds())
        assertEquals(LogLevel.TRACE, result.logs.planLogs()[0].second)
    }

    /** 配置不完整的两项仍排在库存检查之前：没选材料时连缺口都算不了 */
    @Test
    fun incompleteConfig_isReportedBeforeInventoryCheck() {
        val noItem = config(plan(dropId = "", dropCount = 1)).expand(inventory = mapOf(ITEM to 999))
        assertEquals(listOf(R.string.runlog_depot_plan_invalid_drop), noItem.logs.resIds())

        val zeroTarget = config(plan(dropCount = 0)).expand(inventory = mapOf(ITEM to 999))
        assertEquals(listOf(R.string.runlog_depot_plan_zero_count), zeroTarget.logs.resIds())
    }

    /** 开关关闭时数量必须归零，而不是沿用已保存的值 */
    @Test
    fun fightJson_zeroesConsumablesWhenSwitchesOff() {
        val result = config(
            plan(useMedicine = false, medicineCount = 5, useStone = false, stoneCount = 2)
        ).expand()
        val json = result.params[0].json()
        assertEquals(0, json["medicine"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, json["stone"]!!.jsonPrimitive.content.toInt())
    }

    // --- 任务级药/源石总开关 ---

    /** 总开关关掉后，计划自己勾着也得归零；计划里的勾选值不清空，重开即恢复 */
    @Test
    fun taskLevelSwitchesOff_zeroConsumablesEvenWhenPlanChecked() {
        val checked = plan(useMedicine = true, medicineCount = 5, useStone = true, stoneCount = 2)

        val medicineOff = config(checked, useMedicine = false).expand().params[0].json()
        assertEquals(0, medicineOff["medicine"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, medicineOff["stone"]!!.jsonPrimitive.content.toInt())

        val stoneOff = config(checked, useStone = false).expand().params[0].json()
        assertEquals(5, stoneOff["medicine"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, stoneOff["stone"]!!.jsonPrimitive.content.toInt())
    }

    /** 总开关默认开，行为与改动前一致 */
    @Test
    fun taskLevelSwitchesDefaultOn_keepPlanValues() {
        val result = config(
            plan(useMedicine = true, medicineCount = 5, useStone = true, stoneCount = 2)
        ).expand()
        val json = result.params[0].json()
        assertEquals(5, json["medicine"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, json["stone"]!!.jsonPrimitive.content.toInt())
    }

    // --- 临期药 ---

    /** 关掉时不下发 medicine_expire_days，让 core 用默认值 */
    @Test
    fun expiringMedicineOff_omitsExpireDays() {
        val json = config(plan()).expand().params[0].json()
        assertTrue("medicine_expire_days" !in json)
    }

    /** 阈值固定 2 天，不随计划或理智作战的设置变化 */
    @Test
    fun expiringMedicineOn_emitsFixedThreshold() {
        val json = config(plan(), useExpiringMedicine = true).expand().params[0].json()
        assertEquals(
            DepotMaintainConfig.EXPIRING_MEDICINE_DAYS,
            json["medicine_expire_days"]!!.jsonPrimitive.content.toInt(),
        )
    }

    /** 临期药与药剂总开关互不影响：药关了照样带阈值（medicine=0 时 core 自行忽略） */
    @Test
    fun expiringMedicine_isIndependentOfUseMedicineSwitch() {
        val json = config(
            plan(useMedicine = true, medicineCount = 5),
            useMedicine = false,
            useExpiringMedicine = true,
        ).expand().params[0].json()
        assertEquals(0, json["medicine"]!!.jsonPrimitive.content.toInt())
        assertEquals(
            DepotMaintainConfig.EXPIRING_MEDICINE_DAYS,
            json["medicine_expire_days"]!!.jsonPrimitive.content.toInt(),
        )
    }

    // --- 空计划 ---

    /** 没有计划就不该留一条光杆分割线 */
    @Test
    fun noPlans_withUpdateDepot_producesOnlyDepot() {
        val result = config(updateDepot = true).expand()
        assertEquals(listOf(MaaTaskType.DEPOT), result.params.map { it.type })
        assertTrue(result.logs.isEmpty())
    }

    @Test
    fun noPlans_withoutUpdateDepot_producesNothing() {
        val result = config(updateDepot = false).expand()
        assertTrue(result.params.isEmpty())
        assertTrue(result.logs.isEmpty())
    }

    @Test
    fun withPlans_prependsLogSection() {
        val result = config(plan()).expand()
        assertEquals(R.string.runlog_log_section, (result.logs[0].first as UiText.Resource).resId)
    }

    // --- 顺序 ---

    /** 计划顺序即优先级，产出的 Fight 必须与 plans 同序 */
    @Test
    fun multiplePlans_keepConfiguredOrder() {
        val result = config(plan(dropCount = 10), plan(dropCount = 20), plan(dropCount = 30)).expand()
        assertEquals(
            listOf(10, 20, 30),
            result.params.map { it.json()["drops"]!!.jsonObject[ITEM]!!.jsonPrimitive.content.toInt() },
        )
    }

    /** 被跳过的计划只是不产出任务，不影响其余计划的内容与相对顺序 */
    @Test
    fun skippedPlans_doNotAffectRemainingPlans() {
        val result = config(
            plan(dropId = ""), plan(dropCount = 20), plan(stage = ""), plan(dropCount = 40)
        ).expand()
        assertEquals(
            listOf(20, 40),
            result.params.map { it.json()["drops"]!!.jsonObject[ITEM]!!.jsonPrimitive.content.toInt() },
        )
    }

    /** 日志序号取原始位置，不能因前面有计划被跳过而错位 */
    @Test
    fun logNumbering_usesOneBasedOriginalPosition() {
        val result = config(plan(), plan(), plan(dropId = "")).expand()
        assertEquals(listOf<Any?>(3), logArgsOf(result.logs, R.string.runlog_depot_plan_invalid_drop))
    }

    private companion object {
        const val STAGE = "1-7"
        const val ITEM = "30011"
    }
}

