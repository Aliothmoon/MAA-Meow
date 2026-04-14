package com.aliothmoon.maameow

import android.content.Intent
import android.os.Bundle
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.overlay.screensaver.ScreenSaverOverlayManager
import com.aliothmoon.maameow.presentation.navigation.AppNavigation
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.schedule.model.ScheduledExecutionRequest
import com.aliothmoon.maameow.theme.MaaMeowTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {

    @Volatile
    private var isUiReady: Boolean = false

    private val appSettingsManager: AppSettingsManager by inject()
    private val compositionService: MaaCompositionService by inject()
    private val screenSaverManager: ScreenSaverOverlayManager by inject()
    private val backgroundTaskViewModel: BackgroundTaskViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !isUiReady }
        super.onCreate(savedInstanceState)
        dispatchScheduledLaunchIntent(intent)
        enableEdgeToEdge()
        doObserveKeepScreenOn()
        window.decorView.viewTreeObserver.addOnPreDrawListener(object :
            ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                isUiReady = true
                window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        setContent {
            val themeMode by appSettingsManager.themeMode.collectAsStateWithLifecycle()

            MaaMeowTheme(themeMode = themeMode) {
                AppNavigation(backgroundTaskViewModel = backgroundTaskViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchScheduledLaunchIntent(intent)
    }

    private fun dispatchScheduledLaunchIntent(intent: Intent?) {
        ScheduledExecutionRequest.fromIntent(intent)?.let { request ->
            backgroundTaskViewModel.onScheduledLaunch(request)
        }
    }

    private fun doObserveKeepScreenOn() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    compositionService.state,
                    screenSaverManager.showing,
                ) { taskState, saverShowing ->
                    val taskActive = taskState == MaaExecutionState.STARTING
                            || taskState == MaaExecutionState.RUNNING
                            || taskState == MaaExecutionState.STOPPING
                    taskActive || saverShowing
                }.collect { keepOn ->
                    if (keepOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
