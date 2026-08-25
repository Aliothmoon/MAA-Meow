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
        )
        val closed = open.copy(display = UiText.Dynamic("已结束"), utcStartTime = 1L, utcExpireTime = 2L)
        val other = open.copy(display = UiText.Dynamic("别的活动"), activity = "别的活动")
        val tips = manager(
            mapOf("TA-1" to MergedStageInfo(code = "TA-1", displayName = "TA-1", activity = sideStory)),
            miniGames = listOf(open, closed, other),
        ).getStageTips(DayOfWeek.MONDAY)
        assertEquals(listOf("｢测试活动｣ 剩余天数: 3", "小工具→牛杂→测试小游戏"), tips)
    }
}
