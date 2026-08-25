package com.aliothmoon.maameow.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** @param animate false = 瞬时切页，引导黑场里剪辑用 */
data class MainTabRequest(val tab: BottomNavTab, val animate: Boolean = true)

/**
 * 主 Tab（HorizontalPager）导航的共享请求
 *
 * 主 Tab 路由在 NavHost 里只是空占位，navigate 过去不会切 pager，
 * 子页面只能发请求让 MainScreen 代切
 */
class MainTabNavigator {

    private val _request = MutableStateFlow<MainTabRequest?>(null)

    /** [consume] 置空后，同一 Tab 的下次请求仍会重新发射 */
    val request: StateFlow<MainTabRequest?> = _request.asStateFlow()

    fun navigateTo(tab: BottomNavTab, animate: Boolean = true) {
        _request.value = MainTabRequest(tab, animate)
    }

    fun consume() {
        _request.value = null
    }
}
