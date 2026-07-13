package com.aliothmoon.maameow.presentation.view.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.utils.BitmapUtils
import com.aliothmoon.maameow.utils.WallpaperFileStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wallpaper pick / crop / backup lifecycle for [WallpaperSettingsView].
 * UI-free so crop editor and settings body stay presentational.
 *
 * Compose state is only written on the main dispatcher (session [scope]).
 * IO work returns plain values; UI flags are applied after [withContext] returns.
 */
class WallpaperEditSession(
    private val context: Context,
    private val viewModel: SettingsViewModel,
    val scope: CoroutineScope,
    val fileStore: WallpaperFileStore,
    private val showError: (String) -> Unit,
) {
    var isEditing by mutableStateOf(false)
        private set
    var sourceBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var isBusy by mutableStateOf(false)
    var initScale by mutableFloatStateOf(1f)
    val cropState = CropState()

    /** Not Compose state — only used on the main thread between pick/confirm/cancel. */
    private var editPendingBackup: File? = null

    val sourceFile: File get() = fileStore.sourceFile
    val croppedFile: File get() = fileStore.croppedFile

    fun enterEditMode(bitmap: Bitmap) {
        sourceBitmap = bitmap
        isEditing = true
    }

    /** Leave crop UI only; does not touch source backup. */
    fun leaveEditUi() {
        sourceBitmap = null
        isEditing = false
        isBusy = false
    }

    /**
     * User abandoned cropping (back). Restore previous source if we had replaced it
     * for a pending new pick, so re-open crop still matches the on-screen preview.
     * Stays [isBusy] until restore finishes so a quick re-open cannot race the old source.
     */
    fun exitEditMode() {
        if (isBusy) return
        val backup = editPendingBackup
        editPendingBackup = null
        sourceBitmap = null
        isEditing = false
        if (backup != null) {
            isBusy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        fileStore.restoreSourceFile(backup, sourceFile)
                        fileStore.deleteBackup(backup)
                    }
                } catch (_: Exception) {
                    // Best-effort restore; orphan backup is healed on next enter.
                    try {
                        withContext(Dispatchers.IO) { fileStore.deleteBackup(backup) }
                    } catch (_: Exception) {
                    }
                } finally {
                    isBusy = false
                }
            }
        } else {
            isBusy = false
        }
    }

    /**
     * Crop saved: keep new sourceFile and leave UI.
     * Pending backup must already be deleted on the confirm IO path (or be null)
     * so reconcile cannot restore the old source after a durable save.
     */
    fun finishEditModeAfterSave() {
        editPendingBackup = null
        leaveEditUi()
    }

    suspend fun onEnterPage(wallpaperUri: String) {
        withContext(Dispatchers.IO) {
            fileStore.reconcileOrphanEditBackup()
        }
        if (wallpaperUri.isEmpty()) return
        val result = withContext(Dispatchers.IO) {
            val migrated = fileStore.migrateLegacyUriIfNeeded(wallpaperUri)
            when {
                migrated != null -> "migrated" to migrated
                fileStore.fileExistsForUri(wallpaperUri) -> "ok" to null
                else -> "missing" to null
            }
        }
        when (result.first) {
            "migrated" -> viewModel.setWallpaperUri(result.second!!)
            "missing" -> {
                viewModel.setWallpaperUri("")
                showError(context.getString(R.string.settings_wallpaper_missing))
            }
        }
    }

    fun onPickedUri(uri: Uri) {
        if (isBusy) return
        scope.launch {
            isBusy = true
            try {
                // IO returns values only — do not touch Compose state / editPendingBackup on IO.
                data class PickResult(val bitmap: Bitmap?, val pendingBackup: File?)
                val pick = withContext(Dispatchers.IO) {
                    val backup = fileStore.backupSourceFile(sourceFile)
                    try {
                        val copied = fileStore.copyFromUri(uri, sourceFile)
                        val loaded = if (!copied) {
                            val fallback = BitmapUtils.loadDownsampledBitmap(
                                context,
                                uri,
                                maxDimension = 2560,
                            )
                            if (fallback != null) {
                                fileStore.saveBitmap(sourceFile, fallback)
                            }
                            fallback
                        } else {
                            BitmapUtils.loadDownsampledBitmap(sourceFile, maxDimension = 2560)
                        }
                        if (loaded == null) {
                            fileStore.restoreSourceFile(backup, sourceFile)
                            fileStore.deleteBackup(backup)
                            PickResult(bitmap = null, pendingBackup = null)
                        } else {
                            fileStore.clearCropParams()
                            PickResult(bitmap = loaded, pendingBackup = backup)
                        }
                    } catch (t: Throwable) {
                        fileStore.restoreSourceFile(backup, sourceFile)
                        fileStore.deleteBackup(backup)
                        throw t
                    }
                }
                editPendingBackup = pick.pendingBackup
                val bitmap = pick.bitmap
                if (bitmap != null) {
                    enterEditMode(bitmap)
                } else {
                    showError(context.getString(R.string.settings_wallpaper_load_failed))
                }
            } catch (_: Exception) {
                editPendingBackup = null
                showError(context.getString(R.string.settings_wallpaper_load_failed))
            } finally {
                isBusy = false
            }
        }
    }

    fun openExistingForEdit() {
        if (isBusy) return
        scope.launch {
            isBusy = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    when {
                        sourceFile.exists() -> BitmapUtils.loadDownsampledBitmap(
                            sourceFile,
                            maxDimension = 2560,
                        )
                        croppedFile.exists() -> {
                            val loaded = BitmapUtils.loadDownsampledBitmap(
                                croppedFile,
                                maxDimension = 2560,
                            )
                            if (loaded != null) fileStore.saveBitmap(sourceFile, loaded)
                            loaded
                        }
                        else -> {
                            val uri = viewModel.wallpaperUri.value
                            val loaded = if (uri.isNotEmpty()) {
                                BitmapUtils.loadDownsampledBitmap(
                                    context,
                                    uri,
                                    maxDimension = 2560,
                                )
                            } else {
                                null
                            }
                            if (loaded != null) fileStore.saveBitmap(sourceFile, loaded)
                            loaded
                        }
                    }
                }
                if (bitmap != null) {
                    enterEditMode(bitmap)
                } else {
                    showError(context.getString(R.string.settings_wallpaper_load_failed))
                }
            } catch (_: Exception) {
                showError(context.getString(R.string.settings_wallpaper_load_failed))
            } finally {
                isBusy = false
            }
        }
    }

    fun confirmCrop() {
        val bitmap = sourceBitmap ?: return
        if (isBusy) return
        // Capture before IO: after a durable crop we must drop backup in the same
        // IO work so process death cannot leave orphan backup for reconcile-restore.
        val pendingBackup = editPendingBackup
        scope.launch {
            isBusy = true
            try {
                cropState.ensureCoverScale()
                val scale = cropState.scale
                val panX = cropState.panX
                val panY = cropState.panY
                val rotationDegrees = cropState.rotationDegrees
                val result = withContext(Dispatchers.IO) {
                    val cropped = cropState.getCroppedBitmap(bitmap) ?: return@withContext null
                    val saved = fileStore.saveBitmap(croppedFile, cropped)
                    if (!cropped.isRecycled) {
                        cropped.recycle()
                    }
                    if (saved == null) return@withContext null
                    fileStore.saveCropParams(
                        WallpaperFileStore.CropParams(
                            scale = scale,
                            panX = panX,
                            panY = panY,
                            rotationDegrees = rotationDegrees,
                            sourceIdentity = fileStore.sourceIdentity(sourceFile),
                        )
                    )
                    fileStore.clearSeedCache()
                    // Confirmed pick: never restore this backup. Delete before returning
                    // so onEnterPage/AppNav reconcile only sees a clean tree.
                    fileStore.deleteBackup(pendingBackup)
                    saved
                }
                if (result != null) {
                    viewModel.setWallpaperUriSync(fileStore.croppedUriString())
                    finishEditModeAfterSave()
                } else {
                    showError(context.getString(R.string.settings_wallpaper_save_failed))
                }
            } catch (_: Exception) {
                showError(context.getString(R.string.settings_wallpaper_save_failed))
            } finally {
                // Success path already cleared busy via leaveEditUi(); keep safe on failure.
                isBusy = false
            }
        }
    }

    fun clearWallpaper() {
        if (isBusy) return
        scope.launch {
            isBusy = true
            try {
                withContext(Dispatchers.IO) { fileStore.clearFiles() }
                // Await version bump so collectors drop the old bitmap immediately.
                viewModel.setWallpaperUriSync("")
            } catch (_: Exception) {
                showError(context.getString(R.string.settings_wallpaper_save_failed))
            } finally {
                isBusy = false
            }
        }
    }
}
