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
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.ConfigBackupManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.data.repository.CopilotRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.CopilotManager
import com.aliothmoon.maameow.domain.service.LogExportService
import com.aliothmoon.maameow.domain.service.AppAliveChecker
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaEventNotifier
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.AppWatchdog
import com.aliothmoon.maameow.domain.service.RemoteAppAliveChecker
import com.aliothmoon.maameow.domain.service.ResourceInitService
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
import com.aliothmoon.maameow.maa.callback.TaskChainStatusTracker
import com.aliothmoon.maameow.maa.callback.ToolboxResultCollector
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.overlay.OverlayViewModelOwner
import com.aliothmoon.maameow.overlay.border.BorderOverlayManager
import com.aliothmoon.maameow.overlay.screensaver.ScreenSaverOverlayManager
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.data.notification.provider.BarkProvider
import com.aliothmoon.maameow.data.notification.provider.CustomWebhookProvider
import com.aliothmoon.maameow.data.notification.provider.DiscordProvider
import com.aliothmoon.maameow.data.notification.provider.DiscordWebhookProvider
import com.aliothmoon.maameow.data.notification.provider.DingTalkProvider
import com.aliothmoon.maameow.data.notification.provider.GotifyProvider
import com.aliothmoon.maameow.data.notification.provider.NotificationProvider
import com.aliothmoon.maameow.data.notification.provider.QmsgProvider
import com.aliothmoon.maameow.data.notification.provider.ServerChanProvider
import com.aliothmoon.maameow.data.notification.provider.SmtpProvider
import com.aliothmoon.maameow.data.notification.provider.TelegramProvider
import com.aliothmoon.maameow.domain.service.ExternalNotificationService
import com.aliothmoon.maameow.utils.CrashHandler
import com.aliothmoon.maameow.utils.log.LogTreeHolder
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
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
    singleOf(::ScheduleTriggerLogger)
    singleOf(::ScheduleAlarmManager)
    singleOf(::TaskChainState)
    singleOf(::ConfigBackupManager)
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
    singleOf(::MaaSessionLogger)

    // 外部通知
    singleOf(::NotificationSettingsManager)
    single { ServerChanProvider(get(), get()) } bind NotificationProvider::class
    single { TelegramProvider(get(), get()) } bind NotificationProvider::class
    single { DiscordProvider(get(), get()) } bind NotificationProvider::class
    single { DingTalkProvider(get(), get()) } bind NotificationProvider::class
    single { DiscordWebhookProvider(get(), get()) } bind NotificationProvider::class
    single { SmtpProvider(get()) } bind NotificationProvider::class
    single { BarkProvider(get(), get()) } bind NotificationProvider::class
    single { QmsgProvider(get(), get()) } bind NotificationProvider::class
    single { GotifyProvider(get(), get()) } bind NotificationProvider::class
    single { CustomWebhookProvider(get(), get()) } bind NotificationProvider::class
    single { ExternalNotificationService(get(), get(), getAll()) }

    // 内部通知
    singleOf(::MaaEventNotifier)

    // 回调处理链
    singleOf(::ConnectionInfoHandler)
    singleOf(::CopilotRuntimeStateStore)
    singleOf(::ToolboxResultCollector)
    singleOf(::TaskChainStatusTracker)
    singleOf(::TaskChainHandler)
    singleOf(::SubTaskHandler)
    single<AppAliveChecker> { RemoteAppAliveChecker() }
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
    singleOf(::ApplicationLogWriter)
    singleOf(::LogTreeHolder)
}
