package com.aliothmoon.maameow.koin

import com.aliothmoon.maameow.data.api.CopilotApiService
import com.aliothmoon.maameow.data.api.ETagCacheManager
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.MaaApiService
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.datasource.AppDownloader
import com.aliothmoon.maameow.data.datasource.AssetExtractor
import com.aliothmoon.maameow.data.datasource.ResourceDownloader
import com.aliothmoon.maameow.data.datasource.ZipExtractor
import com.aliothmoon.maameow.data.log.ApplicationLogWriter
import com.aliothmoon.maameow.data.log.TaskLogWriter
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskConfigState
import com.aliothmoon.maameow.data.repository.CopilotRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.domain.service.LogExportService
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.service.RuntimeLogCenter
import com.aliothmoon.maameow.domain.service.UnifiedStateDispatcher
import com.aliothmoon.maameow.domain.service.update.AppUpdateHandler
import com.aliothmoon.maameow.domain.service.update.ResourceUpdateHandler
import com.aliothmoon.maameow.domain.service.update.UpdateService
import com.aliothmoon.maameow.maa.callback.ConnectionInfoHandler
import com.aliothmoon.maameow.maa.callback.MaaCallbackDispatcher
import com.aliothmoon.maameow.maa.callback.MaaExecutionStateHolder
import com.aliothmoon.maameow.maa.callback.SubTaskHandler
import com.aliothmoon.maameow.maa.callback.TaskChainHandler
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.overlay.OverlayViewModelOwner
import com.aliothmoon.maameow.overlay.border.BorderOverlayManager
import com.aliothmoon.maameow.utils.CrashHandler
import com.aliothmoon.maameow.utils.log.LogTreeHolder
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {


    singleOf(::CrashHandler)
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    singleOf(::HttpClientHelper)
    singleOf(::ETagCacheManager)
    singleOf(::MaaApiService)
    singleOf(::PermissionManager)


    singleOf(::AppSettingsManager)
    singleOf(::TaskConfigState)
    singleOf(::MaaPathConfig)
    singleOf(::ResourceDownloader)
    singleOf(::AppDownloader)
    singleOf(::ZipExtractor)
    singleOf(::AssetExtractor)
    singleOf(::ResourceUpdateHandler)
    singleOf(::AppUpdateHandler)
    singleOf(::UpdateService)
    singleOf(::ResourceInitService)
    singleOf(::MaaResourceLoader)
    single { RuntimeLogCenter(get()) }

    // 回调处理链
    singleOf(::ConnectionInfoHandler)
    singleOf(::TaskChainHandler)
    singleOf(::SubTaskHandler)
    singleOf(::MaaCompositionService)
    single<MaaExecutionStateHolder> { get<MaaCompositionService>() }
    singleOf(::MaaCallbackDispatcher)

    singleOf(::UnifiedStateDispatcher)
    singleOf(::LogExportService)


    singleOf(::BorderOverlayManager)
    singleOf(::OverlayViewModelOwner)
    singleOf(::OverlayController)


    singleOf(::ItemHelper)
    singleOf(::ActivityManager)
    singleOf(::ResourceDataManager)
    // Copilot (自动战斗)
    singleOf(::CopilotApiService)
    singleOf(::CopilotRepository)
    singleOf(::CopilotManager)
    singleOf(::TaskLogWriter)
    singleOf(::ApplicationLogWriter)
    singleOf(::LogTreeHolder)
}
