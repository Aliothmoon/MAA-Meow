package com.aliothmoon.maameow.data.resource

import android.content.Context
import com.aliothmoon.maameow.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/** 常驻关卡显示名与提示走字符串资源，英文界面不再露中文（issue #281） */
class StageInfoTest {

    private val context = mockk<Context> {
        every { getString(any<Int>()) } answers { "res:${firstArg<Int>()}" }
    }

    @Test
    fun mappedCodes_resolveThroughResources() {
        assertEquals(
            "res:${R.string.panel_fight_stage_name_ce_6}",
            StageInfo(stageId = "CE-6", code = "CE-6").displayName(context),
        )
        assertEquals(
            "res:${R.string.panel_fight_stage_name_pr_a_2}",
            StageInfo(stageId = "PR-A-2", code = "PR-A-2").displayName(context),
        )
        assertEquals(
            "res:${R.string.panel_fight_annihilation_current}",
            StageInfo(stageId = "Annihilation", code = "Annihilation").displayName(context),
        )
    }

    @Test
    fun unmappedCodes_fallBackToCode() {
        assertEquals("1-7", StageInfo(stageId = "1-7", code = "1-7").displayName(context))
        assertEquals("12-17-HARD", StageInfo(stageId = "12-17-HARD", code = "12-17-HARD").displayName(context))
    }

    @Test
    fun everyAnnihilationOption_hasDisplayName() {
        StageInfo.ANNIHILATION_CODES.forEach { code ->
            assertEquals(code, true, StageInfo.displayNameRes(code) != null)
        }
    }

    /** 资源本与芯片本带提示，PR-X-2 不单独出，同上游 */
    @Test
    fun permanentStages_withTips_matchUpstream() {
        assertEquals(
            listOf("CE-6", "AP-5", "CA-5", "LS-6", "SK-5", "PR-A-1", "PR-B-1", "PR-C-1", "PR-D-1"),
            PermanentStages.STAGES.filter { it.tipRes != null }.map { it.code },
        )
    }
}
