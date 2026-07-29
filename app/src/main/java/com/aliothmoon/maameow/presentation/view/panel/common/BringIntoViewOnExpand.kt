package com.aliothmoon.maameow.presentation.view.panel.common

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** 折叠展开后滚入可视区；动画中段再请求一次以免只露出标题 */
@Composable
fun Modifier.bringIntoViewOnExpand(expanded: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        requester.bringIntoView()
        delay(280)
        requester.bringIntoView()
    }
    return this.bringIntoViewRequester(requester)
}
