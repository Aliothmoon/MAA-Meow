package com.aliothmoon.maameow.domain.service

/** 前台模式 core 开跑前等画面上的浮层撤干净：core 一 Start 就截首帧，浮层还在会被当成游戏画面 */
fun interface ForegroundScreenGate {
    suspend fun awaitClear()
}
