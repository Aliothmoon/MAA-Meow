package com.aliothmoon.maameow.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.util.Properties

/**
 * Persistent wallpaper file locations under filesDir, plus helpers for
 * save/clear, crop-transform restore, seed-color cache, and legacy migration.
 */
class WallpaperFileStore(context: Context) {
    private val appContext = context.applicationContext

    val dir: File = File(appContext.filesDir, DIR_NAME).also { it.mkdirs() }
    val sourceFile: File = File(dir, SOURCE_NAME)
    val croppedFile: File = File(dir, CROPPED_NAME)
    private val cropParamsFile: File = File(dir, CROP_PARAMS_NAME)
    private val seedCacheFile: File = File(dir, SEED_CACHE_NAME)

    fun croppedUriString(): String = Uri.fromFile(croppedFile).toString()

    fun saveBitmap(file: File, bitmap: Bitmap): String? = saveBitmapToFile(file, bitmap)

    /**
     * Copy a content/file Uri into [target] using a single open of the source stream.
     * This is the reliable way to import picker URIs that only have a temporary grant.
     */
    fun copyFromUri(uri: Uri, target: File = sourceFile): Boolean {
        return try {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, target.name + ".tmp")
            val input = when (uri.scheme) {
                null, "file" -> {
                    val path = uri.path ?: return false
                    File(path).inputStream()
                }
                else -> appContext.contentResolver.openInputStream(uri) ?: return false
            }
            input.use { src ->
                temp.outputStream().use { dst ->
                    // Cap copy size so a huge content URI cannot fill filesDir before decode.
                    // Matches BitmapUtils in-memory content decode budget (25MB).
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = src.read(buf)
                        if (read < 0) break
                        total += read
                        if (total > MAX_COPY_BYTES) {
                            temp.delete()
                            return false
                        }
                        dst.write(buf, 0, read)
                    }
                }
            }
            if (temp.length() <= 0L) {
                temp.delete()
                return false
            }
            if (target.exists() && !target.delete()) {
                temp.delete()
                return false
            }
            if (!temp.renameTo(target)) {
                temp.inputStream().use { a ->
                    target.outputStream().use { b -> a.copyTo(b) }
                }
                temp.delete()
            }
            target.exists() && target.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates a temporary backup of [source] (appended ".backup") and returns it.
     * Returns null if source does not exist.
     */
    fun backupSourceFile(source: File): File? {
        if (!source.exists()) return null
        val backup = File(source.parentFile, source.name + ".backup")
        source.copyTo(backup, overwrite = true)
        return backup.takeIf { it.exists() }
    }

    /** Restores [backup] over [target].  Does nothing if backup is null or missing. */
    fun restoreSourceFile(backup: File?, target: File) {
        backup ?: return
        if (!backup.exists()) return
        backup.copyTo(target, overwrite = true)
    }

    /** Deletes a backup file.  Safe to pass null. */
    fun deleteBackup(backup: File?) {
        backup?.delete()
    }

    /** Path of the in-edit source backup, if any. */
    fun sourceBackupFile(): File = File(dir, SOURCE_NAME + ".backup")

    /**
     * If a pick was interrupted (process kill), [sourceFile] may already be the new
     * image while [croppedFile] / on-screen preview still show the previous crop.
     * Restore source from the orphan backup so re-open crop matches the preview, then
     * drop the backup.
     *
     * If crop params already match the current [sourceFile] identity, treat the pick
     * as confirmed (cropped + params durable) and only delete the backup — do not
     * restore, or a kill between confirm and backup-delete would resurrect the old source.
     * No-op when no backup exists.
     */
    fun reconcileOrphanEditBackup() {
        val backup = sourceBackupFile()
        if (!backup.exists()) return
        val identity = sourceIdentity(sourceFile)
        if (identity.isNotEmpty() && loadCropParams(identity) != null) {
            deleteBackup(backup)
            return
        }
        restoreSourceFile(backup, sourceFile)
        deleteBackup(backup)
    }

