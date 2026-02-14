package com.aliothmoon.maameow

import android.app.Application
import com.aliothmoon.maameow.domain.service.UnifiedStateDispatcher
import com.aliothmoon.maameow.koin.appModule
import com.aliothmoon.maameow.koin.floatingWindowModule
import com.aliothmoon.maameow.koin.useCaseModule
import com.aliothmoon.maameow.koin.viewModelModule
import com.aliothmoon.maameow.overlay.OverlayController
import com.aliothmoon.maameow.data.log.ApplicationLogWriter
import com.aliothmoon.maameow.utils.CrashHandler
import com.aliothmoon.maameow.utils.log.FileLogTree
import com.aliothmoon.maameow.utils.log.DebugTree
import com.aliothmoon.maameow.utils.log.LogTreeHolder
import com.aliothmoon.maameow.utils.log.ReleaseTree
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class MaaApplication : Application() {

    private val crashHandler: CrashHandler by inject()
    private val unifiedStateDispatcher: UnifiedStateDispatcher by inject()
    private val overlayController: OverlayController by inject()
    private val treeHolder: LogTreeHolder by inject()

    override fun onCreate() {
        super.onCreate()
        val app = this
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(app)
            modules(appModule, useCaseModule, viewModelModule, floatingWindowModule)
        }

        postCreateApplication()
    }

    private fun postCreateApplication() {
        treeHolder.setup()
        crashHandler.init(this)
        overlayController.setup()
        unifiedStateDispatcher.start()
    }
}
