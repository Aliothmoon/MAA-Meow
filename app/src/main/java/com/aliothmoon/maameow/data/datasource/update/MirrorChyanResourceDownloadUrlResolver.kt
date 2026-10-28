package com.aliothmoon.maameow.data.datasource.update

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.CdkRequiredException
import com.aliothmoon.maameow.data.api.MirrorChyanApiClient
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.service.update.resolver.ResourceDownloadUrlResolver
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import com.aliothmoon.maameow.utils.i18n.uiTextOf

class MirrorChyanResourceDownloadUrlResolver(
    private val apiClient: MirrorChyanApiClient,
    private val appSettingsManager: AppSettingsManager
) : ResourceDownloadUrlResolver {

    override suspend fun resolve(currentVersion: String): Result<String> {
        val cdk = appSettingsManager.mirrorChyanCdk.value
        if (cdk.isBlank()) {
            return Result.failure(CdkRequiredException())
        }

        return apiClient.getLatest(
            MaaApi.MIRROR_CHYAN_RESOURCE,
            query = mapOf(
                "current_version" to currentVersion,
                "user_agent" to "MAA-Meow",
                "cdk" to cdk
            )
        ).mapCatching { data ->
            data.url ?: throw LocalizedException(uiTextOf(R.string.update_error_empty_download_url))
        }
    }
}
