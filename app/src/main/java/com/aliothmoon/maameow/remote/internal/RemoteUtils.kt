package com.aliothmoon.maameow.remote.internal

import android.content.pm.IPackageManager
import android.os.IDeviceIdleController
import android.os.Process
import com.aliothmoon.maameow.third.Ln
import com.android.internal.app.IAppOpsService
import rikka.shizuku.SystemServiceHelper

object RemoteUtils {
    private const val TAG = "RemoteUtils"

    val packageManager: IPackageManager by lazy {
        val binder = SystemServiceHelper.getSystemService("package")
        IPackageManager.Stub.asInterface(binder)
    }

    val appOpsService: IAppOpsService by lazy {
        val binder = SystemServiceHelper.getSystemService("appops")
        IAppOpsService.Stub.asInterface(binder)
    }

    val deviceIdleController: IDeviceIdleController by lazy {
        val binder = SystemServiceHelper.getSystemService("deviceidle")
        IDeviceIdleController.Stub.asInterface(binder)
    }

    fun shellExec(command: String): Int {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val exitCode = process.waitFor()
        Ln.i("$TAG: $command -> exitCode=$exitCode")
        return exitCode
    }
}
