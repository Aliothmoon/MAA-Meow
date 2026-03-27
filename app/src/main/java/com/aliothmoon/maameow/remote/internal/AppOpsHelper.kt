package com.aliothmoon.maameow.remote.internal

object AppOpsHelper {

    fun setPlayAudioOpAllowed(packageName: String?, isAllowed: Boolean): Boolean {
        if (packageName.isNullOrBlank()) return false
        val op = if (isAllowed) "allow" else "deny"
        return RemoteUtils.shellExec("appops set $packageName PLAY_AUDIO $op") == 0
    }
}
