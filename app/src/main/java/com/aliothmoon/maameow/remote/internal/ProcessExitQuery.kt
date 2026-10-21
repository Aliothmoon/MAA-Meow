package com.aliothmoon.maameow.remote.internal

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.aliothmoon.maameow.remote.ProcessExitRecord
import com.aliothmoon.maameow.third.FakeContext
import com.aliothmoon.maameow.third.Ln

/** 查任意包的进程退出记录：跨包要 DUMP 权限，shell 自带 */
object ProcessExitQuery {
    private const val TAG = "ProcessExitQuery"

    fun query(packageName: String?, sinceMs: Long, maxNum: Int): List<ProcessExitRecord> {
        if (packageName.isNullOrBlank() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return runCatching {
            FakeContext.get().getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(packageName, 0, maxNum)
                .filter { it.timestamp >= sinceMs }
                .map { ProcessExitRecord.from(it).withHiddenDetail(it) }
        }.onFailure {
            Ln.w("$TAG: query $packageName failed: ${it.javaClass.simpleName}: ${it.message}")
        }.getOrDefault(emptyList())
    }

    // subreason 的 getter 与可读名都是隐藏 API：App 进程受 targetSdk 限制拿不到，提权进程不受限
    @SuppressLint("BlockedPrivateApi", "DiscouragedPrivateApi")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun ProcessExitRecord.withHiddenDetail(info: ApplicationExitInfo): ProcessExitRecord = runCatching {
        val type = ApplicationExitInfo::class.java
        val detail = type.getDeclaredMethod("getSubReason").invoke(info) as Int
        val name = runCatching {
            type.getDeclaredMethod("subreasonToString", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(null, detail) as String
        }.getOrNull() ?: detailName
        copy(detail = detail, detailName = name)
    }.getOrDefault(this)
}
