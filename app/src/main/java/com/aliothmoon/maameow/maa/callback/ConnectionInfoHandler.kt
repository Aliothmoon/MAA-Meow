package com.aliothmoon.maameow.maa.callback

import android.content.Context
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import timber.log.Timber

/**
 * 处理 ConnectionInfo (msg=2) 的所有 what 值。
 *
 * 参考 ds/02-ConnectionInfo连接信息处理.md 和
 * ds2/回调日志JSON字段映射迁移表.md 第一节。
 */
class ConnectionInfoHandler(
    applicationContext: Context,
    private val sessionLogger: MaaSessionLogger,
) {
    private val resources = applicationContext.resources
    private val packageName = applicationContext.packageName

    fun handle(details: JSONObject) {
        val what = details.getString("what") ?: return
        val innerDetails = details.getJSONObject("details")

        when (what) {
            "Connected" -> {
                Timber.i("MaaCore Connected: $innerDetails")
            }

            "UnsupportedResolution" -> {
                val width = innerDetails?.getIntValue("width") ?: 0
                val height = innerDetails?.getIntValue("height") ?: 0
                val msg = if (width > 0 && height > 0) {
                    "${str("ResolutionNotSupported")} (${str("ResolutionNotSupportedCurrentResolution", width, height)})"
                } else {
                    str("ResolutionNotSupported")
                }
                sessionLogger.append(msg, LogLevel.ERROR)
            }

            "ResolutionError" -> {
                sessionLogger.append(
                    str("ResolutionAcquisitionFailure"),
                    LogLevel.ERROR
                )
            }

            "Reconnecting" -> {
                val times = (innerDetails?.getIntValue("times") ?: 0) + 1
                sessionLogger.append(
                    "${str("TryToReconnect")} ($times)",
                    LogLevel.ERROR
                )
            }

            "Reconnected" -> {
                sessionLogger.append(
                    str("ReconnectSuccess"),
                    LogLevel.SUCCESS
                )
            }

            "Disconnect" -> {
                sessionLogger.append(
                    str("ReconnectFailed"),
                    LogLevel.ERROR
                )
            }

            "ScreencapFailed" -> {
                sessionLogger.append(
                    str("ScreencapFailed"),
                    LogLevel.ERROR
                )
            }

            "TouchModeNotAvailable" -> {
                sessionLogger.append(
                    str("TouchModeNotAvailable"),
                    LogLevel.ERROR
                )
            }

            "FastestWayToScreencap" -> handleFastestScreencap(innerDetails)

            "ScreencapCost" -> handleScreencapCost(innerDetails)

            // ResolutionGot / ConnectFailed / UuidGot — 仅 Timber，不输出用户日志
            else -> Timber.d("ConnectionInfo unhandled what=$what")
        }
    }

    /**
     * 最快截图方式选择结果。
     *
     * cost > 800ms → Warning (ErrorTip)
     * cost > 400ms → Warning (WarningTip)
     * 其他 → Trace (正常)
     */
    private fun handleFastestScreencap(details: JSONObject?) {
        val cost = details?.getString("cost") ?: "???"
        val method = details?.getString("method") ?: "???"
        val costInt = cost.toIntOrNull()

        when {
            costInt != null && costInt > 800 -> {
                sessionLogger.append(
                    str("FastestWayToScreencapErrorTip", cost),
                    LogLevel.WARNING
                )
            }

            costInt != null && costInt > 400 -> {
                sessionLogger.append(
                    str("FastestWayToScreencapWarningTip", cost),
                    LogLevel.WARNING
                )
            }

            else -> {
                sessionLogger.append(
                    str("FastestWayToScreencap", cost, method),
                    LogLevel.TRACE
                )
            }
        }
    }

    /**
     * 截图耗时统计（每 10 次截图回传一次）。
     *
     * avg >= 800ms → Warning (ErrorTip)
     * avg >= 400ms → Warning (WarningTip)
     * 其他 → Trace
     */
    private fun handleScreencapCost(details: JSONObject?) {
        val min = details?.getString("min") ?: "?"
        val avg = details?.getString("avg") ?: "?"
        val max = details?.getString("max") ?: "?"
        val avgInt = avg.toIntOrNull()

        when {
            avgInt != null && avgInt >= 800 -> {
                sessionLogger.append(
                    str("FastestWayToScreencapErrorTip", avg),
                    LogLevel.WARNING
                )
            }

            avgInt != null && avgInt >= 400 -> {
                sessionLogger.append(
                    str("FastestWayToScreencapWarningTip", avg),
                    LogLevel.WARNING
                )
            }

            else -> {
                sessionLogger.append(
                    str("ScreencapCost", min, avg, max, ""),
                    LogLevel.TRACE
                )
            }
        }
    }

    /** 便捷方法：通过 MaaStringRes 动态查找 i18n 字符串 */
    private fun str(key: String): String =
        MaaStringRes.getString(resources, packageName, key)

    private fun str(key: String, vararg args: Any): String =
        MaaStringRes.getString(resources, packageName, key, *args)
}
