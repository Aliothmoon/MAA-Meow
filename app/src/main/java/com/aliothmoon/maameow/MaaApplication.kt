package com.aliothmoon.maameow

import android.app.Application
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.datasource.AppDownloader
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.domain.service.GameMuteCoordinator
import com.aliothmoon.maameow.domain.service.TaskEndRegistry
import com.aliothmoon.maameow.domain.service.UnifiedStateDispatcher
import com.aliothmoon.maameow.koin.appModule
import com.aliothmoon.maameow.koin.floatingWindowModule
import com.aliothmoon.maameow.koin.useCaseModule
import com.aliothmoon.maameow.koin.viewModelModule
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import com.aliothmoon.maameow.utils.CrashHandler
import com.aliothmoon.maameow.utils.EyeProtectionDetector
import com.aliothmoon.maameow.utils.i18n.LocaleBootstrap
import com.aliothmoon.maameow.utils.log.LogTreeHolder
import timber.log.Timber
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MaaApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialization = CompletableDeferred<Unit>()
    private val appSettingsManager: AppSettingsManager by inject()
    private val pathConfig: MaaPathConfig by inject()
    private val crashHandler: CrashHandler by inject()
    private val unifiedStateDispatcher: UnifiedStateDispatcher by inject()
    private val taskEndRegistry: TaskEndRegistry by inject()
    private val gameMuteCoordinator: GameMuteCoordinator by inject()
    private val overlayController: OverlayController by inject()
    private val appDownloader: AppDownloader by inject()
    private val treeHolder: LogTreeHolder by inject()
    private val scheduleRepository: ScheduleStrategyRepository by inject()
    private val scheduleAlarmManager: ScheduleAlarmManager by inject()
    private val depotRepository: DepotRepository by inject()
    private val operBoxRepository: OperBoxRepository by inject()

    suspend fun awaitReady() = initialization.await()

    override fun onCreate() {
        super.onCreate()
        val app = this
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(app)
            modules(appModule, useCaseModule, viewModelModule, floatingWindowModule)
        }
        // 不等设置读盘，冷启动 receiver / FGS 的日志与崩溃才接得住
        treeHolder.setup()
        crashHandler.init(this)
        val eyeProtection = EyeProtectionDetector.detect(this)
        Timber.i("isEyeProtectionEnabled: %s (source=%s)", eyeProtection.isEnabled, eyeProtection.source)

        applicationScope.launch(Dispatchers.Main) {
            appSettingsManager.awaitLoaded()
            LocaleBootstrap.applyPersisted(appSettingsManager)
            postCreateApplication()
            initialization.complete(Unit)
        }.invokeOnCompletion { cause ->
            if (cause != null) initialization.completeExceptionally(cause)
        }
    }

    private fun postCreateApplication() {
        RemoteServiceManager.initialize(this, appSettingsManager, pathConfig)
        overlayController.setup()
        unifiedStateDispatcher.start()
        taskEndRegistry.start()
        gameMuteCoordinator.startAutoRestore()
        depotRepository.start()
        operBoxRepository.start()
        cleanCachedUpdateApks()
        applicationScope.launch { crashHandler.cleanOldCrashLogs() }
        doSyncScheduleAlarms()
    }

    private fun cleanCachedUpdateApks() {
        applicationScope.launch {
            appDownloader.cleanInstalledApks()
        }
    }

    // BootReceiver 依赖 ACTION_MY_PACKAGE_REPLACED / BOOT_COMPLETED 恢复闹钟，
    // 但国产 ROM 在自启动未开启时会拦截该广播，导致闹钟丢失后无法恢复。
    // 每次应用启动时执行一次幂等同步，作为兜底保障。
    private fun doSyncScheduleAlarms() {
        applicationScope.launch {
            scheduleRepository.isLoaded.filter { it }.first()
            scheduleAlarmManager.rescheduleAll(scheduleRepository.strategies.value)
        }
    }
}
