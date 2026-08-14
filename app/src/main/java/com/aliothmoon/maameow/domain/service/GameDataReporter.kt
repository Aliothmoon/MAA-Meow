package com.aliothmoon.maameow.domain.service

/** Core `ReportRequest` 在 App 进程落地 HTTP */
data class CoreReportRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
    val subtask: String,
)

interface GameDataReporter {
    fun submit(request: CoreReportRequest)
}