    fun clearFiles() {
        if (sourceFile.exists()) sourceFile.delete()
        if (croppedFile.exists()) croppedFile.delete()
        if (cropParamsFile.exists()) cropParamsFile.delete()
        if (seedCacheFile.exists()) seedCacheFile.delete()
        // Orphan edit backups (e.g. process killed mid-pick, or clear after cancel race).
        sourceBackupFile().delete()
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".backup") }
            ?.forEach { it.delete() }
    }

    fun fileExistsForUri(uriString: String): Boolean {
        if (uriString.isEmpty()) return false
        val path = Uri.parse(uriString).path ?: return false
        // Only the exact preference path counts. A leftover wallpaper.jpg must not
        // keep a stale/missing URI alive.
        return try {
            File(path).exists()
        } catch (_: Exception) {
            false
        }
    }

    data class CropParams(
        val scale: Float,
        val panX: Float,
        val panY: Float,
        val rotationDegrees: Float,
        val sourceIdentity: String,
    )

    fun sourceIdentity(file: File = sourceFile): String {
        if (!file.exists()) return ""
        return file.absolutePath + "|" + file.length() + "|" + file.lastModified()
    }

    fun saveCropParams(params: CropParams) {
        try {
            val props = Properties().apply {
                setProperty("scale", params.scale.toString())
                setProperty("panX", params.panX.toString())
                setProperty("panY", params.panY.toString())
                setProperty("rotationDegrees", params.rotationDegrees.toString())
                setProperty("sourceIdentity", params.sourceIdentity)
            }
            cropParamsFile.outputStream().use { props.store(it, "wallpaper crop transform") }
        } catch (_: Exception) {
            // best-effort
        }
    }

    fun loadCropParams(expectedSourceIdentity: String): CropParams? {
        if (!cropParamsFile.exists() || expectedSourceIdentity.isEmpty()) return null
        return try {
            val props = Properties()
            cropParamsFile.inputStream().use { props.load(it) }
            val identity = props.getProperty("sourceIdentity").orEmpty()
            if (identity != expectedSourceIdentity) return null
            CropParams(
                scale = props.getProperty("scale")?.toFloatOrNull() ?: return null,
                panX = props.getProperty("panX")?.toFloatOrNull() ?: 0f,
                panY = props.getProperty("panY")?.toFloatOrNull() ?: 0f,
                rotationDegrees = props.getProperty("rotationDegrees")?.toFloatOrNull() ?: 0f,
                sourceIdentity = identity,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearCropParams() {
        if (cropParamsFile.exists()) cropParamsFile.delete()
    }

    fun wallpaperIdentity(uriString: String): String {
        val path = Uri.parse(uriString).path
        val file = if (path != null) File(path) else croppedFile
        if (!file.exists()) return uriString
        return file.absolutePath + "|" + file.length() + "|" + file.lastModified()
    }

    fun loadCachedSeed(identity: String): Int? {
        if (identity.isEmpty() || !seedCacheFile.exists()) return null
        return try {
            val props = Properties()
            seedCacheFile.inputStream().use { props.load(it) }
            if (props.getProperty("identity") != identity) return null
            props.getProperty("seed")?.toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun saveCachedSeed(identity: String, seedArgb: Int) {
        if (identity.isEmpty()) return
        try {
            val props = Properties().apply {
                setProperty("identity", identity)
                setProperty("seed", seedArgb.toString())
            }
            seedCacheFile.outputStream().use { props.store(it, "wallpaper seed cache") }
        } catch (_: Exception) {
            // best-effort
        }
    }

    fun clearSeedCache() {
        if (seedCacheFile.exists()) seedCacheFile.delete()
    }

    /**
     * If preferences still point at a legacy cacheDir wallpaper that still exists,
     * copy it into filesDir and return the new URI string; otherwise return null.
     */
    fun migrateLegacyUriIfNeeded(uriString: String): String? {
        if (uriString.isEmpty()) return null
        val uri = Uri.parse(uriString)
        val path = uri.path ?: return null
        val legacy = File(path)
        if (!legacy.exists()) return null

        // Already under the new filesDir location.
        if (legacy.canonicalPath.startsWith(dir.canonicalPath)) return null

        val cacheRoot = appContext.cacheDir.canonicalPath
        val inCacheDir = legacy.canonicalPath.startsWith(cacheRoot)
        val looksLikeOldWallpaper =
            inCacheDir && (
                legacy.name == CROPPED_NAME ||
                    legacy.name == SOURCE_NAME ||
                    legacy.parentFile?.name == DIR_NAME ||
                    path.contains("/cache/")
            )
        if (!looksLikeOldWallpaper) {
            // Unknown external path: leave as-is (may still open via content/file).
            return null
        }

        return try {
            dir.mkdirs()
            legacy.inputStream().use { input ->
                croppedFile.outputStream().use { output -> input.copyTo(output) }
            }
            // Best-effort source copy when the legacy file is the only copy.
            if (!sourceFile.exists()) {
                croppedFile.copyTo(sourceFile, overwrite = true)
            }
            // Clean old cache copies if they live under cacheDir.
            if (legacy.canonicalPath.startsWith(cacheRoot)) {
                legacy.delete()
                legacy.parentFile
                    ?.takeIf { it.name == DIR_NAME && it.canonicalPath.startsWith(cacheRoot) }
                    ?.listFiles()
                    ?.forEach { candidate ->
                        if (candidate.name == SOURCE_NAME || candidate.name == CROPPED_NAME) {
                            candidate.delete()
                        }
                    }
            }
            croppedUriString()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DIR_NAME = "wallpaper"
        const val SOURCE_NAME = "wallpaper_source.jpg"
        const val CROPPED_NAME = "wallpaper.jpg"
        private const val CROP_PARAMS_NAME = "crop_params.properties"
        private const val SEED_CACHE_NAME = "seed_cache.properties"
        private const val MAX_BYTES = 4L * 1024 * 1024
        /** Max bytes accepted when copying a picker/content URI into filesDir. */
        private const val MAX_COPY_BYTES = 25L * 1024 * 1024

        fun saveBitmapToFile(file: File, bitmap: Bitmap): String? {
            return try {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, file.name + ".tmp")
                // JPEG has no alpha; composite onto black to avoid random garbage in transparent areas.
                val opaque = if (bitmap.hasAlpha()) {
                    Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { out ->
                        val canvas = Canvas(out)
                        canvas.drawColor(Color.BLACK)
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                    }
                } else {
                    bitmap
                }
                var quality = 95
                while (quality >= 20) {
                    temp.outputStream().use { opaque.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                    if (temp.length() <= MAX_BYTES || quality == 20) break
                    quality -= 15
                }
                if (opaque !== bitmap && !opaque.isRecycled) {
                    opaque.recycle()
                }
                if (file.exists() && !file.delete()) {
                    temp.delete()
                    return null
                }
                if (!temp.renameTo(file)) {
                    temp.inputStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    temp.delete()
                }
                file.absolutePath
            } catch (_: Exception) {
                null
            }
        }
    }
}
