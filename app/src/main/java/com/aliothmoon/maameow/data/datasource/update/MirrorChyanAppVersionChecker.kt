package com.aliothmoon.maameow.data.datasource.update

import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.MirrorChyanApiClient
import com.aliothmoon.maameow.data.api.MirrorChyanBizException
import com.aliothmoon.maameow.data.datasource.AppDownloader
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.model.update.UpdateError
import com.aliothmoon.maameow.data.model.update.UpdateInfo
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.service.update.checker.AppVersionChecker

class MirrorChyanAppVersionChecker(
    private val apiClient: MirrorChyanApiClient,
    private val appSettings: AppSettingsManager
) : AppVersionChecker {

    override suspend fun check(
        current: String,
        channel: UpdateChannel,
    ): UpdateCheckResult {
        val cdk = appSettings.mirrorChyanCdk.value
        val query = mapOf(
            "current_version" to current,
            "user_agent" to "MAA-Meow",
            "os" to "android",
            "channel" to channel.value,
        ).let {
            if (cdk.length == 24) it + mapOf("cdk" to cdk) else it
        }
        val result = apiClient.getLatest(
            MaaApi.MIRROR_CHYAN_APP_RESOURCE,
            query = query,
            fetchVersion = true
        )

        return result.fold(
            onSuccess = { data ->
                val remoteVersion = data.versionName
                if (remoteVersion.isEmpty() || AppDownloader.compareVersions(
                        current,
                        remoteVersion
                    ) >= 0
                ) {
                    UpdateCheckResult.UpToDate(current)
                } else {
                    UpdateCheckResult.Available(
                        UpdateInfo(
                            version = remoteVersion,
                            releaseNote = data.releaseNote
                        )
                    )
                }
            },
            onFailure = { e ->
                when (e) {
                    is MirrorChyanBizException -> UpdateCheckResult.Error(e.toUpdateError())
                    else -> UpdateCheckResult.Error(
                        UpdateError.NetworkError(
                            e.message ?: "网络错误"
                        )
                    )
                }
            }
        )
    }
}
