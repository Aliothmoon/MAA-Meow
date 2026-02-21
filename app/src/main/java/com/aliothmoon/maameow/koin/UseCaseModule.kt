package com.aliothmoon.maameow.koin

import com.aliothmoon.maameow.domain.usecase.BuildTaskParamsUseCase
import org.koin.dsl.module


val useCaseModule = module {
    factory { BuildTaskParamsUseCase(get()) }
}
