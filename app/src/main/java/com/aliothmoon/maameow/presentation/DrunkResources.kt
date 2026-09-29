package com.aliothmoon.maameow.presentation

import android.content.res.Resources
import android.util.SparseArray
import com.aliothmoon.maameow.data.achievement.PallasSpeak

@Suppress("DEPRECATION")
class DrunkResources(base: Resources) : Resources(
    base.assets,
    base.displayMetrics,
    base.configuration,
) {
    private val cache = SparseArray<String>()
    private val arrayCache = SparseArray<Array<String>>()

    private fun drunk(id: Int): String = synchronized(cache) {
        cache[id] ?: slurred(runCatching { super.getText(id) }.getOrNull())
            .also { cache.put(id, it) }
    }

    private fun drunkArray(id: Int): Array<String> = synchronized(arrayCache) {
        arrayCache[id] ?: super.getStringArray(id).let { original ->
            Array(original.size) { slurred(original[it]) }
        }.also { arrayCache.put(id, it) }
    }

    private fun slurred(original: CharSequence?): String {
        val cap = original?.let { it.toString().codePointCount(0, it.length) } ?: Int.MAX_VALUE
        return PallasSpeak.random(cap = cap)
    }

    override fun getText(id: Int): CharSequence = drunk(id)

    override fun getText(id: Int, def: CharSequence?): CharSequence = drunk(id)

    override fun getString(id: Int): String = drunk(id)

    override fun getString(id: Int, vararg formatArgs: Any?): String = drunk(id)

    override fun getQuantityText(id: Int, quantity: Int): CharSequence = drunk(id)

    override fun getQuantityString(id: Int, quantity: Int): String = drunk(id)

    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String =
        drunk(id)

    override fun getStringArray(id: Int): Array<String> = drunkArray(id)

    override fun getTextArray(id: Int): Array<CharSequence> {
        val drunk = drunkArray(id)
        return Array(drunk.size) { drunk[it] }
    }
}
