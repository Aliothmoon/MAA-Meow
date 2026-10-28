package com.aliothmoon.maameow.schedule.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.aliothmoon.maameow.schedule.service.AutoStartTarget

@Composable
internal fun rememberAutoStartReminder(
    active: Boolean,
    enabled: Boolean,
    load: suspend () -> AutoStartTarget?,
    onShown: suspend () -> Unit,
): MutableState<AutoStartTarget?> {
    val target = remember { mutableStateOf<AutoStartTarget?>(null) }
    var wasShown by remember { mutableStateOf(false) }
    val currentLoad by rememberUpdatedState(load)
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(active, enabled) {
        if (active && enabled && target.value == null) target.value = currentLoad()
    }
    LaunchedEffect(active, enabled, target.value) {
        if (active && enabled && target.value != null && !wasShown) {
            // 等可见帧后再记录，避免离屏预加载消耗提醒
            withFrameNanos { }
            currentOnShown()
            wasShown = true
        }
    }
    return target
}
