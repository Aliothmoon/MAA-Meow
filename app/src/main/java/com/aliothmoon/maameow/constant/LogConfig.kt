package com.aliothmoon.maameow.constant


object LogConfig {
    /** 内存中最大运行时日志条数 */
    const val MAX_RUNTIME_LOG_COUNT = 750

    /** 任务日志保留天数 */
    const val MAX_TASK_LOG_DAYS = 30

    /** 错误日志单文件最大大小 */
    const val MAX_ERROR_LOG_SIZE = 2L * 1024 * 1024 // 2MB

    /** 错误日志最大文件数 */
    const val MAX_ERROR_LOG_FILES = 5

    /** 崩溃日志最大文件数 */
    const val MAX_CRASH_LOG_FILES = 10

    /** 日志批量刷新间隔（毫秒） */
    const val LOG_FLUSH_INTERVAL_MS = 75L

    /** 导出前清理：logcat / screenshots / crash_logs 保留天数 */
    const val EXPORT_CLEANUP_DAYS = 7

    /** 导出时 screenshots 目录只打包近 N 天的文件 */
    const val EXPORT_SCREENSHOT_DAYS = 3

    /** 导出 ZIP 单文件大小上限（logcat 除外，见下） */
    const val MAX_EXPORT_SINGLE_FILE_SIZE = 5L * 1024 * 1024 // 5 MB

    /** logcat 单独放宽（无轮转、是核心证据） */
    const val MAX_EXPORT_LOGCAT_FILE_SIZE = 20L * 1024 * 1024 // 20 MB

    /** 导出时日志类子目录最多打包文件数（gui / logcat / schedule / error_logs / crash_logs） */
    const val MAX_EXPORT_FILES_PER_LOG_DIR = 50

    /** 导出时 screenshots 子目录最多打包文件数 */
    const val MAX_EXPORT_FILES_PER_SCREENSHOT_DIR = 30

    /** 导出时其他子目录最多打包文件数 */
    const val MAX_EXPORT_FILES_PER_OTHER_DIR = 20

    /** 导出 ZIP 总大小软上限 */
    const val MAX_EXPORT_TOTAL_SIZE = 50L * 1024 * 1024 // 50 MB
}
