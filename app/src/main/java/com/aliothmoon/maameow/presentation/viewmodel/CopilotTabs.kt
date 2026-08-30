package com.aliothmoon.maameow.presentation.viewmodel

/** 自动战斗页签下标与各页签能力，VM 与 AutoBattlePanel 共用同一份判定 */
object CopilotTabs {
    const val MAIN = 0
    const val SSS = 1
    const val PARADOX = 2
    const val OTHER_ACTIVITY = 3

    /** 与 WPF 一致：只有主线/故事集与悖论模拟支持战斗列表 */
    fun supportsBattleList(tabIndex: Int): Boolean =
        tabIndex == MAIN || tabIndex == PARADOX

    fun supportsRegularCopilotOptions(tabIndex: Int): Boolean =
        tabIndex == MAIN || tabIndex == OTHER_ACTIVITY

    fun supportsLoopCount(tabIndex: Int): Boolean =
        tabIndex == SSS || tabIndex == OTHER_ACTIVITY
}
