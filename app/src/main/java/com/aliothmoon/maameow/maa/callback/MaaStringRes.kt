package com.aliothmoon.maameow.maa.callback

import android.content.res.Resources
import java.util.concurrent.ConcurrentHashMap


/**
 * MaaCore 回调里的 key（`what` / `subtask` / 任务名）→ `maa_*` 字符串资源。
 *
 * 本对象位于回调热路径上：MaaCore 的 ProcessTask 每识别一个节点就发一对回调，
 * 所以查找必须常数时间，且不能每次都碰 [Resources.getIdentifier]
 * —— 那是基于字符串的资源表查找，是 Android 上典型的慢路径。
 *
 * 两条约束由此而来：
 * - **未命中也要缓存**（[MISSING]）。MaaCore 的 key 里有相当一部分没有对应资源，
 *   只缓存命中等于让这些 key 每次出现都重跑 camelToSnake + getIdentifier。
 * - **必须并发安全**。回调经 oneway binder 送达，虽然对同一 binder node 严格串行，
 *   但执行线程取自 binder 线程池、身份会变，普通 HashMap 的写入之间没有 happens-before。
 */
object MaaStringRes {

    /** 未命中标记：区分「查过且没有」与「还没查过」，避免负结果反复穿透 */
    private const val MISSING = 0

    private val cache = ConcurrentHashMap<String, Int>(64)

    fun getResId(resources: Resources, packageName: String, key: String): Int {
        // 先读一次：命中时避开 computeIfAbsent 的 lambda 分配（热路径上每条回调都会走）
        cache[key]?.let { return it }
        // 命中与未命中一律入缓存；MISSING 表示查过且确实没有。
        // 用 computeIfAbsent 而非「查完再 put」：冷启动突发时同一 key 只解析一次
        return cache.computeIfAbsent(key) {
            resources.getIdentifier("maa_${camelToSnake(it)}", "string", packageName)
        }
    }


    fun getString(resources: Resources, packageName: String, key: String): String {
        val resId = getResId(resources, packageName, key)
        return if (resId != MISSING) resources.getString(resId) else key
    }


    fun getString(
        resources: Resources,
        packageName: String,
        key: String,
        vararg formatArgs: Any,
    ): String {
        val resId = getResId(resources, packageName, key)
        return if (resId != MISSING) resources.getString(resId, *formatArgs) else key
    }

    /** 仅供测试：清空缓存，避免用例间互相污染。 */
    internal fun clearCacheForTest() = cache.clear()


    internal fun camelToSnake(name: String): String {
        val s = name.replace('.', '_')
        val sb = StringBuilder(s.length + 4)
        for (i in s.indices) {
            val c = s[i]
            if (c.isUpperCase()) {
                if (i > 0 && (s[i - 1].isLowerCase() || s[i - 1].isDigit())) {
                    sb.append('_')
                } else if (i > 0 && i + 1 < s.length && s[i + 1].isLowerCase()
                    && s[i - 1].isUpperCase()
                ) {
                    sb.append('_')
                }
                sb.append(c.lowercaseChar())
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}
