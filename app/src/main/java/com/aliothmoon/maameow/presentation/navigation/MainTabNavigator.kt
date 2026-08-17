package com.aliothmoon.maameow.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主 Tab（HorizontalPager）导航的共享请求
 *
 * 主 Tab 路由在 NavHost 里只是空占位，navigate 过去不会切 pager，
 * 子页面只能发请求让 MainScreen 代切
 */
class MainTabNavigator {

    private val _request = MutableStateFlow<BottomNavTab?>(null)

    /** [consume] 置空后，同一 Tab 的下次请求仍会重新发射 */
    val request: StateFlow<BottomNavTab?> = _request.asStateFlow()

    fun navigateTo(tab: BottomNavTab) {
        _request.value = tab
    }

    fun consume() {
        _request.value = null
    }
}
