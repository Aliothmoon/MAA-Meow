package com.aliothmoon.maameow.koin

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.domain.usecase.AnalyzeTaskChainUseCase
import com.aliothmoon.maameow.domain.usecase.CheckGameReadinessUseCase
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import com.aliothmoon.maameow.domain.usecase.SwitchCoreDataLocationUseCase
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.utils.EyeProtectionDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import timber.log.Timber


val useCaseModule = module {
    factory { SwitchCoreDataLocationUseCase(get(), get()) }

    factory {
        AnalyzeTaskChainUseCase(
            taskChainState = get(),
            resourceDataManager = get(),
            activityManager = get(),
            depotRepository = get(),
            operBoxRepository = get(),
            itemHelper = get(),
            dropsRefresher = get(),
            appSettingsManager = get(),
            relocatePath = get<MaaPathConfig>()::toCorePath,
        )
    }
    factory {
        CheckGameReadinessUseCase(
            appAliveChecker = get(),
            appSettings = get(),
            achievementReporter = get(),
            isPackageInstalled = { packageName ->
                withContext(Dispatchers.IO) {
                    try {
                        RemoteServiceManager.getInstanceOrNull()
                            ?.isPackageInstalled(packageName) ?: true
                    } catch (e: Exception) {
                        Timber.w(e, "isPackageInstalled check failed for %s", packageName)
                        true
                    }
                }
            },
            isEyeProtectionEnabled = { EyeProtectionDetector.isEyeProtectionEnabled(androidContext()) },
        )
    }
    factory {
        PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = get(),
            checkGameReadiness = get(),
        )
    }
}
