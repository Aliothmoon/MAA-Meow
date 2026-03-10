package com.aliothmoon.maameow.data.datasource.update

import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.CdkRequiredException
import com.aliothmoon.maameow.data.api.MirrorChyanApiClient
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.service.update.resolver.AppDownloadUrlResolver

class MirrorChyanAppDownloadUrlResolver(
    private val apiClient: MirrorChyanApiClient,
    private val appSettingsManager: AppSettingsManager
) : AppDownloadUrlResolver {

    override suspend fun resolve(version: String, channel: UpdateChannel): Result<String> {
        val cdk = appSettingsManager.mirrorChyanCdk.value
        if (cdk.isBlank()) {
            return Result.failure(CdkRequiredException())
        }

        return apiClient.getLatest(
            MaaApi.MIRROR_CHYAN_APP_RESOURCE,
            query = mapOf(
                "current_version" to BuildConfig.VERSION_NAME,
                "user_agent" to "MAA-Meow",
                "os" to "android",
                "channel" to channel.value,
                "cdk" to cdk
            )
        ).map { data ->
            data.url ?: throw Exception("下载链接为空")
        }
    }
}
