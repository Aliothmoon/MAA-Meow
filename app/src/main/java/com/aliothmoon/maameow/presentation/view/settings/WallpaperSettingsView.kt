package com.aliothmoon.maameow.presentation.view.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.presentation.LocalToaster
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.utils.WallpaperFileStore
import com.dokar.sonner.ToastType

/**
 * Wallpaper settings entry: owns DataStore-bound state and [WallpaperEditSession],
 * then hosts either the crop editor or the settings body.
 */
@Composable
fun WallpaperSettingsView(
    navController: NavController,
    viewModel: SettingsViewModel,
) {
    val wallpaperUri by viewModel.wallpaperUri.collectAsStateWithLifecycle()
    val wallpaperAlpha by viewModel.wallpaperAlpha.collectAsStateWithLifecycle()
    val wallpaperBlur by viewModel.wallpaperBlur.collectAsStateWithLifecycle()
    val wallpaperFrostedGlass by viewModel.wallpaperFrostedGlass.collectAsStateWithLifecycle()
    val wallpaperTextContrast by viewModel.wallpaperTextContrast.collectAsStateWithLifecycle()
    val wallpaperUpdateVersion by viewModel.wallpaperUpdateVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val coroutineScope = rememberCoroutineScope()
    val fileStore = remember { WallpaperFileStore(context) }
    val session = remember(context, viewModel, coroutineScope, fileStore) {
        WallpaperEditSession(
            context = context,
            viewModel = viewModel,
            scope = coroutineScope,
            fileStore = fileStore,
            showError = { msg -> toaster.show(msg, type = ToastType.Error) },
        )
    }

    var localAlpha by remember { mutableFloatStateOf(wallpaperAlpha.toFloat()) }
    var localBlur by remember { mutableFloatStateOf(wallpaperBlur.toFloat()) }
    LaunchedEffect(wallpaperAlpha) {
        if (kotlin.math.abs(localAlpha - wallpaperAlpha) > 0.5f) {
            localAlpha = wallpaperAlpha.toFloat()
        }
    }
    LaunchedEffect(wallpaperBlur) {
        if (kotlin.math.abs(localBlur - wallpaperBlur) > 0.5f) {
            localBlur = wallpaperBlur.toFloat()
        }
    }
    LaunchedEffect(localAlpha) {
        if (kotlin.math.abs(localAlpha - wallpaperAlpha) < 0.5f) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        viewModel.setWallpaperAlpha(localAlpha.toInt())
    }
    LaunchedEffect(localBlur) {
        if (kotlin.math.abs(localBlur - wallpaperBlur) < 0.5f) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        viewModel.setWallpaperBlur(localBlur.toInt())
    }
    // Read latest local + committed values on leave (do not capture first composition snapshot).
    DisposableEffect(viewModel) {
        onDispose {
            val alpha = localAlpha.toInt()
            val blur = localBlur.toInt()
            if (alpha != viewModel.wallpaperAlpha.value) {
                viewModel.setWallpaperAlpha(alpha)
            }
            if (blur != viewModel.wallpaperBlur.value) {
                viewModel.setWallpaperBlur(blur)
            }
        }
    }

    LaunchedEffect(wallpaperUri) {
        session.onEnterPage(wallpaperUri)
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        session.onPickedUri(uri)
    }

    val configuration = LocalConfiguration.current
    val screenRatio =
        configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()

    if (session.isEditing && session.sourceBitmap != null) {
        WallpaperCropEditor(
            session = session,
            localAlpha = localAlpha,
            onLocalAlphaChange = { localAlpha = it },
            localBlur = localBlur,
            onLocalBlurChange = { localBlur = it },
            screenRatio = screenRatio,
        )
    } else {
        WallpaperSettingsBody(
            navController = navController,
            viewModel = viewModel,
            session = session,
            wallpaperUri = wallpaperUri,
            wallpaperUpdateVersion = wallpaperUpdateVersion,
            wallpaperFrostedGlass = wallpaperFrostedGlass,
            wallpaperTextContrast = wallpaperTextContrast,
            localAlpha = localAlpha,
            onLocalAlphaChange = { localAlpha = it },
            localBlur = localBlur,
            onLocalBlurChange = { localBlur = it },
            screenRatio = screenRatio,
            onChangeWallpaper = { wallpaperPicker.launch("image/*") },
        )
    }
}
