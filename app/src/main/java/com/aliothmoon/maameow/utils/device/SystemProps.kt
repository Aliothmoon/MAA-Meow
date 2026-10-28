package com.aliothmoon.maameow.utils.device

/** 反射读系统属性，两个进程都能用；读不到返回空串，不 fork getprop */
internal object SystemProps {

    private val getter by lazy {
        runCatching { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java) }
            .getOrNull()
    }

    fun get(key: String): String =
        runCatching { getter?.invoke(null, key) as? String }.getOrNull().orEmpty()
}
