package com.aliothmoon.maameow.manager

object RootServiceUidPolicy {

    private const val KEEP_ROOT_MIN_API = 33

    fun shouldKeepRoot(apiLevel: Int): Boolean = apiLevel >= KEEP_ROOT_MIN_API
}
