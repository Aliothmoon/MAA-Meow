package com.aliothmoon.maameow.utils

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process

/** 独立进程里杀掉主进程再拉起新任务，主进程自己拉起会随进程一起被系统移除 */
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Process.killProcess(intent.getIntExtra(EXTRA_MAIN_PID, 0))
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val EXTRA_MAIN_PID = "main_pid"
    }
}
