package com.aliothmoon.maameow.data.api

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import okhttp3.Headers

class ETagCacheManager(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "ETagCache"
        private val ETAG_KEY_MAKER: (String) -> String = {
            "ETag_$it"
        }
        private val LAST_MODIFIED_KEY_MAKER: (String) -> String = {
            "LastModified_$it"
        }

        const val IF_NONE_MATCH = "If-None-Match"
        const val IF_MODIFIED_SINCE = "If-Modified-Since"
        const val ETAG = "ETag"
        const val LAST_MODIFIED = "Last-Modified"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }


    fun getConditionalHeader(url: String): Map<String, String> {
        return buildMap {
            prefs.getString(ETAG_KEY_MAKER(url), null)?.let {
                put(IF_NONE_MATCH, it)
            }
            prefs.getString(LAST_MODIFIED_KEY_MAKER(url), null)?.let {
                put(IF_MODIFIED_SINCE, it)
            }
        }
    }

    /**
     * 同时保存 ETag 和 Last-Modified
     */
    fun updateConditionalHeaders(url: String, headers: Headers) {
        prefs.edit {
            headers[ETAG]?.let {
                putString(ETAG_KEY_MAKER(url), it)
            }
            headers[LAST_MODIFIED]?.let {
                putString(LAST_MODIFIED_KEY_MAKER(url), it)
            }
        }
    }


    /**
     * 清除所有缓存
     */
    fun invalidate() {
        prefs.edit { clear() }
    }
}
