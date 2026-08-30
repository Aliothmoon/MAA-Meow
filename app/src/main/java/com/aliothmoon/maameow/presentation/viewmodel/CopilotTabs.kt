package com.aliothmoon.maameow.presentation.viewmodel

/** 自动战斗页签下标与能力判定，VM 与面板共用 */
object CopilotTabs {
    const val MAIN = 0
    const val SSS = 1
    const val PARADOX = 2
    const val OTHER_ACTIVITY = 3

    /** 同 WPF：保全不支持战斗列表 */
    fun supportsBattleList(tabIndex: Int): Boolean =
        tabIndex == MAIN || tabIndex == PARADOX

    fun supportsRegularCopilotOptions(tabIndex: Int): Boolean =
        tabIndex == MAIN || tabIndex == OTHER_ACTIVITY

    fun supportsLoopCount(tabIndex: Int): Boolean =
        tabIndex == SSS || tabIndex == OTHER_ACTIVITY
}
