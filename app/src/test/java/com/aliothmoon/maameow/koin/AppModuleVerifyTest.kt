package com.aliothmoon.maameow.koin

import android.app.Application
import android.content.Context
import com.aliothmoon.maameow.domain.launch.LaunchPipeline
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

/**
 * 静态校验 Koin 依赖图，防止运行期 NoDefinitionFound 启动崩溃
 * 模块列表与 MaaApplication.startKoin 保持一致
 */
class AppModuleVerifyTest {

    @Test
    fun allModules_dependencyGraph_isClosed() {
        val all = module { includes(appModule, useCaseModule, viewModelModule, floatingWindowModule) }
        all.verify(
            extraTypes = listOf(
                Application::class,
                Context::class,
                androidx.datastore.core.DataStore::class,
            ),
            // 构造器里的 lambda 参数由模块内联提供，静态校验需显式放行
            injections = injectedParameters(
                definition<LaunchPipeline>(Function0::class, Function2::class),
            ),
        )
    }
}
