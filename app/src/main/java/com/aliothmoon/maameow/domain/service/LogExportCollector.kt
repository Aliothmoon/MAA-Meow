package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.LogConfig
import java.io.File

/** 日志导出筛选：不删源、不写盘。 */
object LogExportCollector {

    const val EXPORT_DIR_NAME = "export"

    private const val MS_PER_DAY = 24L * 60 * 60 * 1000

    private val ROLLING_DIR_MARKERS = listOf(
        "/gui/",
        "/schedule/",
        "/error_logs/",
        "/crash_logs/",
    )

    fun collect(debugDir: File): List<File> {
        if (!debugDir.isDirectory) return emptyList()
        val exportPrefix = File(debugDir, EXPORT_DIR_NAME).invariantSeparatorsPath
        return select(
            debugDir.walkTopDown()
                .filter { file ->
                    file.isFile && !file.invariantSeparatorsPath.startsWith(exportPrefix)
                }
                .toList(),
        )
    }

    fun select(files: Iterable<File>): List<File> {
        val cutoff = System.currentTimeMillis() -
            LogConfig.EXPORT_ROLLING_LOG_DAYS * MS_PER_DAY
        return files.filter { shouldExport(it, cutoff) }
    }

    private fun shouldExport(file: File, rollingCutoff: Long): Boolean {
        if (!file.isFile) return false
        if (!isUnderRollingDir(file.invariantSeparatorsPath)) return true
        return file.lastModified() >= rollingCutoff
    }

    private fun isUnderRollingDir(invariantPath: String): Boolean =
        ROLLING_DIR_MARKERS.any { invariantPath.contains(it) }
}
