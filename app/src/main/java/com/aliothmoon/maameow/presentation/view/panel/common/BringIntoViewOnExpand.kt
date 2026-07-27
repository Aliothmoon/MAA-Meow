package com.aliothmoon.maameow.presentation.view.panel.common

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * 折叠内容展开后，请求最近的可滚动父级把本组件滚入可视区
 *
 * 用于关卡/材料等「标题行折叠、展开后高度突增」的块：否则展开内容常落在视口外，
 * 用户还要手动往下拖 对 [androidx.compose.foundation.verticalScroll] /
 * Lazy 列表等 NestedScroll 父级均有效
 */
@Composable
fun Modifier.bringIntoViewOnExpand(expanded: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        requester.bringIntoView()
        // expandVertically 动画中高度仍在变，中段再请求一次，避免只露出标题
        delay(280)
        requester.bringIntoView()
    }
    return this.bringIntoViewRequester(requester)
}
