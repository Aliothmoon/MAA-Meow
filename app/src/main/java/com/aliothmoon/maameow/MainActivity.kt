package com.aliothmoon.maameow

import android.content.Intent
import android.os.Bundle
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.flow.drop
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.overlay.screensaver.HardwareScreenOffManager
import com.aliothmoon.maameow.overlay.screensaver.ScreenSaverOverlayManager
import com.aliothmoon.maameow.presentation.navigation.AppNavigation
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.schedule.model.ScheduledExecutionRequest
import com.aliothmoon.maameow.LocalUiMode
import com.aliothmoon.maameow.UiMode
import com.aliothmoon.maameow.LocalUiMode
import com.aliothmoon.maameow.UiMode
import com.aliothmoon.maameow.theme.MaaMeowMiuixTheme
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
    private val hardwareScreenOffManager: HardwareScreenOffManager by inject()
    private val backgroundTaskViewModel: BackgroundTaskViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = appSettingsManager.themeMode.value.toAppCompatNightMode()
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !isUiReady }
        super.onCreate(savedInstanceState)
        dispatchScheduledLaunchIntent(intent)
        enableEdgeToEdge()
        doObserveKeepScreenOn()
        doObserveThemeMode()
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

            CompositionLocalProvider(LocalUiMode provides UiMode.Material) {
                MaaMeowMiuixTheme(themeMode = themeMode) {
                    AppNavigation(backgroundTaskViewModel = backgroundTaskViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchScheduledLaunchIntent(intent)
    }

    private fun dispatchScheduledLaunchIntent(intent: Intent?) {
        val request = ScheduledExecutionRequest.fromIntent(intent)
            ?: ScheduledExecutionRequest.fromExternalIntent(intent)
        request?.let { backgroundTaskViewModel.onScheduledLaunch(it) }
    }

    private fun doObserveKeepScreenOn() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    compositionService.state,
                    screenSaverManager.showing,
                    hardwareScreenOffManager.active,
                ) { taskState, saverShowing, hwScreenOff ->
                    val taskActive = taskState == MaaExecutionState.STARTING
                            || taskState == MaaExecutionState.RUNNING
                            || taskState == MaaExecutionState.STOPPING
                    taskActive || saverShowing || hwScreenOff
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
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun doObserveThemeMode() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appSettingsManager.themeMode.drop(1).collect { mode ->
                    val target = mode.toAppCompatNightMode()
                    if (delegate.localNightMode != target) {
                        delegate.localNightMode = target
                    }
                }
                }
            }
        }
    }

    private fun AppSettingsManager.ThemeMode.toAppCompatNightMode(): Int = when (this) {
        AppSettingsManager.ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        AppSettingsManager.ThemeMode.WHITE -> AppCompatDelegate.MODE_NIGHT_NO
        AppSettingsManager.ThemeMode.DARK,
        AppSettingsManager.ThemeMode.PURE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }
}
