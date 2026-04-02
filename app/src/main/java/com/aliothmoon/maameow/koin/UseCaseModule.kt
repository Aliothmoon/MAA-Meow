package com.aliothmoon.maameow.koin

import com.aliothmoon.maameow.domain.usecase.AnalyzeTaskChainUseCase
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import org.koin.dsl.module


val useCaseModule = module {
    factory { AnalyzeTaskChainUseCase() }
    factory { PrepareTaskStartUseCase(get(), get()) }
}
