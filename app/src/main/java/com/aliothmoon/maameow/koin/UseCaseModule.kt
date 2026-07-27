package com.aliothmoon.maameow.koin

import com.aliothmoon.maameow.domain.usecase.AnalyzeTaskChainUseCase
import com.aliothmoon.maameow.domain.usecase.CheckGameReadinessUseCase
import com.aliothmoon.maameow.domain.usecase.DepotMaintainExpander
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import com.aliothmoon.maameow.manager.RemoteServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.dsl.module
import timber.log.Timber


val useCaseModule = module {
    factory {
        DepotMaintainExpander(
            depotRepository = get(),
            activityManager = get(),
            itemHelper = get(),
        )
    }
    factory {
        AnalyzeTaskChainUseCase(
            taskChainState = get(),
            resourceDataManager = get(),
            depotMaintainExpander = get(),
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
        )
    }
    factory {
        PrepareTaskStartUseCase(
            analyzeTaskChainUseCase = get(),
            checkGameReadiness = get(),
        )
    }
}
