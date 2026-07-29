package com.aliothmoon.maameow.presentation.view.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@Stable
internal class BackgroundCropController(
    private val viewModel: SettingsViewModel,
    private val scope: CoroutineScope,
    private val appContext: Context,
    val cropState: WallpaperCropState,
    private val sourcePathState: MutableState<String?>,
) {
    var sourceBitmap by mutableStateOf<Bitmap?>(null)
        private set

    val sourcePath: String? get() = sourcePathState.value

    /** 选中新图片：复制到缓存并解码，成功后进入裁剪会话。 */
    fun pick(uri: Uri) {
        scope.launch {
            val path = viewModel.prepareBackgroundSource(uri)
            val bitmap = path?.let { viewModel.decodeBackgroundSource(it) }
            if (path != null && bitmap != null) {
                cropState.initialized = false
                sourcePathState.value = path
                sourceBitmap = bitmap
            } else {
                viewModel.discardBackgroundSource()
                showFailureToast()
            }
        }
    }

    /** 进程重建后从缓存文件恢复源图片（此时图片选择器的 Uri 授权通常已失效）。 */
    suspend fun restoreIfNeeded() {
        val path = sourcePathState.value ?: return
        if (sourceBitmap != null) return
        val bitmap = viewModel.decodeBackgroundSource(path)
        if (bitmap != null) {
            sourceBitmap = bitmap
        } else {
            sourcePathState.value = null
            viewModel.discardBackgroundSource()
            showFailureToast()
        }
    }

    fun cancel() {
        endSession()
    }

    /** 保存裁剪结果；成功后结束会话并清理缓存，失败提示后停留在裁剪页。 */
    suspend fun confirm(cropped: Bitmap) {
        try {
            if (viewModel.saveCroppedBackground(cropped)) {
                endSession()
            } else {
                showFailureToast()
            }
        } finally {
            cropped.recycle()
        }
    }

    private fun endSession() {
        sourceBitmap = null
        sourcePathState.value = null
        viewModel.discardBackgroundSource()
    }

    private fun showFailureToast() {
        Toast.makeText(appContext, R.string.settings_background_import_failed, Toast.LENGTH_SHORT)
            .show()
    }
}

@Composable
internal fun rememberBackgroundCropController(viewModel: SettingsViewModel): BackgroundCropController {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val sourcePathState = rememberSaveable { mutableStateOf<String?>(null) }
    val cropState = rememberSaveable(saver = WallpaperCropState.Saver) { WallpaperCropState() }
    val controller = remember {
        BackgroundCropController(viewModel, scope, appContext, cropState, sourcePathState)
    }
    LaunchedEffect(sourcePathState.value) {
        controller.restoreIfNeeded()
    }
    return controller
}
