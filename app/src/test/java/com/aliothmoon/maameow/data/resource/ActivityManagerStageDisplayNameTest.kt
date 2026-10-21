package com.aliothmoon.maameow.data.resource

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.activity.ActivityStage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** 关卡代码 → 显示名，剿灭等底层代码不得原样露出 */
class ActivityManagerStageDisplayNameTest {

    private val context = mockk<Context> {
        every { getString(any<Int>()) } answers { "res:${firstArg<Int>()}" }
    }

    private val manager = ActivityManager(
        context = context,
        chainState = mockk(relaxed = true),
        itemHelper = mockk(relaxed = true),
    ).also { manager ->
        val field = ActivityManager::class.java.getDeclaredField("_activityStages")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(manager) as MutableStateFlow<List<ActivityStage>>).value = listOf(
            ActivityStage(display = "YW-8", value = "YW-8", activityKey = "act"),
        )
    }

    @Test
    fun blank_isCurrentOrLast() {
        assertEquals("res:${R.string.panel_fight_stage_reset_current}", manager.getStageDisplayName(""))
    }

    @Test
    fun annihilationCodes_useLocalizedNames() {
        assertEquals(
            "res:${R.string.panel_fight_annihilation_current}",
            manager.getStageDisplayName("Annihilation"),
        )
        assertEquals(
            "res:${R.string.panel_fight_annihilation_outskirts}",
            manager.getStageDisplayName("LungmenOutskirts@Annihilation"),
        )
    }

    @Test
    fun permanentAndActivityStages_resolve_unknownFallsBackToCode() {
        assertEquals("res:${R.string.panel_fight_stage_name_ce_6}", manager.getStageDisplayName("CE-6"))
        assertEquals("YW-8", manager.getStageDisplayName("YW-8"))
        assertEquals("16-14", manager.getStageDisplayName("16-14"))
    }
}
