package com.aliothmoon.maameow.remote

import android.app.ApplicationExitInfo
import android.os.Build
import android.os.Parcelable
import androidx.annotation.RequiresApi
import kotlinx.parcelize.Parcelize

/** 系统记下的一次进程退出；App 查自己、提权服务查游戏，两边共用 */
@Parcelize
data class ProcessExitRecord(
    val timestamp: Long,
    val pid: Int,
    val processName: String,
    val reason: Int,
    val reasonName: String,
    val detail: Int,
    val detailName: String,
    val status: Int,
    val importance: Int,
    val pssKb: Long,
    val rssKb: Long,
    val description: String?,
) : Parcelable {

    internal data class Names(val reasonName: String, val detail: Int, val detailName: String)

    companion object {
        private val REASON = Regex("""\breason=-?\d+ \(([^)]*)\)""")
        private val DETAIL = Regex("""\bsubreason=(-?\d+) \(([^)]*)\)""")

        @RequiresApi(Build.VERSION_CODES.R)
        fun from(info: ApplicationExitInfo): ProcessExitRecord {
            val names = parseNames(info.toString())
            return ProcessExitRecord(
                timestamp = info.timestamp,
                pid = info.pid,
                processName = info.processName.orEmpty(),
                reason = info.reason,
                reasonName = names.reasonName,
                detail = names.detail,
                detailName = names.detailName,
                status = info.status,
                importance = info.importance,
                pssKb = info.pss,
                rssKb = info.rss,
                description = info.description,
            )
        }

        // detail 即系统的 subreason，getter 是隐藏 API；部分版本 toString 里带，提权进程另行反射补全
        internal fun parseNames(raw: String): Names {
            val detail = DETAIL.find(raw)
            return Names(
                reasonName = REASON.find(raw)?.groupValues?.get(1).orEmpty(),
                detail = detail?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                detailName = detail?.groupValues?.get(2).orEmpty(),
            )
        }
    }
}
