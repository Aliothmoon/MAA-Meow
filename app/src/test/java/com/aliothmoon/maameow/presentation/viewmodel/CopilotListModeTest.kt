package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.data.model.CopilotConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 战斗列表模式 = 用户偏好 && 页签支持。
 * 偏好不再被页签切换改写，切到不支持的页签只是暂时不生效
 */
class CopilotListModeTest {

    private fun state(useList: Boolean, tab: Int) = CopilotUiState(
        tabIndex = tab,
        config = CopilotConfig(useCopilotList = useList),
    )

    @Test
    fun onlyMainAndParadoxSupportBattleList() {
        assertTrue(CopilotTabs.supportsBattleList(CopilotTabs.MAIN))
        assertTrue(CopilotTabs.supportsBattleList(CopilotTabs.PARADOX))
        assertFalse("与 WPF 一致，保全不走战斗列表", CopilotTabs.supportsBattleList(CopilotTabs.SSS))
        assertFalse(CopilotTabs.supportsBattleList(CopilotTabs.OTHER_ACTIVITY))
    }

    @Test
    fun loopCountAndRegularOptionsFollowWpf() {
        assertTrue(CopilotTabs.supportsLoopCount(CopilotTabs.SSS))
        assertTrue(CopilotTabs.supportsLoopCount(CopilotTabs.OTHER_ACTIVITY))
        assertFalse(CopilotTabs.supportsLoopCount(CopilotTabs.MAIN))
        assertFalse(CopilotTabs.supportsLoopCount(CopilotTabs.PARADOX))

        assertTrue(CopilotTabs.supportsRegularCopilotOptions(CopilotTabs.MAIN))
        assertTrue(CopilotTabs.supportsRegularCopilotOptions(CopilotTabs.OTHER_ACTIVITY))
        assertFalse(CopilotTabs.supportsRegularCopilotOptions(CopilotTabs.SSS))
        assertFalse(CopilotTabs.supportsRegularCopilotOptions(CopilotTabs.PARADOX))
    }

    @Test
    fun listModeActive_requiresPreferenceAndSupportedTab() {
        assertTrue(state(useList = true, tab = CopilotTabs.MAIN).listModeActive)
        assertTrue(state(useList = true, tab = CopilotTabs.PARADOX).listModeActive)
        assertFalse(state(useList = false, tab = CopilotTabs.MAIN).listModeActive)
        assertFalse(state(useList = true, tab = CopilotTabs.SSS).listModeActive)
        assertFalse(state(useList = true, tab = CopilotTabs.OTHER_ACTIVITY).listModeActive)
    }

    @Test
    fun preferenceSurvivesUnsupportedTab() {
        // 去「其他活动」瞄一眼再切回来，列表模式应原样恢复
        val onMain = state(useList = true, tab = CopilotTabs.MAIN)
        val onOther = onMain.copy(tabIndex = CopilotTabs.OTHER_ACTIVITY)
        val backToMain = onOther.copy(tabIndex = CopilotTabs.MAIN)

        assertFalse(onOther.listModeActive)
        assertTrue(onOther.useCopilotList)
        assertTrue(backToMain.listModeActive)
    }
}
