package com.aliothmoon.maameow.data.model.update

/**
 * 更新检查结果（纯数据，不含过程状态）
 */
sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class UpToDate(val version: String) : UpdateCheckResult()
    data class Error(val error: UpdateError) : UpdateCheckResult()
}
