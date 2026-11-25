package com.aliothmoon.maameow.data.datasource.update

import android.content.Context
import android.net.ConnectivityManager
import com.aliothmoon.maameow.domain.service.update.UnmeteredNetworkChecker
import timber.log.Timber

/** 基于系统计费状态判定当前是否处于非计费网络 */
class AndroidUnmeteredNetworkChecker(
    private val context: Context,
) : UnmeteredNetworkChecker {

    override fun isUnmetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return runCatching { !cm.isActiveNetworkMetered }
            .onFailure { Timber.w(it, "读取网络计费状态失败，按计费网络处理") }
            .getOrDefault(false)
    }
}
