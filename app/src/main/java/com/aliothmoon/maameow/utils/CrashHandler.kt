package com.aliothmoon.maameow.utils

import android.content.Context
import android.os.Build
import android.os.Process
import com.aliothmoon.maameow.constant.LogConfig
import com.aliothmoon.maameow.data.config.MaaPathConfig
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

class CrashHandler(private val pathConfig: MaaPathConfig) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val CRASH_LOG_DIR = "crash_logs"
    }

    private var context: Context? = null


    fun init(context: Context) {
        Timber.i("CrashHandler init")
        this.context = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler(this)
        cleanOldCrashLogs()
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Timber.e(throwable, "Uncaught exception occurred")
            saveCrashLog(collectCrashInfo(throwable))
        } catch (_: Exception) {
        } finally {
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }


    private fun collectCrashInfo(throwable: Throwable): String {
        val sb = StringBuilder()
        val now = ZonedDateTime.now()

        sb.appendLine("========== Crash Log ==========")
        sb.appendLine("Time: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (Z)"))}")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("App Version: ${getAppVersion()}")
        sb.appendLine()
        sb.appendLine("========== Stack Trace ==========")

        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        throwable.printStackTrace(printWriter)
        sb.append(writer.toString())

        printWriter.close()
        writer.close()

        return sb.toString()
    }

    private fun saveCrashLog(crashInfo: String) {
        try {
            val crashDir = File(pathConfig.debugDir, CRASH_LOG_DIR)
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            val fileName = "crash_${
                ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            }.txt"
            val crashFile = File(crashDir, fileName)

            crashFile.writeText(crashInfo)
        } catch (_: Exception) {
        }
    }


    private fun cleanOldCrashLogs() {
        try {
            val crashDir = File(pathConfig.debugDir, CRASH_LOG_DIR)
            if (crashDir.exists() && crashDir.isDirectory) {
                val files = crashDir.listFiles()?.sortedByDescending { it.lastModified() }
                files?.let {
                    if (it.size > LogConfig.MAX_LOG_FILES) {
                        for (i in LogConfig.MAX_LOG_FILES until it.size) {
                            it[i].delete()
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }


    private fun getAppVersion(): String {
        return try {
            context?.let { ctx ->
                val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                "${packageInfo.versionName} (${packageInfo.longVersionCode})"
            } ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }
}