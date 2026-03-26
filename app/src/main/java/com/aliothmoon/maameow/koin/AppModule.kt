package com.aliothmoon.maameow.koin

import com.aliothmoon.maameow.data.api.CopilotApiService
import com.aliothmoon.maameow.data.api.ETagCacheManager
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.MaaApiService
import com.aliothmoon.maameow.data.api.MirrorChyanApiClient
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.datasource.AppDownloader
import com.aliothmoon.maameow.data.datasource.AssetExtractor
import com.aliothmoon.maameow.data.datasource.ResourceDownloader
import com.aliothmoon.maameow.data.datasource.ZipExtractor
import com.aliothmoon.maameow.data.datasource.update.MirrorChyanAppVersionChecker
import com.aliothmoon.maameow.data.datasource.update.MirrorChyanResourceVersionChecker
import com.aliothmoon.maameow.data.log.ApplicationLogWriter
import com.aliothmoon.maameow.data.log.TaskLogWriter
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.data.repository.CopilotRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.domain.service.LogExportService
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.AppWatchdog
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.service.RuntimeLogCenter
import com.aliothmoon.maameow.domain.service.UnifiedStateDispatcher
import com.aliothmoon.maameow.domain.service.update.UpdateService
import com.aliothmoon.maameow.domain.service.update.checker.AppVersionChecker
import com.aliothmoon.maameow.domain.service.update.checker.ResourceVersionChecker
import com.aliothmoon.maameow.maa.callback.ConnectionInfoHandler
import com.aliothmoon.maameow.maa.callback.CopilotRuntimeStateStore
import com.aliothmoon.maameow.maa.callback.MaaCallbackDispatcher
import com.aliothmoon.maameow.maa.callback.MaaExecutionStateHolder
import com.aliothmoon.maameow.maa.callback.SubTaskHandler
import com.aliothmoon.maameow.maa.callback.TaskChainHandler
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.overlay.OverlayViewModelOwner
import com.aliothmoon.maameow.overlay.border.BorderOverlayManager
import com.aliothmoon.maameow.overlay.screensaver.ScreenSaverOverlayManager
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.data.notification.provider.BarkProvider
import com.aliothmoon.maameow.data.notification.provider.CustomWebhookProvider
import com.aliothmoon.maameow.data.notification.provider.DingTalkProvider
import com.aliothmoon.maameow.data.notification.provider.NotificationProvider
import com.aliothmoon.maameow.data.notification.provider.ServerChanProvider
import com.aliothmoon.maameow.data.notification.provider.TelegramProvider
import com.aliothmoon.maameow.domain.service.ExternalNotificationService
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
    singleOf(::ScheduleStrategyRepository)
    singleOf(::ScheduleAlarmManager)
    singleOf(::TaskChainState)
    singleOf(::MaaPathConfig)
    singleOf(::ResourceDownloader)
    singleOf(::AppDownloader)
    singleOf(::ZipExtractor)
    singleOf(::AssetExtractor)

    // MirrorChyan API Client
    singleOf(::MirrorChyanApiClient)

    // Version Checkers
    single<AppVersionChecker> { MirrorChyanAppVersionChecker(get(), get()) }
    single<ResourceVersionChecker> { MirrorChyanResourceVersionChecker(get()) }

    singleOf(::UpdateService)

    singleOf(::ResourceInitService)
    singleOf(::MaaResourceLoader)
    single { RuntimeLogCenter(get()) }

    // 外部通知
    singleOf(::NotificationSettingsManager)
    single<NotificationProvider> { ServerChanProvider(get(), get()) }
    single<NotificationProvider> { BarkProvider(get(), get()) }
    single<NotificationProvider> { TelegramProvider(get(), get()) }
    single<NotificationProvider> { DingTalkProvider(get(), get()) }
    single<NotificationProvider> { CustomWebhookProvider(get(), get()) }
    single { ExternalNotificationService(get(), get(), getAll()) }

    // 回调处理链
    singleOf(::ConnectionInfoHandler)
    singleOf(::CopilotRuntimeStateStore)
    singleOf(::TaskChainHandler)
    singleOf(::SubTaskHandler)
    singleOf(::AppWatchdog)
    singleOf(::MaaCompositionService)
    single<MaaExecutionStateHolder> { get<MaaCompositionService>() }
    singleOf(::MaaCallbackDispatcher)

    singleOf(::UnifiedStateDispatcher)
    singleOf(::LogExportService)


    singleOf(::BorderOverlayManager)
    singleOf(::ScreenSaverOverlayManager)
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
