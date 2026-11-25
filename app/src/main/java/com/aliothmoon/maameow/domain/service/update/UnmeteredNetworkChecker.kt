package com.aliothmoon.maameow.domain.service.update

/** 判断当前活跃网络是否为非计费网络（如常规 Wi-Fi） */
fun interface UnmeteredNetworkChecker {
    fun isUnmetered(): Boolean
}
