package com.aliothmoon.maameow.remote.internal

import android.app.ActivityOptions
import android.content.Intent
import com.aliothmoon.maameow.third.FakeContext
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager

object ActivityUtils {
    @JvmStatic
    fun startApp(packageName: String, displayId: Int, forceStop: Boolean = true): Boolean {
        val pm = FakeContext.get().packageManager

        val intent = pm.getLaunchIntentForPackage(packageName) ?: run {
            pm.getLeanbackLaunchIntentForPackage(packageName)
        }

        if (intent == null) {
            Ln.w("Cannot create launch intent for app $packageName")
            return false
        }

        var flag = Intent.FLAG_ACTIVITY_NEW_TASK
        if (displayId != 0) {
            flag = flag or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        intent.addFlags(flag)

        val am = ServiceManager.getActivityManager()
        if (forceStop) {
            am.forceStopPackage(packageName)
        }
        Ln.i("startApp ${intent.component?.flattenToShortString()}")

        try {
            val launchOptions = ActivityOptions.makeBasic()
            launchOptions.setLaunchDisplayId(displayId)
            val options = launchOptions.toBundle()
            val ret = am.startActivity(intent, options)
            if (ret != 0) {
                return startAppViaAmCommand(intent, displayId)
            }
            return true
        } catch (e: Exception) {
            Ln.w("startActivity failed, fallback to am command", e)
            return startAppViaAmCommand(intent, displayId)
        }
    }

    /**
     * ActivityManagerService 有过调整兼容实现较复杂
     */
    private fun startAppViaAmCommand(intent: Intent, displayId: Int): Boolean {
        try {
            val component = intent.component ?: return false
            val cmd = "am start" +
                    " --display $displayId" +
                    " -n ${component.flattenToShortString()}" +
                    " -f ${intent.flags}"
            Ln.d("Executing: $cmd")
            val process = Runtime.getRuntime().exec(cmd)
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Ln.w("am command exited with code $exitCode")
                return false
            }
            return true
        } catch (e: Exception) {
            Ln.e("am command fallback also failed", e)
            return false
        }
    }
}