package com.aliothmoon.maameow.data.resource

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.activity.MiniGame
import com.aliothmoon.maameow.data.model.activity.StageActivityInfo
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

/**
 * [ActivityManager.getStageTips] 文本契约，对齐 WPF StageManager.GetStageTips
 */
class ActivityManagerStageTipsTest {

    private val context = mockk<Context> {
        every { getString(R.string.panel_fight_stage_tip_inventory) } returns "库存"
        every { getString(R.string.panel_fight_activity_days_left_open) } returns "剩余天数: "
        every { getString(R.string.panel_fight_activity_less_than_one_day) } returns "不到 1 天"
        every { getString(R.string.panel_fight_stage_tip_affiliated_mini_game, any()) } answers {
            "小工具→牛杂→${secondArg<Array<Any?>>()[0]}"
        }
    }

    private val itemHelper = mockk<ItemHelper> {
        every { getItemInfo("30011") } returns ItemInfo(id = "30011", name = "源岩")
        every { getItemInfo("99999") } returns null
    }

    private val threeDaysLater = System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000 + 60_000

    private val sideStory = StageActivityInfo(
        name = "测试活动", tip = "", utcStartTime = 0L, utcExpireTime = threeDaysLater,
    )

    private fun manager(
        stages: Map<String, MergedStageInfo>,
        miniGames: List<MiniGame> = emptyList(),
    ): ActivityManager {
        val manager = ActivityManager(
            context = context,
            chainState = mockk(relaxed = true),
            maaApiService = mockk(relaxed = true),
            itemHelper = itemHelper,
        )
        setFlow(manager, "_stages", stages)
        setFlow(manager, "_miniGames", miniGames)
        return manager
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(manager: ActivityManager, field: String, value: T) {
        val f = ActivityManager::class.java.getDeclaredField(field).apply { isAccessible = true }
        (f.get(manager) as MutableStateFlow<T>).value = value
    }

    @Test
    fun activityLine_usesLocalizedDaysLeft() {
        val tips = manager(
            mapOf("TA-1" to MergedStageInfo(code = "TA-1", displayName = "TA-1", activity = sideStory))
        ).getStageTips(DayOfWeek.MONDAY)
        assertEquals(listOf("｢测试活动｣ 剩余天数: 3"), tips)
    }

    @Test
    fun dropLine_appendsInventoryOnlyWhenRecognized() {
        val stages = mapOf(
            "TA-1" to MergedStageInfo(code = "TA-1", displayName = "TA-1", activity = sideStory, drop = "30011"),
        )
        assertEquals(
            listOf("｢测试活动｣ 剩余天数: 3", "TA-1: 源岩"),
            manager(stages).getStageTips(DayOfWeek.MONDAY),
        )
        assertEquals(
            listOf("｢测试活动｣ 剩余天数: 3", "TA-1: 源岩 (库存 42)"),
            manager(stages).getStageTips(DayOfWeek.MONDAY, inventory = mapOf("30011" to 42)),
        )
    }

    @Test
    fun dropLine_fallsBackToItemIdWhenUnknown() {
        val tips = manager(
            mapOf("TA-2" to MergedStageInfo(code = "TA-2", displayName = "TA-2", activity = sideStory, drop = "99999"))
        ).getStageTips(DayOfWeek.MONDAY, inventory = mapOf("99999" to 0))
        assertEquals(listOf("｢测试活动｣ 剩余天数: 3", "TA-2: 99999 (库存 0)"), tips)
    }

    @Test
    fun affiliatedMiniGame_isListedUnderItsActivity_onlyWhenOpen() {
        val open = MiniGame(
            display = UiText.Dynamic("测试小游戏"), value = "MiniGame@Test",
            utcStartTime = 0L, utcExpireTime = Long.MAX_VALUE, activity = "测试活动",
            category = UiText.Empty,
        )
        val closed = open.copy(display = UiText.Dynamic("已结束"), utcStartTime = 1L, utcExpireTime = 2L)
        val other = open.copy(display = UiText.Dynamic("别的活动"), activity = "别的活动")
        val tips = manager(
            mapOf("TA-1" to MergedStageInfo(code = "TA-1", displayName = "TA-1", activity = sideStory)),
            miniGames = listOf(open, closed, other),
        ).getStageTips(DayOfWeek.MONDAY)
        assertEquals(listOf("｢测试活动｣ 剩余天数: 3", "小工具→牛杂→测试小游戏"), tips)
    }

    /** CA-5 / PR-x-1 这类带分组库存的常驻关（openDays 为空 = 每天开放） */
    private fun groupedStage(
        code: String = "CA-5",
        groups: List<List<String>> = listOf(listOf("3301", "3302", "3303")),
    ) = MergedStageInfo(code = code, displayName = code, dropGroups = groups)

    @Test
    fun dropGroupLine_unsyncedShowsDashesForUnknownItems() {
        assertEquals(
            emptyList<String>(),
            manager(mapOf("CA-5" to groupedStage())).getStageTips(DayOfWeek.MONDAY),
        )
        assertEquals(
            listOf(" (库存 -- & -- & 7)"),
            manager(mapOf("CA-5" to groupedStage()))
                .getStageTips(DayOfWeek.MONDAY, inventory = mapOf("3303" to 7)),
        )
    }

    @Test
    fun dropGroupLine_fillsMissingItemsWithZeroAfterSync() {
        val tips = manager(mapOf("CA-5" to groupedStage()))
            .getStageTips(DayOfWeek.MONDAY, inventory = mapOf("3303" to 7), hasSyncedInventory = true)
        assertEquals(listOf(" (库存 0 & 0 & 7)"), tips)
    }

    @Test
    fun dropGroupLine_staysVisibleWhenEveryItemIsZeroAfterSync() {
        val tips = manager(mapOf("CA-5" to groupedStage()))
            .getStageTips(DayOfWeek.MONDAY, hasSyncedInventory = true)
        assertEquals(listOf(" (库存 0 & 0 & 0)"), tips)
    }

    @Test
    fun dropGroupLine_joinsItemsWithAmpersandAndGroupsWithSlash() {
        val tips = manager(
            mapOf("PR-A-1" to groupedStage("PR-A-1", listOf(listOf("3261", "3231"), listOf("3262", "3232"))))
        ).getStageTips(DayOfWeek.MONDAY, inventory = mapOf("3261" to 3), hasSyncedInventory = true)
        assertEquals(listOf(" (库存 3 & 0 / 0 & 0)"), tips)
    }
}
