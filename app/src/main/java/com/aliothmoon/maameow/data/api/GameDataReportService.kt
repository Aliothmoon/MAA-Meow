package com.aliothmoon.maameow.data.api

import android.content.Context
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.CoreReportRequest
import com.aliothmoon.maameow.domain.service.GameDataReporter
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

class GameDataReportService(
    private val appContext: Context,
    private val httpClient: OkHttpClient,
    private val appSettings: AppSettingsManager,
    private val taskChainState: TaskChainState,
    private val sessionLogger: MaaSessionLogger,
    private val delayMs: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : GameDataReporter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun submit(request: CoreReportRequest) {
        scope.launch {
            runCatching { post(request) }
                .onFailure { Timber.e(it, "game data report failed: %s", request.url) }
        }
    }

    internal suspend fun post(request: CoreReportRequest): Boolean {
        if (request.url.isBlank() || request.body.isBlank()) {
            Timber.e("ReportRequest missing url/body")
            return false
        }
        if (request.subtask == SUBTASK_PENGUIN && taskChainState.clientType == "txwy") {
            Timber.i("PenguinStats report skipped for txwy")
            return true
        }

        val first = tryPost(request.url, request.headers, request.body)
        if (first.ok) {
            maybeSavePenguinId(first.penguinId)
            return true
        }
        Timber.w("Initial report failed, status=%s url=%s", first.code, request.url)

        if (request.subtask != SUBTASK_PENGUIN || !request.url.contains(PENGUIN_IO)) {
            return true
        }

        for (backup in PENGUIN_BACKUPS) {
            val backupUrl = request.url.replace(PENGUIN_IO, backup)
            Timber.i("Trying penguin backup %s", backupUrl)
            val resp = tryPost(backupUrl, request.headers, request.body)
            if (resp.ok) {
                maybeSavePenguinId(resp.penguinId)
                return true
            }
        }

        sessionLogger.append(
            appContext.getString(R.string.report_http_failed),
            LogLevel.WARNING,
        )
        return false
    }

    private suspend fun tryPost(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): PostResult {
        var backoff = INITIAL_BACKOFF_MS
        repeat(MAX_RETRY_PER_DOMAIN) {
            val response = runCatching {
                httpClient.newCall(buildRequest(url, headers, body)).execute()
            }.onFailure { Timber.e(it, "POST %s", url) }.getOrNull()
            if (response == null) return PostResult()
            val result = response.use {
                PostResult(
                    code = it.code,
                    penguinId = it.header(PENGUIN_ID_HEADER).orEmpty(),
                )
            }
            when {
                result.ok -> return result
                result.code in 500..599 -> {
                    delayMs(backoff)
                    backoff = (backoff * 3) / 2
                }
                else -> return result
            }
        }
        return PostResult()
    }

    private fun buildRequest(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .header("User-Agent", "MaaMeow/${BuildConfig.VERSION_NAME}")
        headers.forEach { (key, value) -> builder.addHeader(key, value) }
        return builder.build()
    }

    private suspend fun maybeSavePenguinId(assigned: String) {
        if (assigned.isBlank()) return
        if (appSettings.penguinId.value.isBlank()) {
            appSettings.setPenguinId(assigned)
        }
        Timber.i("PenguinId from server: %s", assigned)
    }

    private data class PostResult(
        val code: Int? = null,
        val penguinId: String = "",
    ) {
        val ok: Boolean get() = code == 200
    }

    companion object {
        const val SUBTASK_PENGUIN = "ReportToPenguinStats"
        const val SUBTASK_YITULIU = "ReportToYituliu"
        const val PENGUIN_IO = "https://penguin-stats.io"
        val PENGUIN_BACKUPS = listOf("https://penguin-stats.cn")
        const val PENGUIN_ID_HEADER = "x-penguin-set-penguinid"
        private const val MAX_RETRY_PER_DOMAIN = 3
        private const val INITIAL_BACKOFF_MS = 3000L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
