package com.aliothmoon.maameow.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import rikka.sui.Sui
import timber.log.Timber
import java.io.File

object ShizukuInstallHelper {

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val ASSET_NAME = "shizuku.apk"

    enum class ShizukuStatus {
        INSTALLED,      // Shizuku App 已安装
        SUI_DETECTED,   // 检测到 Sui（Magisk 模块）
        NOT_INSTALLED   // 均未检测到
    }

    fun checkStatus(context: Context): ShizukuStatus {
        val appInstalled = try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (appInstalled) return ShizukuStatus.INSTALLED

        return try {
            if (Sui.init(context.packageName)) ShizukuStatus.SUI_DETECTED
            else ShizukuStatus.NOT_INSTALLED
        } catch (_: Exception) {
            ShizukuStatus.NOT_INSTALLED
        }
    }

    fun installShizuku(context: Context): Boolean {
        return try {
            val apkFile = copyApkFromAssets(context) ?: return false
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to install Shizuku")
            false
        }
    }

    private fun copyApkFromAssets(context: Context): File? {
        return try {
            val destFile = File(context.cacheDir, ASSET_NAME)
            context.assets.open(ASSET_NAME).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy Shizuku APK from assets")
            null
        }
    }
}
