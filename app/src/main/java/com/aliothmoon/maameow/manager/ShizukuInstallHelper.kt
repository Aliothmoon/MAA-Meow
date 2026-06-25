package com.aliothmoon.maameow.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import com.aliothmoon.maameow.constant.OFFICIAL_SHIZUKU_PACKAGE
import rikka.sui.Sui
import timber.log.Timber
import java.io.File

object ShizukuInstallHelper {

    private const val ASSET_NAME = "shizuku.apk"

    enum class ShizukuStatus {
        READY,              // Binder 可用，服务正常运行（Shizuku App 或其他兼容框架）
        SUI_AVAILABLE,      // 通过 Sui（Magisk 模块）提供服务
        APP_NOT_RUNNING,    // Shizuku App 已安装但服务未启动
        NOT_INSTALLED       // 均未检测到，需要安装
    }

    fun checkStatus(context: Context, launchPackageName: String = OFFICIAL_SHIZUKU_PACKAGE): ShizukuStatus {
        val isSuiAvailable = try { Sui.init(context.packageName) } catch (_: Exception) { false }
        if (isSuiAvailable) return ShizukuStatus.SUI_AVAILABLE
        if (ShizukuManager.isShizukuAvailable()) return ShizukuStatus.READY

        return when {
            isPackageInstalled(context, launchPackageName) -> ShizukuStatus.APP_NOT_RUNNING
            else -> ShizukuStatus.NOT_INSTALLED
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

    fun openShizuku(context: Context, launchPackageName: String = OFFICIAL_SHIZUKU_PACKAGE): Boolean {
        return try {
            val intent = launchIntentForPackage(context, launchPackageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to open Shizuku")
            false
        }
    }

    private fun launchIntentForPackage(context: Context, packageName: String): Intent? {
        if (packageName.isBlank()) return null
        return context.packageManager.getLaunchIntentForPackage(packageName)
    }

    fun getLaunchAppLabel(context: Context, packageName: String): String? {
        if (packageName.isBlank()) return null
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrNull()
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
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
