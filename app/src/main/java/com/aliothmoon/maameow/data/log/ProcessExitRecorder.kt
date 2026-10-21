package com.aliothmoon.maameow.data.log

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.domain.service.ProcessExitJournal
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.remote.ProcessExitRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 系统记下的进程退出写进应用日志，按包名记水位只写一次
 * App 自身查自己不要权限；游戏要 DUMP，经提权服务查
 */
class ProcessExitRecorder(context: Context) : ProcessExitJournal {

    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val mutex = Mutex()

    /** App 启动时调，不依赖提权服务，服务起不来也记得下 */
    suspend fun recordSelf() = guarded(appContext.packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@guarded
        val records = withContext(Dispatchers.IO) {
            appContext.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(appContext.packageName, 0, ProcessExitLog.MAX_RECORDS)
                .map(ProcessExitRecord::from)
        }
        record(ExitSubject.SELF, appContext.packageName, records)
    }

    override suspend fun recordGames() {
        for ((_, packageName) in Packages) recordGame(packageName)
    }

    override suspend fun recordGame(packageName: String) = guarded(packageName) {
        val since = mutex.withLock { watermark(packageName) }?.timestamp ?: 0L
        val records = fetch(packageName, since) ?: return@guarded
        record(ExitSubject.GAME, packageName, records)
    }

    /** 只是记日志，出什么错都不能挡住调用方：启动、连上服务、游戏死亡后的中止；取消照常上抛 */
    private suspend inline fun guarded(packageName: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "record process exit failed: %s", packageName)
        }
    }

    private suspend fun fetch(packageName: String, sinceMs: Long): List<ProcessExitRecord>? {
        val service = RemoteServiceManager.getInstanceOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { service.getProcessExitRecords(packageName, sinceMs, ProcessExitLog.MAX_RECORDS) }
                .onFailure { Timber.w(it, "query process exit reasons failed: %s", packageName) }
                .getOrNull()
        }
    }

    private suspend fun record(subject: ExitSubject, packageName: String, records: List<ProcessExitRecord>) =
        mutex.withLock {
            val watermark = watermark(packageName)
            val fresh = ProcessExitLog.selectNew(records, watermark)
            for (item in fresh) {
                val line = ProcessExitLog.format(subject, item)
                if (ProcessExitLog.isExpected(subject, item)) Timber.i(line) else Timber.w(line)
            }
            val next = fresh.lastOrNull()?.let { ProcessExitLog.Watermark(it.timestamp, it.pid) }
                ?: ProcessExitLog.Watermark(0L, 0).takeIf { watermark == null }
            if (next != null) saveWatermark(packageName, next)
        }

    private fun watermark(packageName: String): ProcessExitLog.Watermark? {
        if (!prefs.contains(tsKey(packageName))) return null
        return ProcessExitLog.Watermark(prefs.getLong(tsKey(packageName), 0L), prefs.getInt(pidKey(packageName), 0))
    }

    // 同步落盘：刚写完日志就被杀，下次不至于再写一遍
    private fun saveWatermark(packageName: String, watermark: ProcessExitLog.Watermark) {
        prefs.edit(commit = true) {
            putLong(tsKey(packageName), watermark.timestamp)
            putInt(pidKey(packageName), watermark.pid)
        }
    }

    private fun tsKey(packageName: String) = "$packageName.ts"
    private fun pidKey(packageName: String) = "$packageName.pid"

    private companion object {
        const val PREFS = "process_exit_watermark"
    }
}
